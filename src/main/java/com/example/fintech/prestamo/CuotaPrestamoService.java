package com.example.fintech.prestamo;

import com.example.fintech.common.ResourceNotFoundException;
import com.example.fintech.contabilidad.AsientoContable;
import com.example.fintech.contabilidad.AsientoContableService;
import com.example.fintech.contabilidad.LineaContable;
import com.example.fintech.contabilidad.PlanCuenta;
import com.example.fintech.contabilidad.PlanCuentaService;
import com.example.fintech.cuentacorriente.CuentaCorrienteLedgerService;
import com.example.fintech.cuentacorriente.TipoMovimientoCC;
import com.example.fintech.outbox.OutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * registrarPago booka, en la misma transacción que actualiza la cuota: el
 * asiento contable por el monto efectivamente cobrado en este llamado (el
 * delta contra el monto ya pagado, no el acumulado), un movimiento de
 * crédito en la cuenta corriente del cliente si existe, y un evento outbox.
 * Ver feature-specs/1-consistencia-datos.md sección 5.
 */
@Service
@Transactional(readOnly = true)
public class CuotaPrestamoService {

	private static final String CUENTA_BANCO = "1.1.02";
	private static final String CUENTA_CUENTAS_A_COBRAR = "1.1.05";
	private static final String MONEDA_DEFAULT = "ARS";

	private final CuotaPrestamoRepository cuotaPrestamoRepository;
	private final PrestamoService prestamoService;
	private final AsientoContableService asientoContableService;
	private final PlanCuentaService planCuentaService;
	private final CuentaCorrienteLedgerService cuentaCorrienteLedgerService;
	private final OutboxEventService outboxEventService;

	public CuotaPrestamoService(CuotaPrestamoRepository cuotaPrestamoRepository, PrestamoService prestamoService,
			AsientoContableService asientoContableService, PlanCuentaService planCuentaService,
			CuentaCorrienteLedgerService cuentaCorrienteLedgerService, OutboxEventService outboxEventService) {
		this.cuotaPrestamoRepository = cuotaPrestamoRepository;
		this.prestamoService = prestamoService;
		this.asientoContableService = asientoContableService;
		this.planCuentaService = planCuentaService;
		this.cuentaCorrienteLedgerService = cuentaCorrienteLedgerService;
		this.outboxEventService = outboxEventService;
	}

	public List<CuotaPrestamo> findByPrestamo(UUID prestamoId) {
		return cuotaPrestamoRepository.findByPrestamoIdOrderByNumero(prestamoId);
	}

	public CuotaPrestamo findById(UUID id) {
		return cuotaPrestamoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("CuotaPrestamo", id));
	}

	@Transactional
	public CuotaPrestamo create(CuotaPrestamo cuota) {
		if (cuota.getPrestamo() == null || cuota.getPrestamo().getId() == null) {
			throw new IllegalArgumentException("la cuota debe referenciar un préstamo existente");
		}
		Prestamo prestamo = prestamoService.findById(cuota.getPrestamo().getId());
		cuota.setId(null);
		cuota.setPrestamo(prestamo);
		cuota.setEstado(EstadoCuota.PENDIENTE);
		cuota.setMontoPagado(BigDecimal.ZERO);
		cuota.setFechaPago(null);
		return cuotaPrestamoRepository.save(cuota);
	}

	@Transactional
	public CuotaPrestamo registrarPago(UUID id, BigDecimal montoPagado, String idempotencyKey) {
		if (asientoContableService.existePorIdempotencyKey(idempotencyKey)) {
			return findById(id);
		}

		CuotaPrestamo cuota = findById(id);
		BigDecimal montoPagadoAnterior = cuota.getMontoPagado();
		BigDecimal delta = montoPagado.subtract(montoPagadoAnterior);
		if (delta.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException(
					"el monto pagado (" + montoPagado + ") debe ser mayor al ya registrado (" + montoPagadoAnterior
							+ ")");
		}

		cuota.setMontoPagado(montoPagado);
		cuota.setFechaPago(LocalDate.now());
		cuota.setEstado(montoPagado.compareTo(cuota.getMonto()) >= 0 ? EstadoCuota.PAGADA : EstadoCuota.PARCIAL);

		PlanCuenta banco = planCuentaService.findByCodigo(CUENTA_BANCO);
		PlanCuenta cuentasACobrar = planCuentaService.findByCodigo(CUENTA_CUENTAS_A_COBRAR);
		AsientoContable asiento = asientoContableService.crearConLineas(
				"Cobro cuota " + cuota.getNumero() + " préstamo " + cuota.getPrestamo().getId(),
				"CUOTA_PRESTAMO",
				cuota.getId(),
				idempotencyKey,
				List.of(
						LineaContable.debe(banco.getId(), delta),
						LineaContable.haber(cuentasACobrar.getId(), delta)));

		UUID clienteId = cuota.getPrestamo().getCliente().getId();
		cuentaCorrienteLedgerService.registrarMovimientoSiExisteCuenta(clienteId, MONEDA_DEFAULT,
				TipoMovimientoCC.CREDITO, delta, "CUOTA_PRESTAMO", cuota.getId(), idempotencyKey + "-cc");

		outboxEventService.publicarEvento("CUOTA_PRESTAMO", cuota.getId(), "CUOTA_PAGADA", Map.of(
				"cuotaId", cuota.getId().toString(),
				"prestamoId", cuota.getPrestamo().getId().toString(),
				"montoCobrado", delta.toString(),
				"montoPagadoTotal", montoPagado.toString(),
				"estado", cuota.getEstado().toString(),
				"asientoId", asiento.getId().toString()));

		return cuota;
	}

	@Transactional
	public void delete(UUID id) {
		if (!cuotaPrestamoRepository.existsById(id)) {
			throw new ResourceNotFoundException("CuotaPrestamo", id);
		}
		cuotaPrestamoRepository.deleteById(id);
	}
}
