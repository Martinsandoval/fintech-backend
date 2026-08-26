package com.example.fintech.integracion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegracionLogRepository extends JpaRepository<IntegracionLog, UUID> {

	List<IntegracionLog> findByServicioOrderByFechaDesc(String servicio);
}
