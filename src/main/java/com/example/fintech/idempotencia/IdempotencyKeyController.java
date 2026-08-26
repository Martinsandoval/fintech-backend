package com.example.fintech.idempotencia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sólo lectura, para observabilidad: útil para ver qué sagas quedaron
 * "esperando" (resultado IS NULL) sin tener que consultar la base a mano.
 */
@RestController
@RequestMapping("/api/idempotency-keys")
public class IdempotencyKeyController {

	private final IdempotencyKeyService idempotencyKeyService;

	public IdempotencyKeyController(IdempotencyKeyService idempotencyKeyService) {
		this.idempotencyKeyService = idempotencyKeyService;
	}

	@GetMapping("/pendientes")
	public List<IdempotencyKeyEntry> findPendientes(@RequestParam String operacion) {
		return idempotencyKeyService.findPendientesAntiguos(operacion, OffsetDateTime.now());
	}
}
