package com.example.fintech.contabilidad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sólo lectura: las líneas se crean exclusivamente vía
 * AsientoContableService.crearConLineas, nunca por un POST directo.
 */
@RestController
@RequestMapping("/api/asientos-contables/{asientoId}/movimientos")
public class MovimientoContableController {

	private final AsientoContableService asientoContableService;

	public MovimientoContableController(AsientoContableService asientoContableService) {
		this.asientoContableService = asientoContableService;
	}

	@GetMapping
	public List<MovimientoContable> findByAsiento(@PathVariable UUID asientoId) {
		return asientoContableService.findLineas(asientoId);
	}
}
