package com.example.fintech.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Usuario interno de la app (analista/admin que la opera), no un `Cliente`
 * (sujeto de crédito). passwordHash nunca debe salir de esta clase hacia
 * una respuesta HTTP — los controllers de auth siempre devuelven
 * UsuarioResponse, nunca esta entidad directamente.
 */
@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, length = 100)
	private String apellido;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private RolUsuario rol = RolUsuario.ANALISTA;

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
