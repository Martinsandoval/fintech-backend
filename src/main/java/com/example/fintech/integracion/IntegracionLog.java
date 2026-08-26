package com.example.fintech.integracion;

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
 * Registro de auditoría de cada intento de llamada externa (Nosis/AFIP/BNA),
 * exitoso o no. Se escribe siempre, nunca sólo en el camino feliz — ver
 * feature-specs/2-implementar-sagas.md sección 3.
 */
@Getter
@Setter
@Entity
@Table(name = "integraciones_log")
public class IntegracionLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 30)
	private String servicio;

	@Column(nullable = false, length = 200)
	private String endpoint;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> request;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> response;

	@Column(name = "estado_http")
	private Integer estadoHttp;

	@Column(nullable = false)
	private boolean exitoso;

	@Column(name = "duracion_ms")
	private Integer duracionMs;

	@Column(nullable = false)
	private OffsetDateTime fecha;

	@PrePersist
	void prePersist() {
		fecha = OffsetDateTime.now();
	}
}
