package com.example.fintech.decision;

import com.example.fintech.solicitud.SolicitudCredito;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * regla_id no se mapea: en este pase la regla está hardcodeada en
 * ScoringSagaService, no hay motor de reglas configurable (ver
 * feature-specs/2-implementar-sagas.md sección 5), así que esa columna
 * queda siempre NULL (es nullable en el schema).
 */
@Getter
@Setter
@Entity
@Table(name = "resultados_decision")
public class ResultadoDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitud_id", nullable = false)
	private SolicitudCredito solicitud;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private ResultadoDecisionTipo resultado;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> detalle;

	@Column(nullable = false)
	private OffsetDateTime fecha;

	@PrePersist
	void prePersist() {
		fecha = OffsetDateTime.now();
	}
}
