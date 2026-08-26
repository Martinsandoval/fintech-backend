package com.example.fintech.cuentacorriente;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sólo lectura: los movimientos se crean exclusivamente vía
 * CuentaCorrienteLedgerService desde los servicios de negocio, nunca por un
 * POST directo del cliente.
 */
@RestController
@RequestMapping("/api/movimientos-cta-cte")
public class MovimientoCtaCteController {

	private final CuentaCorrienteLedgerService cuentaCorrienteLedgerService;

	public MovimientoCtaCteController(CuentaCorrienteLedgerService cuentaCorrienteLedgerService) {
		this.cuentaCorrienteLedgerService = cuentaCorrienteLedgerService;
	}

	@GetMapping
	public List<MovimientoCtaCte> findByCuentaCorriente(@RequestParam UUID cuentaCorrienteId) {
		return cuentaCorrienteLedgerService.findByCuentaCorriente(cuentaCorrienteId);
	}
}
