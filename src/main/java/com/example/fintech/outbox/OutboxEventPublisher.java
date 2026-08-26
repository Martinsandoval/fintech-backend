package com.example.fintech.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxEventPublisher {

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxEventService outboxEventService;

	public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, OutboxEventService outboxEventService) {
		this.outboxEventRepository = outboxEventRepository;
		this.outboxEventService = outboxEventService;
	}

	@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
	public void publicarPendientes() {
		List<OutboxEvent> pendientes = outboxEventRepository.findTop50ByPublicadoFalseOrderByCreadoEnAsc();
		for (OutboxEvent evento : pendientes) {
			outboxEventService.publicarUno(evento.getId());
		}
	}
}
