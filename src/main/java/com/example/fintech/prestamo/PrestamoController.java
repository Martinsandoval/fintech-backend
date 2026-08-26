package com.example.fintech.prestamo;

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
@RequestMapping("/api/prestamos")
public class PrestamoController {

	private final PrestamoService prestamoService;
	private final AuditoriaAccionService auditoriaAccionService;

	public PrestamoController(PrestamoService prestamoService, AuditoriaAccionService auditoriaAccionService) {
		this.prestamoService = prestamoService;
		this.auditoriaAccionService = auditoriaAccionService;
	}

	@GetMapping
	public List<Prestamo> findAll(@RequestParam(required = false) UUID clienteId) {
		return clienteId != null ? prestamoService.findByCliente(clienteId) : prestamoService.findAll();
	}

	@GetMapping("/{id}")
	public Prestamo findById(@PathVariable UUID id) {
		return prestamoService.findById(id);
	}

	@PostMapping
	public ResponseEntity<Prestamo> create(@Valid @RequestBody Prestamo prestamo) {
		Prestamo creado = prestamoService.create(prestamo);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@PutMapping("/{id}/estado")
	public Prestamo actualizarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoUpdateRequest request,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
		String estadoAnterior = prestamoService.findById(id).getEstado().name();
		Prestamo actualizado = prestamoService.actualizarEstado(id, request.estado());
		auditoriaAccionService.registrar(
				UUID.fromString(jwt.getClaimAsString("usuarioId")), jwt.getSubject(),
				"PRESTAMO_ESTADO_ACTUALIZADO", "PRESTAMO", id,
				estadoAnterior, actualizado.getEstado().name(), httpRequest.getRemoteAddr());
		return actualizado;
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		prestamoService.delete(id);
		return ResponseEntity.noContent().build();
	}

	public record EstadoUpdateRequest(@NotNull EstadoPrestamo estado) {
	}
}
