package com.example.fintech.cuentacorriente;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila de ledger append-only. Se crea únicamente a través de
 * CuentaCorrienteLedgerService — nunca directamente vía repositorio desde
 * otro service, y no tiene endpoint de escritura en el controller.
 */
@Getter
@Setter
@Entity
@Table(name = "movimientos_cta_cte")
public class MovimientoCtaCte {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cuenta_corriente_id", nullable = false)
	private CuentaCorriente cuentaCorriente;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private TipoMovimientoCC tipo;

	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal monto;

	@Column(name = "saldo_posterior", nullable = false, precision = 16, scale = 2)
	private BigDecimal saldoPosterior;

	@Column(name = "referencia_tipo", nullable = false, length = 50)
	private String referenciaTipo;

	@Column(name = "referencia_id", nullable = false)
	private UUID referenciaId;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(nullable = false)
	private OffsetDateTime fecha;

	@PrePersist
	void prePersist() {
		fecha = OffsetDateTime.now();
	}
}
