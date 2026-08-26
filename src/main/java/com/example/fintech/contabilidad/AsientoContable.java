package com.example.fintech.contabilidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "asientos_contables")
public class AsientoContable {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private LocalDate fecha;

	@NotNull
	@Size(max = 300)
	@Column(nullable = false, length = 300)
	private String descripcion;

	@NotNull
	@Size(max = 50)
	@Column(name = "origen_tipo", nullable = false, length = 50)
	private String origenTipo;

	@NotNull
	@Column(name = "origen_id", nullable = false)
	private UUID origenId;

	@NotNull
	@Size(max = 100)
	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void prePersist() {
		if (fecha == null) {
			fecha = LocalDate.now();
		}
		creadoEn = OffsetDateTime.now();
	}
}
