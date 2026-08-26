package com.example.fintech.auditoria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sólo lectura, restringido a ADMIN — ver feature-specs/4-auditoria.md
 * sección 4. Las filas se crean exclusivamente vía
 * AuditoriaAccionService.registrar desde los controllers de negocio.
 */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMIN')")
public class AuditoriaAccionController {

	private final AuditoriaAccionService auditoriaAccionService;

	public AuditoriaAccionController(AuditoriaAccionService auditoriaAccionService) {
		this.auditoriaAccionService = auditoriaAccionService;
	}

	@GetMapping
	public List<AuditoriaAccion> findAll(
			@RequestParam(required = false) String entidadTipo,
			@RequestParam(required = false) UUID entidadId,
			@RequestParam(required = false) UUID usuarioId) {
		if (entidadTipo != null && entidadId != null) {
			return auditoriaAccionService.findByEntidad(entidadTipo, entidadId);
		}
		if (usuarioId != null) {
			return auditoriaAccionService.findByUsuario(usuarioId);
		}
		return auditoriaAccionService.findAll();
	}
}
