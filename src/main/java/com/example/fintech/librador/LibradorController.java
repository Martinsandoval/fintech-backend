package com.example.fintech.librador;

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
@RequestMapping("/api/libradores")
public class LibradorController {

	private final LibradorService libradorService;

	public LibradorController(LibradorService libradorService) {
		this.libradorService = libradorService;
	}

	@GetMapping
	public List<Librador> findAll() {
		return libradorService.findAll();
	}

	@GetMapping("/{id}")
	public Librador findById(@PathVariable UUID id) {
		return libradorService.findById(id);
	}

	@PostMapping
	public ResponseEntity<Librador> create(@Valid @RequestBody Librador librador) {
		Librador creado = libradorService.create(librador);
		return ResponseEntity.status(HttpStatus.CREATED).body(creado);
	}

	@PutMapping("/{id}")
	public Librador update(@PathVariable UUID id, @Valid @RequestBody Librador librador) {
		return libradorService.update(id, librador);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		libradorService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
