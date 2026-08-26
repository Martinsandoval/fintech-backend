package com.example.fintech.cuentacorriente;

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
@RequestMapping("/api/cuentas-corrientes")
public class CuentaCorrienteController {

	private final CuentaCorrienteService cuentaCorrienteService;

	public CuentaCorrienteController(CuentaCorrienteService cuentaCorrienteService) {
		this.cuentaCorrienteService = cuentaCorrienteService;
	}

	@GetMapping
	public List<CuentaCorriente> findAll(@RequestParam(required = false) UUID clienteId) {
		return clienteId != null
				? cuentaCorrienteService.findByCliente(clienteId)
				: cuentaCorrienteService.findAll();
	}

	@GetMapping("/{id}")
	public CuentaCorriente findById(@PathVariable UUID id) {
		return cuentaCorrienteService.findById(id);
	}

	@PostMapping
	public ResponseEntity<CuentaCorriente> create(@Valid @RequestBody CuentaCorriente cuenta) {
		CuentaCorriente creada = cuentaCorrienteService.create(cuenta);
		return ResponseEntity.status(HttpStatus.CREATED).body(creada);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		cuentaCorrienteService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
