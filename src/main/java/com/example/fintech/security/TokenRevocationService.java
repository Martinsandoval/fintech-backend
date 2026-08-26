package com.example.fintech.security;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * estaRevocado() se consulta en cada request autenticado (desde
 * TokenNoRevocadoValidator), así que va cacheado — un SELECT por request
 * para el caso común (nada revocado) no es aceptable. revocar() invalida
 * la entrada de cache de ese jti puntual para que el logout sea efectivo
 * de inmediato, no recién cuando venza el TTL. Ver
 * feature-specs/5-revocacion-tokens.md secciones 2 y 3.
 */
@Service
@Transactional(readOnly = true)
public class TokenRevocationService {

	private final TokenRevocadoRepository tokenRevocadoRepository;

	public TokenRevocationService(TokenRevocadoRepository tokenRevocadoRepository) {
		this.tokenRevocadoRepository = tokenRevocadoRepository;
	}

	@Cacheable(cacheNames = "tokens-revocados", key = "#jti")
	public boolean estaRevocado(UUID jti) {
		return tokenRevocadoRepository.existsById(jti);
	}

	@Transactional
	@CacheEvict(cacheNames = "tokens-revocados", key = "#jti")
	public void revocar(UUID jti, UUID usuarioId, OffsetDateTime expiraEn) {
		if (!tokenRevocadoRepository.existsById(jti)) {
			tokenRevocadoRepository.save(new TokenRevocado(jti, usuarioId, expiraEn));
		}
	}

	@Transactional
	public long purgarExpirados() {
		return tokenRevocadoRepository.deleteByExpiraEnBefore(OffsetDateTime.now());
	}
}
