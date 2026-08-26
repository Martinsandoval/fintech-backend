package com.example.fintech.prestamo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cuotas")
public class CuotaPrestamoController {

	private final CuotaPrestamoService cuotaPrestamoService;

	public CuotaPrestamoController(CuotaPrestamoService cuotaPrestamoService) {
		this.cuotaPrestamoService = cuotaPrestamoService;
	}

	@GetMapping
	public List<CuotaPrestamo> findByPrestamo(@RequestParam UUID prestamoId) {
		return cuotaPrestamoService.findByPrestamo(prestamoId);
	}

	@GetMapping("/{id}")
	public CuotaPrestamo findById(@PathVariable UUID id) {
		return cuotaPrestamoService.findById(id);
	}

	@PostMapping
	public ResponseEntity<CuotaPrestamo> create(@Valid @RequestBody CuotaPrestamo cuota) {
		CuotaPrestamo creada = cuotaPrestamoService.create(cuota);
		return ResponseEntity.status(HttpStatus.CREATED).body(creada);
	}

	@PutMapping("/{id}/pago")
	public CuotaPrestamo registrarPago(@PathVariable UUID id, @Valid @RequestBody PagoRequest request) {
		return cuotaPrestamoService.registrarPago(id, request.montoPagado(), request.idempotencyKey());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		cuotaPrestamoService.delete(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * idempotencyKey: generarla una vez por intento real de pago y reusar la
	 * misma si se reintenta el mismo request (ver
	 * feature-specs/1-consistencia-datos.md sección 2).
	 */
	public record PagoRequest(@NotNull BigDecimal montoPagado, @NotBlank String idempotencyKey) {
	}
}
