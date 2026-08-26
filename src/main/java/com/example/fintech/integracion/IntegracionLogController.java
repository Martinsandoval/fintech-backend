package com.example.fintech.integracion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sólo lectura: los registros se crean exclusivamente vía
 * IntegracionLogService.registrar desde los clientes de integraciones.
 */
@RestController
@RequestMapping("/api/integraciones-log")
public class IntegracionLogController {

	private final IntegracionLogService integracionLogService;

	public IntegracionLogController(IntegracionLogService integracionLogService) {
		this.integracionLogService = integracionLogService;
	}

	@GetMapping
	public List<IntegracionLog> findByServicio(@RequestParam String servicio) {
		return integracionLogService.findByServicio(servicio);
	}
}
