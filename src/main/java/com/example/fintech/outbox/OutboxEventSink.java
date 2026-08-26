package com.example.fintech.outbox;

/**
 * Punto de extensión para publicar eventos outbox a un sistema externo
 * (SQS, etc). Reemplazar LoggingOutboxEventSink por una implementación real
 * es un cambio de una sola clase — ver feature-specs/1-consistencia-datos.md
 * sección 4.
 */
public interface OutboxEventSink {

	void publicar(OutboxEvent evento);
}
