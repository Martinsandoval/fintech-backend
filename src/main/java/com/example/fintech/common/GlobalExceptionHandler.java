package com.example.fintech.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(HttpStatus.BAD_REQUEST, ex.getMessage()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body(HttpStatus.UNAUTHORIZED, ex.getMessage()));
	}

	/**
	 * Choque de escritura concurrente: locking optimista (columna version) o
	 * una unique constraint de idempotencia (dos requests con la misma
	 * idempotency_key llegaron a la vez). Ver
	 * feature-specs/1-consistencia-datos.md secciones 1 y 2 — el cliente debe
	 * releer el recurso, no reintentar la escritura a ciegas.
	 */
	@ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
	public ResponseEntity<Map<String, Object>> handleConflict(Exception ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,
				"conflicto de escritura concurrente, releer el recurso antes de reintentar"));
	}

	private Map<String, Object> body(HttpStatus status, String message) {
		return Map.of(
				"timestamp", OffsetDateTime.now().toString(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", message == null ? "" : message
		);
	}
}
