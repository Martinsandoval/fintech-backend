package com.example.fintech.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntry, UUID> {

	Optional<IdempotencyKeyEntry> findByOperacionAndKey(String operacion, String key);

	List<IdempotencyKeyEntry> findByOperacionAndResultadoIsNullAndCreadoEnBefore(String operacion,
			OffsetDateTime antesDe);
}
