package com.example.fintech.cuentacorriente;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteRepository;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.cliente.TipoPersona;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba, con concurrencia real (no secuencial), el locking optimista sobre
 * cuentas_corrientes.saldo que describe feature-specs/1-consistencia-datos.md
 * sección 1. N threads arrancan a la vez (CyclicBarrier) contra la misma
 * cuenta, cada uno con su propia idempotency key — así el único mecanismo
 * que puede evitar un lost update es la columna version, no el atajo de
 * idempotencia (que sólo protege reintentos con la misma key).
 *
 * Tanto el CUIT como el prefijo de idempotency key se generan por corrida
 * (no fijos): idempotency_key tiene constraint única global en
 * movimientos_cta_cte, así que una key fija reusada entre corridas pega
 * contra la fila de la corrida anterior en vez de competir por la cuenta
 * de esta corrida — así se detectó y descartó un falso positivo mientras
 * se escribía este test.
 */
@SpringBootTest
class CuentaCorrienteLedgerServiceConcurrencyTest {

	@Autowired
	private CuentaCorrienteLedgerService cuentaCorrienteLedgerService;

	@Autowired
	private ClienteService clienteService;

	@Autowired
	private CuentaCorrienteService cuentaCorrienteService;

	@Autowired
	private CuentaCorrienteRepository cuentaCorrienteRepository;

	@Autowired
	private MovimientoCtaCteRepository movimientoCtaCteRepository;

	@Autowired
	private ClienteRepository clienteRepository;

	private UUID clienteIdCreado;
	private UUID cuentaIdCreada;

	/**
	 * No usa @Transactional de test: los threads que arrancan más abajo
	 * necesitan su propia conexión/transacción confirmada para poder
	 * realmente competir por la fila, así que el cleanup es manual en vez
	 * de depender de un rollback automático.
	 */
	@AfterEach
	void limpiar() {
		if (cuentaIdCreada != null) {
			movimientoCtaCteRepository.deleteAll(
					cuentaCorrienteLedgerService.findByCuentaCorriente(cuentaIdCreada));
			cuentaCorrienteRepository.deleteById(cuentaIdCreada);
		}
		if (clienteIdCreado != null) {
			clienteRepository.deleteById(clienteIdCreado);
		}
	}

	@Test
	void escrituras_concurrentes_sobre_la_misma_cuenta_no_pierden_updates() throws Exception {
		String sufijoCorrida = UUID.randomUUID().toString();
		String cuitUnico = String.format("20%09d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000L);

		Cliente cliente = new Cliente();
		cliente.setCuit(cuitUnico);
		cliente.setRazonSocial("Test Concurrencia SA");
		cliente.setTipoPersona(TipoPersona.JURIDICA);
		cliente = clienteService.create(cliente);
		clienteIdCreado = cliente.getId();

		CuentaCorriente cuenta = new CuentaCorriente();
		cuenta.setCliente(cliente);
		cuenta.setMoneda("ARS");
		cuenta = cuentaCorrienteService.create(cuenta);
		UUID cuentaId = cuenta.getId();
		cuentaIdCreada = cuentaId;

		int hilos = 20;
		BigDecimal montoPorMovimiento = new BigDecimal("100.00");
		ExecutorService pool = Executors.newFixedThreadPool(hilos);
		CyclicBarrier arrancarJuntos = new CyclicBarrier(hilos);
		AtomicInteger exitosos = new AtomicInteger();
		AtomicInteger conflictos = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (int i = 0; i < hilos; i++) {
			int idx = i;
			futures.add(pool.submit(() -> {
				arrancarJuntos.await();
				try {
					cuentaCorrienteLedgerService.registrarMovimiento(cuentaId, TipoMovimientoCC.CREDITO,
							montoPorMovimiento, "TEST_CONCURRENCIA", cuentaId, sufijoCorrida + "-" + idx);
					exitosos.incrementAndGet();
				} catch (OptimisticLockingFailureException e) {
					conflictos.incrementAndGet();
				}
				return null;
			}));
		}

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				if (e.getCause() instanceof OptimisticLockingFailureException) {
					conflictos.incrementAndGet();
				} else {
					throw e;
				}
			}
		}
		pool.shutdown();

		System.out.println("[concurrencia cta-cte] hilos=" + hilos + " exitosos=" + exitosos.get()
				+ " conflictos(409)=" + conflictos.get());

		CuentaCorriente estadoFinal = cuentaCorrienteRepository.findById(cuentaId).orElseThrow();
		BigDecimal saldoEsperado = montoPorMovimiento.multiply(BigDecimal.valueOf(exitosos.get())).negate();

		assertThat(exitosos.get() + conflictos.get()).isEqualTo(hilos);
		assertThat(conflictos.get())
				.as("con %d threads arrancando a la vez sobre la misma fila, tiene que haber al menos un choque de version"
						+ " — si esto da 0, o el locking optimista no está andando o el test no está generando concurrencia real",
						hilos)
				.isGreaterThan(0);
		assertThat(estadoFinal.getSaldo())
				.as("el saldo final tiene que reflejar exactamente los movimientos que realmente commitearon, ni más ni menos"
						+ " (ningún update se pisó en silencio)")
				.isEqualByComparingTo(saldoEsperado);
		assertThat(cuentaCorrienteLedgerService.findByCuentaCorriente(cuentaId)).hasSize(exitosos.get());
	}
}
