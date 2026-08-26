package com.example.fintech.solicitud;

import com.example.fintech.cliente.Cliente;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "solicitudes_credito")
public class SolicitudCredito {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private TipoSolicitud tipo;

	@NotNull
	@DecimalMin(value = "0.01")
	@Column(name = "monto_solicitado", nullable = false, precision = 16, scale = 2)
	private BigDecimal montoSolicitado;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private EstadoSolicitud estado = EstadoSolicitud.INICIADA;

	@Column(name = "fecha_solicitud", nullable = false)
	private OffsetDateTime fechaSolicitud;

	@Column(name = "fecha_resolucion")
	private OffsetDateTime fechaResolucion;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private OffsetDateTime actualizadoEn;

	@PrePersist
	void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		if (fechaSolicitud == null) {
			fechaSolicitud = now;
		}
		creadoEn = now;
		actualizadoEn = now;
	}

	@PreUpdate
	void preUpdate() {
		actualizadoEn = OffsetDateTime.now();
	}
}
