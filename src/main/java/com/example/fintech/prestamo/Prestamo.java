package com.example.fintech.prestamo;

import com.example.fintech.cliente.Cliente;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "prestamos")
public class Prestamo {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitud_id")
	private SolicitudCredito solicitud;

	@NotNull
	@DecimalMin(value = "0.01")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal monto;

	@NotNull
	@DecimalMin(value = "0.0")
	@Column(name = "tasa_anual", nullable = false, precision = 7, scale = 4)
	private BigDecimal tasaAnual;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "sistema_amortizacion", nullable = false)
	private SistemaAmortizacion sistemaAmortizacion;

	@NotNull
	@Min(1)
	@Column(name = "plazo_meses", nullable = false)
	private Integer plazoMeses;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private EstadoPrestamo estado = EstadoPrestamo.ORIGINADO;

	@Column(name = "fecha_originacion", nullable = false)
	private LocalDate fechaOriginacion;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private OffsetDateTime actualizadoEn;

	@Version
	@Column(nullable = false)
	private Long version;

	@PrePersist
	void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		if (fechaOriginacion == null) {
			fechaOriginacion = LocalDate.now();
		}
		creadoEn = now;
		actualizadoEn = now;
	}

	@PreUpdate
	void preUpdate() {
		actualizadoEn = OffsetDateTime.now();
	}
}
