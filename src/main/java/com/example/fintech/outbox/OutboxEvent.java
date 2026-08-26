package com.example.fintech.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Se crea únicamente vía OutboxEventService.publicarEvento, siempre dentro
 * de la misma transacción que la operación de negocio que la origina. Ver
 * feature-specs/1-consistencia-datos.md sección 4.
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "aggregate_tipo", nullable = false, length = 50)
	private String aggregateTipo;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "tipo_evento", nullable = false, length = 100)
	private String tipoEvento;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Column(nullable = false)
	private boolean publicado = false;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "publicado_en")
	private OffsetDateTime publicadoEn;

	@PrePersist
	void prePersist() {
		creadoEn = OffsetDateTime.now();
	}
}
