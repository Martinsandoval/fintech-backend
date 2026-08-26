package com.example.fintech.solicitud;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SolicitudCreditoService {

	private final SolicitudCreditoRepository solicitudCreditoRepository;
	private final ClienteService clienteService;

	public SolicitudCreditoService(SolicitudCreditoRepository solicitudCreditoRepository,
			ClienteService clienteService) {
		this.solicitudCreditoRepository = solicitudCreditoRepository;
		this.clienteService = clienteService;
	}

	public List<SolicitudCredito> findAll() {
		return solicitudCreditoRepository.findAll();
	}

	public SolicitudCredito findById(UUID id) {
		return solicitudCreditoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("SolicitudCredito", id));
	}

	/**
	 * Resuelve el CUIT del cliente dentro de la misma transacción/sesión de
	 * Hibernate (evita que un caller no transaccional dispare un
	 * LazyInitializationException al tocar la relación cliente perezosa
	 * después de que este método ya devolvió).
	 */
	public String obtenerCuitCliente(UUID solicitudId) {
		return findById(solicitudId).getCliente().getCuit();
	}

	public List<SolicitudCredito> findByCliente(UUID clienteId) {
		return solicitudCreditoRepository.findByClienteId(clienteId);
	}

	@Transactional
	public SolicitudCredito create(SolicitudCredito solicitud) {
		if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
			throw new IllegalArgumentException("la solicitud debe referenciar un cliente existente");
		}
		Cliente cliente = clienteService.findById(solicitud.getCliente().getId());
		solicitud.setId(null);
		solicitud.setCliente(cliente);
		solicitud.setEstado(EstadoSolicitud.INICIADA);
		solicitud.setFechaResolucion(null);
		return solicitudCreditoRepository.save(solicitud);
	}

	@Transactional
	public SolicitudCredito actualizarEstado(UUID id, EstadoSolicitud nuevoEstado) {
		SolicitudCredito existente = findById(id);
		existente.setEstado(nuevoEstado);
		if (isEstadoResolutivo(nuevoEstado)) {
			existente.setFechaResolucion(java.time.OffsetDateTime.now());
		}
		return existente;
	}

	private boolean isEstadoResolutivo(EstadoSolicitud estado) {
		return estado == EstadoSolicitud.APROBADA
				|| estado == EstadoSolicitud.RECHAZADA
				|| estado == EstadoSolicitud.CANCELADA;
	}

	@Transactional
	public void delete(UUID id) {
		if (!solicitudCreditoRepository.existsById(id)) {
			throw new ResourceNotFoundException("SolicitudCredito", id);
		}
		solicitudCreditoRepository.deleteById(id);
	}
}
