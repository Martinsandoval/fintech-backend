package com.example.fintech.cliente;

import com.example.fintech.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "clientes")
public class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@Pattern(regexp = "\\d{11}", message = "el CUIT debe tener 11 dígitos")
	@Convert(converter = EncryptedStringConverter.class)
	@Column(nullable = false)
	private String cuit;

	@NotNull
	@Size(max = 200)
	@Column(name = "razon_social", nullable = false, length = 200)
	private String razonSocial;

	@NotNull
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "tipo_persona", nullable = false)
	private TipoPersona tipoPersona;

	@Email
	@Size(max = 200)
	@Column(length = 200)
	private String email;

	@Size(max = 30)
	@Column(length = 30)
	private String telefono;

	@Size(max = 300)
	@Column(length = 300)
	private String direccion;

	@Column(name = "score_nosis")
	private Integer scoreNosis;

	@Column(name = "fecha_ultimo_score")
	private OffsetDateTime fechaUltimoScore;

	@Column(nullable = false)
	private boolean activo = true;

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
