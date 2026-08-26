package com.example.fintech.idempotencia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdempotencyKeyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;

	public IdempotencyKeyService(IdempotencyKeyRepository idempotencyKeyRepository) {
		this.idempotencyKeyRepository = idempotencyKeyRepository;
	}

	public Optional<IdempotencyKeyEntry> find(String operacion, String key) {
		return idempotencyKeyRepository.findByOperacionAndKey(operacion, key);
	}

	/**
	 * true si esta llamada reservó la key (el caller debe disparar el efecto
	 * externo); false si ya existía (alguien ya la disparó — no repetir).
	 * No protege contra una carrera verdaderamente concurrente dentro de la
	 * misma transacción — igual que en el ledger, una colisión real cae en
	 * la unique constraint y se traduce a 409 (ver GlobalExceptionHandler).
	 */
	@Transactional
	public boolean reservar(String operacion, String key) {
		if (idempotencyKeyRepository.findByOperacionAndKey(operacion, key).isPresent()) {
			return false;
		}
		IdempotencyKeyEntry entry = new IdempotencyKeyEntry();
		entry.setOperacion(operacion);
		entry.setKey(key);
		idempotencyKeyRepository.save(entry);
		return true;
	}

	@Transactional
	public void completar(String operacion, String key, Map<String, Object> resultado) {
		idempotencyKeyRepository.findByOperacionAndKey(operacion, key)
				.ifPresent(entry -> entry.setResultado(resultado));
	}

	public List<IdempotencyKeyEntry> findPendientesAntiguos(String operacion, OffsetDateTime antesDe) {
		return idempotencyKeyRepository.findByOperacionAndResultadoIsNullAndCreadoEnBefore(operacion, antesDe);
	}
}
