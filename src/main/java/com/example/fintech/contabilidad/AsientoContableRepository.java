package com.example.fintech.contabilidad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsientoContableRepository extends JpaRepository<AsientoContable, UUID> {

	List<AsientoContable> findByOrigenTipoAndOrigenId(String origenTipo, UUID origenId);

	Optional<AsientoContable> findByIdempotencyKey(String idempotencyKey);

	boolean existsByIdempotencyKey(String idempotencyKey);
}
