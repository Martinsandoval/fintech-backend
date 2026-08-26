package com.example.fintech.librador;

import com.example.fintech.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "libradores")
public class Librador {

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

	@Column(name = "creado_en", nullable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@PrePersist
	void prePersist() {
		creadoEn = OffsetDateTime.now();
	}
}
