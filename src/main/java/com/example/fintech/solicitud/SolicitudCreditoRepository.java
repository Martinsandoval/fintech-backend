package com.example.fintech.solicitud;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SolicitudCreditoRepository extends JpaRepository<SolicitudCredito, UUID> {

	List<SolicitudCredito> findByClienteId(UUID clienteId);

	List<SolicitudCredito> findByEstado(EstadoSolicitud estado);
}
