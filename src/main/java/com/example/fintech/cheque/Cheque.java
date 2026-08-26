package com.example.fintech.cheque;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.librador.Librador;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "cheques")
public class Cheque {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "librador_id", nullable = false)
	private Librador librador;

	@NotNull
	@Size(max = 30)
	@Column(nullable = false, length = 30)
	private String numero;

	@NotNull
	@Size(max = 100)
	@Column(nullable = false, length = 100)
	private String banco;

	@NotNull
	@DecimalMin(value = "0.01")
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal monto;

	@NotNull
	@Column(name = "fecha_emision", nullable = false)
	private LocalDate fechaEmision;

	@NotNull
	@Column(name = "fecha_vencimiento", nullable = false)
	private LocalDate fechaVencimiento;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private EstadoCheque estado = EstadoCheque.EN_CARTERA;

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
		creadoEn = now;
		actualizadoEn = now;
	}

	@PreUpdate
	void preUpdate() {
		actualizadoEn = OffsetDateTime.now();
	}
}
