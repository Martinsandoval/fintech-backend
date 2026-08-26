package com.example.fintech.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OutboxEventService {

	private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxEventSink outboxEventSink;

	public OutboxEventService(OutboxEventRepository outboxEventRepository, OutboxEventSink outboxEventSink) {
		this.outboxEventRepository = outboxEventRepository;
		this.outboxEventSink = outboxEventSink;
	}

	/**
	 * Se llama siempre dentro de la transacción de negocio que ya está
	 * abierta (nunca abre una propia): si esa transacción hace rollback, el
	 * evento nunca se escribe.
	 */
	@Transactional
	public OutboxEvent publicarEvento(String aggregateTipo, UUID aggregateId, String tipoEvento,
			Map<String, Object> payload) {
		OutboxEvent evento = new OutboxEvent();
		evento.setAggregateTipo(aggregateTipo);
		evento.setAggregateId(aggregateId);
		evento.setTipoEvento(tipoEvento);
		evento.setPayload(payload);
		return outboxEventRepository.save(evento);
	}

	public List<OutboxEvent> findPendientes() {
		return outboxEventRepository.findByPublicado(false);
	}

	/**
	 * Entrega un evento puntual al sink y lo marca publicado, en su propia
	 * transacción corta. Pensado para ser invocado desde OutboxEventPublisher
	 * (un bean distinto: si se llamara desde otro método de esta misma clase
	 * el proxy de @Transactional no se aplicaría).
	 */
	@Transactional
	public void publicarUno(UUID id) {
		OutboxEvent evento = outboxEventRepository.findById(id).orElse(null);
		if (evento == null || evento.isPublicado()) {
			return;
		}
		try {
			outboxEventSink.publicar(evento);
			evento.setPublicado(true);
			evento.setPublicadoEn(OffsetDateTime.now());
		} catch (RuntimeException e) {
			log.error("no se pudo publicar el evento outbox {}", id, e);
		}
	}
}
