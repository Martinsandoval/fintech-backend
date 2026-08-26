package com.example.fintech.cheque;

import com.example.fintech.auditoria.AuditoriaAccionService;
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
@RequestMapping("/api/cheques")
public class ChequeController {

	private final ChequeService chequeService;
	private final AuditoriaAccionService auditoriaAccionService;

	public ChequeController(ChequeService chequeService, AuditoriaAccionService auditoriaAccionService) {
		this.chequeService = chequeService;
		this.auditoriaAccionService = auditoriaAccionService;
	}

	@GetMapping
	public List<Cheque> findAll(@RequestParam(required = false) UUID clienteId) {
		return clienteId != null ? chequeService.findByCliente(clienteId) : chequeService.findAll();
	}

	@GetMapping("/{id}")
	public Cheque findById(@PathVariable UUID id) {
		return chequeService.findById(id);
	}

	@PostMapping
	public ResponseEntity<Cheque> create(@Valid @RequestBody Cheque cheque) {
		Cheque creado = chequeService.create(cheque);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@PutMapping("/{id}/estado")
	public Cheque actualizarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoUpdateRequest request,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
		String estadoAnterior = chequeService.findById(id).getEstado().name();
		Cheque actualizado = chequeService.actualizarEstado(id, request.estado());
		auditoriaAccionService.registrar(
				UUID.fromString(jwt.getClaimAsString("usuarioId")), jwt.getSubject(),
				"CHEQUE_ESTADO_ACTUALIZADO", "CHEQUE", id,
				estadoAnterior, actualizado.getEstado().name(), httpRequest.getRemoteAddr());
		return actualizado;
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		chequeService.delete(id);
		return ResponseEntity.noContent().build();
	}

	public record EstadoUpdateRequest(@NotNull EstadoCheque estado) {
	}
}
