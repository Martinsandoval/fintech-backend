package com.example.fintech.cuentacorriente;

import com.example.fintech.cliente.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "cuentas_corrientes")
public class CuentaCorriente {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@NotNull
	@Size(min = 3, max = 3)
	@Column(nullable = false, length = 3)
	private String moneda = "ARS";

	@NotNull
	@Column(nullable = false, precision = 16, scale = 2)
	private BigDecimal saldo = BigDecimal.ZERO;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private OffsetDateTime actualizadoEn;

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
