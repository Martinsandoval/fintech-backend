package com.example.fintech.idempotencia;

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
 * Dedup genérica de operaciones (no ligada a una tabla de negocio puntual,
 * a diferencia de los idempotency_key de movimientos_cta_cte/
 * asientos_contables). "resultado IS NULL" significa "todavía no se
 * completó" — es la señal que usa el job de reconciliación. Ver
 * feature-specs/2-implementar-sagas.md sección 2.
 */
@Getter
@Setter
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "key", nullable = false, length = 100)
	private String key;

	@Column(nullable = false, length = 100)
	private String operacion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> resultado;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void prePersist() {
		creadoEn = OffsetDateTime.now();
	}
}
