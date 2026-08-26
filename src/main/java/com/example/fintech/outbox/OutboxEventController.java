package com.example.fintech.outbox;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sólo lectura, para observabilidad/debug: los eventos se crean
 * exclusivamente vía OutboxEventService.publicarEvento desde los servicios
 * de negocio.
 */
@RestController
@RequestMapping("/api/outbox-events")
public class OutboxEventController {

	private final OutboxEventService outboxEventService;

	public OutboxEventController(OutboxEventService outboxEventService) {
		this.outboxEventService = outboxEventService;
	}

	@GetMapping("/pendientes")
	public List<OutboxEvent> findPendientes() {
		return outboxEventService.findPendientes();
	}
}
