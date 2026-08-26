package com.example.fintech.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sin setters a propósito: se construye completa de una vez y nunca se
 * vuelve a tocar — el trigger de la tabla (ver V4__auditoria.sql) rechaza
 * cualquier UPDATE/DELETE igual, pero que ni la entidad Java ofrezca cómo
 * mutarla es la primera línea de defensa. Ver
 * feature-specs/4-auditoria.md sección 3.
 */
@Getter
@Entity
@Table(name = "auditoria_acciones")
public class AuditoriaAccion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "usuario_id", nullable = false)
	private UUID usuarioId;

	@Column(name = "usuario_email", nullable = false)
	private String usuarioEmail;

	@Column(nullable = false, length = 60)
	private String accion;

	@Column(name = "entidad_tipo", nullable = false, length = 50)
	private String entidadTipo;

	@Column(name = "entidad_id", nullable = false)
	private UUID entidadId;

	@Column(name = "estado_anterior", length = 50)
	private String estadoAnterior;

	@Column(name = "estado_nuevo", nullable = false, length = 50)
	private String estadoNuevo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> detalle;

	@Column(name = "ip_origen", length = 45)
	private String ipOrigen;

	@Column(nullable = false)
	private OffsetDateTime fecha;

	protected AuditoriaAccion() {
		// JPA
	}

	public AuditoriaAccion(UUID usuarioId, String usuarioEmail, String accion, String entidadTipo, UUID entidadId,
			String estadoAnterior, String estadoNuevo, Map<String, Object> detalle, String ipOrigen) {
		this.usuarioId = usuarioId;
		this.usuarioEmail = usuarioEmail;
		this.accion = accion;
		this.entidadTipo = entidadTipo;
		this.entidadId = entidadId;
		this.estadoAnterior = estadoAnterior;
		this.estadoNuevo = estadoNuevo;
		this.detalle = detalle;
		this.ipOrigen = ipOrigen;
		this.fecha = OffsetDateTime.now();
	}
}
