package com.example.fintech.contabilidad;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/plan-cuentas")
public class PlanCuentaController {

	private final PlanCuentaService planCuentaService;

	public PlanCuentaController(PlanCuentaService planCuentaService) {
		this.planCuentaService = planCuentaService;
	}

	@GetMapping
	public List<PlanCuenta> findAll() {
		return planCuentaService.findAll();
	}

	@GetMapping("/{id}")
	public PlanCuenta findById(@PathVariable UUID id) {
		return planCuentaService.findById(id);
	}

	@PostMapping
	public ResponseEntity<PlanCuenta> create(@Valid @RequestBody PlanCuenta planCuenta) {
		PlanCuenta creada = planCuentaService.create(planCuenta);
		return ResponseEntity.status(HttpStatus.CREATED).body(creada);
	}

	@PutMapping("/{id}")
	public PlanCuenta update(@PathVariable UUID id, @Valid @RequestBody PlanCuenta planCuenta) {
		return planCuentaService.update(id, planCuenta);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		planCuentaService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
