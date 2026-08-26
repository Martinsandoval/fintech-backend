package com.example.fintech.cuentacorriente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimientoCtaCteRepository extends JpaRepository<MovimientoCtaCte, UUID> {

	Optional<MovimientoCtaCte> findByIdempotencyKey(String idempotencyKey);

	List<MovimientoCtaCte> findByCuentaCorrienteIdOrderByFechaDesc(UUID cuentaCorrienteId);
}
