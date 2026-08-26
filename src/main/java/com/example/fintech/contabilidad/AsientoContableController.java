package com.example.fintech.contabilidad;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/asientos-contables")
public class AsientoContableController {

	private final AsientoContableService asientoContableService;

	public AsientoContableController(AsientoContableService asientoContableService) {
		this.asientoContableService = asientoContableService;
	}

	@GetMapping
	public List<AsientoContable> findAll(
			@RequestParam(required = false) String origenTipo,
			@RequestParam(required = false) UUID origenId) {
		if (origenTipo != null && origenId != null) {
			return asientoContableService.findByOrigen(origenTipo, origenId);
		}
		return asientoContableService.findAll();
	}

	@GetMapping("/{id}")
	public AsientoContable findById(@PathVariable UUID id) {
		return asientoContableService.findById(id);
	}

	@PostMapping
	public ResponseEntity<AsientoContable> create(@Valid @RequestBody AsientoContable asiento) {
		AsientoContable creado = asientoContableService.create(asiento);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		asientoContableService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
