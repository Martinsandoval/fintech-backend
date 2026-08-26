package com.example.fintech.cuentacorriente;

import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Única forma de modificar cuentas_corrientes.saldo. Ver
 * feature-specs/1-consistencia-datos.md sección 3 para la convención de
 * signo (DEBITO aumenta el saldo, CREDITO lo disminuye) y sección 2 para el
 * contrato de idempotencia.
 */
@Service
@Transactional(readOnly = true)
public class CuentaCorrienteLedgerService {

	private final MovimientoCtaCteRepository movimientoCtaCteRepository;
	private final CuentaCorrienteRepository cuentaCorrienteRepository;

	public CuentaCorrienteLedgerService(MovimientoCtaCteRepository movimientoCtaCteRepository,
			CuentaCorrienteRepository cuentaCorrienteRepository) {
		this.movimientoCtaCteRepository = movimientoCtaCteRepository;
		this.cuentaCorrienteRepository = cuentaCorrienteRepository;
	}

	@Transactional
	public MovimientoCtaCte registrarMovimiento(UUID cuentaCorrienteId, TipoMovimientoCC tipo, BigDecimal monto,
			String referenciaTipo, UUID referenciaId, String idempotencyKey) {
		Optional<MovimientoCtaCte> existente = movimientoCtaCteRepository.findByIdempotencyKey(idempotencyKey);
		if (existente.isPresent()) {
			return existente.get();
		}
		if (monto.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("el monto del movimiento debe ser mayor a cero");
		}

		CuentaCorriente cuenta = cuentaCorrienteRepository.findById(cuentaCorrienteId)
				.orElseThrow(() -> new ResourceNotFoundException("CuentaCorriente", cuentaCorrienteId));

		BigDecimal nuevoSaldo = tipo == TipoMovimientoCC.DEBITO
				? cuenta.getSaldo().add(monto)
				: cuenta.getSaldo().subtract(monto);
		cuenta.setSaldo(nuevoSaldo);

		MovimientoCtaCte movimiento = new MovimientoCtaCte();
		movimiento.setCuentaCorriente(cuenta);
		movimiento.setTipo(tipo);
		movimiento.setMonto(monto);
		movimiento.setSaldoPosterior(nuevoSaldo);
		movimiento.setReferenciaTipo(referenciaTipo);
		movimiento.setReferenciaId(referenciaId);
		movimiento.setIdempotencyKey(idempotencyKey);
		return movimientoCtaCteRepository.save(movimiento);
	}

	/**
	 * Igual que registrarMovimiento, pero no falla si el cliente todavía no
	 * tiene una cuenta corriente en esa moneda: la usan flujos donde la
	 * cuenta corriente es opcional (ver feature-specs/1-consistencia-datos.md
	 * sección 5, cobro de cuota).
	 */
	@Transactional
	public Optional<MovimientoCtaCte> registrarMovimientoSiExisteCuenta(UUID clienteId, String moneda,
			TipoMovimientoCC tipo, BigDecimal monto, String referenciaTipo, UUID referenciaId,
			String idempotencyKey) {
		return cuentaCorrienteRepository.findByClienteIdAndMoneda(clienteId, moneda)
				.map(cuenta -> registrarMovimiento(cuenta.getId(), tipo, monto, referenciaTipo, referenciaId,
						idempotencyKey));
	}

	public List<MovimientoCtaCte> findByCuentaCorriente(UUID cuentaCorrienteId) {
		return movimientoCtaCteRepository.findByCuentaCorrienteIdOrderByFechaDesc(cuentaCorrienteId);
	}
}
