package com.example.fintech.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila = un JWT revocado antes de su expiración natural. No tiene
 * setters — se crea completa o no se crea; la única otra operación válida
 * es borrarla (TokenRevocationCleanupJob, una vez que expira_en ya pasó
 * de todas formas). Ver feature-specs/5-revocacion-tokens.md.
 */
@Getter
@Entity
@Table(name = "tokens_revocados")
public class TokenRevocado {

	@Id
	private UUID jti;

	@Column(name = "usuario_id", nullable = false)
	private UUID usuarioId;

	@Column(name = "expira_en", nullable = false)
	private OffsetDateTime expiraEn;

	@Column(name = "revocado_en", nullable = false)
	private OffsetDateTime revocadoEn;

	protected TokenRevocado() {
		// JPA
	}

	public TokenRevocado(UUID jti, UUID usuarioId, OffsetDateTime expiraEn) {
		this.jti = jti;
		this.usuarioId = usuarioId;
		this.expiraEn = expiraEn;
		this.revocadoEn = OffsetDateTime.now();
	}
}
