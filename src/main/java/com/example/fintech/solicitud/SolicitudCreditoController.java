package com.example.fintech.solicitud;

import com.example.fintech.auditoria.AuditoriaAccionService;
import com.example.fintech.scoring.ScoringSagaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudCreditoController {

	private final SolicitudCreditoService solicitudCreditoService;
	private final ScoringSagaService scoringSagaService;
	private final AuditoriaAccionService auditoriaAccionService;

	public SolicitudCreditoController(SolicitudCreditoService solicitudCreditoService,
			ScoringSagaService scoringSagaService, AuditoriaAccionService auditoriaAccionService) {
		this.solicitudCreditoService = solicitudCreditoService;
		this.scoringSagaService = scoringSagaService;
		this.auditoriaAccionService = auditoriaAccionService;
	}

	@GetMapping
	public List<SolicitudCredito> findAll(@RequestParam(required = false) UUID clienteId) {
		return clienteId != null
				? solicitudCreditoService.findByCliente(clienteId)
				: solicitudCreditoService.findAll();
	}

	@GetMapping("/{id}")
	public SolicitudCredito findById(@PathVariable UUID id) {
		return solicitudCreditoService.findById(id);
	}

	@PostMapping
	public ResponseEntity<SolicitudCredito> create(@Valid @RequestBody SolicitudCredito solicitud) {
		SolicitudCredito creada = solicitudCreditoService.create(solicitud);
		return ResponseEntity.status(HttpStatus.CREATED).body(creada);
	}

	@PostMapping("/{id}/iniciar-evaluacion")
	public SolicitudCredito iniciarEvaluacion(@PathVariable UUID id) {
		return scoringSagaService.iniciarEvaluacion(id);
	}

	@PutMapping("/{id}/estado")
	public SolicitudCredito actualizarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoUpdateRequest request,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
		String estadoAnterior = solicitudCreditoService.findById(id).getEstado().name();
		SolicitudCredito actualizada = solicitudCreditoService.actualizarEstado(id, request.estado());
		auditoriaAccionService.registrar(
				UUID.fromString(jwt.getClaimAsString("usuarioId")), jwt.getSubject(),
				"SOLICITUD_ESTADO_ACTUALIZADO", "SOLICITUD_CREDITO", id,
				estadoAnterior, actualizada.getEstado().name(), httpRequest.getRemoteAddr());
		return actualizada;
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		solicitudCreditoService.delete(id);
		return ResponseEntity.noContent().build();
	}

	public record EstadoUpdateRequest(@NotNull EstadoSolicitud estado) {
	}
}
