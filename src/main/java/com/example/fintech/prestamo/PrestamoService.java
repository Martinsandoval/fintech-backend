package com.example.fintech.prestamo;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.common.ResourceNotFoundException;
import com.example.fintech.contabilidad.AsientoContableService;
import com.example.fintech.contabilidad.LineaContable;
import com.example.fintech.contabilidad.PlanCuenta;
import com.example.fintech.contabilidad.PlanCuentaService;
import com.example.fintech.outbox.OutboxEventService;
import com.example.fintech.solicitud.SolicitudCredito;
import com.example.fintech.solicitud.SolicitudCreditoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PrestamoService.create booka el asiento de originación (Debe "Préstamos
 * otorgados" / Haber "Banco"), genera el cronograma de cuotas y un evento
 * outbox, todo en la misma transacción que crea el préstamo. Ver
 * feature-specs/1-consistencia-datos.md sección 5 y
 * feature-specs/6-motor-amortizacion.md.
 *
 * Inyecta CuotaPrestamoRepository directo (no CuotaPrestamoService): ese
 * service ya depende de PrestamoService para resolver el préstamo de cada
 * cuota que se crea a mano vía POST /api/cuotas — inyectarlo acá cerraría
 * un ciclo.
 */
@Service
@Transactional(readOnly = true)
public class PrestamoService {

	private static final String CUENTA_PRESTAMOS_OTORGADOS = "1.1.03";
	private static final String CUENTA_BANCO = "1.1.02";

	private final PrestamoRepository prestamoRepository;
	private final CuotaPrestamoRepository cuotaPrestamoRepository;
	private final ClienteService clienteService;
	private final SolicitudCreditoService solicitudCreditoService;
	private final AsientoContableService asientoContableService;
	private final PlanCuentaService planCuentaService;
	private final OutboxEventService outboxEventService;
	private final AmortizacionService amortizacionService;

	public PrestamoService(PrestamoRepository prestamoRepository, CuotaPrestamoRepository cuotaPrestamoRepository,
			ClienteService clienteService, SolicitudCreditoService solicitudCreditoService,
			AsientoContableService asientoContableService, PlanCuentaService planCuentaService,
			OutboxEventService outboxEventService, AmortizacionService amortizacionService) {
		this.prestamoRepository = prestamoRepository;
		this.cuotaPrestamoRepository = cuotaPrestamoRepository;
		this.clienteService = clienteService;
		this.solicitudCreditoService = solicitudCreditoService;
		this.asientoContableService = asientoContableService;
		this.planCuentaService = planCuentaService;
		this.outboxEventService = outboxEventService;
		this.amortizacionService = amortizacionService;
	}

	public List<Prestamo> findAll() {
		return prestamoRepository.findAll();
	}

	public Prestamo findById(UUID id) {
		return prestamoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Prestamo", id));
	}

	public List<Prestamo> findByCliente(UUID clienteId) {
		return prestamoRepository.findByClienteId(clienteId);
	}

	@Transactional
	public Prestamo create(Prestamo prestamo) {
		if (prestamo.getCliente() == null || prestamo.getCliente().getId() == null) {
			throw new IllegalArgumentException("el préstamo debe referenciar un cliente existente");
		}
		Cliente cliente = clienteService.findById(prestamo.getCliente().getId());
		prestamo.setCliente(cliente);

		if (prestamo.getSolicitud() != null && prestamo.getSolicitud().getId() != null) {
			SolicitudCredito solicitud = solicitudCreditoService.findById(prestamo.getSolicitud().getId());
			prestamo.setSolicitud(solicitud);
		} else {
			prestamo.setSolicitud(null);
		}

		prestamo.setId(null);
		prestamo.setEstado(EstadoPrestamo.ORIGINADO);
		Prestamo guardado = prestamoRepository.save(prestamo);

		for (CuotaPrestamo cuota : amortizacionService.generar(guardado)) {
			cuotaPrestamoRepository.save(cuota);
		}

		PlanCuenta prestamosOtorgados = planCuentaService.findByCodigo(CUENTA_PRESTAMOS_OTORGADOS);
		PlanCuenta banco = planCuentaService.findByCodigo(CUENTA_BANCO);
		asientoContableService.crearConLineas(
				"Originación préstamo " + guardado.getId(),
				"PRESTAMO",
				guardado.getId(),
				"prestamo-originacion-" + guardado.getId(),
				List.of(
						LineaContable.debe(prestamosOtorgados.getId(), guardado.getMonto()),
						LineaContable.haber(banco.getId(), guardado.getMonto())));

		outboxEventService.publicarEvento("PRESTAMO", guardado.getId(), "PRESTAMO_ORIGINADO", Map.of(
				"prestamoId", guardado.getId().toString(),
				"clienteId", guardado.getCliente().getId().toString(),
				"monto", guardado.getMonto().toString()));

		return guardado;
	}

	@Transactional
	public Prestamo actualizarEstado(UUID id, EstadoPrestamo nuevoEstado) {
		Prestamo existente = findById(id);
		existente.setEstado(nuevoEstado);
		return existente;
	}

	@Transactional
	public void delete(UUID id) {
		if (!prestamoRepository.existsById(id)) {
			throw new ResourceNotFoundException("Prestamo", id);
		}
		prestamoRepository.deleteById(id);
	}
}
