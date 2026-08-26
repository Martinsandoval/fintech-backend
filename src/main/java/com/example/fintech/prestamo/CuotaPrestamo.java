package com.example.fintech.prestamo;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "cuotas_prestamo")
public class CuotaPrestamo {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "prestamo_id", nullable = false)
	private Prestamo prestamo;

	@NotNull
	@Min(1)
	@Column(nullable = false)
	private Integer numero;

	@NotNull
	@DecimalMin(value = "0.01")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal monto;

	@NotNull
	@DecimalMin(value = "0.0")
	@Column(name = "monto_pagado", nullable = false, precision = 16, scale = 2)
	private BigDecimal montoPagado = BigDecimal.ZERO;

	@NotNull
	@Column(name = "fecha_vencimiento", nullable = false)
	private LocalDate fechaVencimiento;

	@Column(name = "fecha_pago")
	private LocalDate fechaPago;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private EstadoCuota estado = EstadoCuota.PENDIENTE;

	@Version
	@Column(nullable = false)
	private Long version;
}
