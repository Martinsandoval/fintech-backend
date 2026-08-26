package com.example.fintech.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxEventSink implements OutboxEventSink {

	private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventSink.class);

	@Override
	public void publicar(OutboxEvent evento) {
		log.info("outbox event publicado tipoEvento={} aggregateTipo={} aggregateId={} payload={}",
				evento.getTipoEvento(), evento.getAggregateTipo(), evento.getAggregateId(), evento.getPayload());
	}
}
