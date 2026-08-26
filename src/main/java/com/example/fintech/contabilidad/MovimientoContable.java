package com.example.fintech.contabilidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Línea de un asiento contable (debe/haber). Se crea únicamente a través de
 * AsientoContableService.crearConLineas, que valida partida doble antes de
 * persistir — nunca directamente vía repositorio.
 */
@Getter
@Setter
@Entity
@Table(name = "movimientos_contables")
public class MovimientoContable {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "asiento_id", nullable = false)
	private AsientoContable asiento;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cuenta_contable_id", nullable = false)
	private PlanCuenta cuentaContable;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal debe = BigDecimal.ZERO;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal haber = BigDecimal.ZERO;
}
