package com.example.fintech.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Se combina con JwtValidators.createDefault() en SecurityConfig.jwtDecoder
 * — ver feature-specs/5-revocacion-tokens.md sección 2. Un token sin claim
 * "jti" (no debería pasar con JwtIssuer, pero por las dudas con uno emitido
 * a mano) no se puede revocar individualmente, así que pasa sin chequeo en
 * vez de romper.
 */
public class TokenNoRevocadoValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error TOKEN_REVOCADO = new OAuth2Error("token_revoked", "el token fue revocado", null);

	private final TokenRevocationService tokenRevocationService;

	public TokenNoRevocadoValidator(TokenRevocationService tokenRevocationService) {
		this.tokenRevocationService = tokenRevocationService;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		String jti = token.getId();
		if (jti == null) {
			return OAuth2TokenValidatorResult.success();
		}
		if (tokenRevocationService.estaRevocado(UUID.fromString(jti))) {
			return OAuth2TokenValidatorResult.failure(TOKEN_REVOCADO);
		}
		return OAuth2TokenValidatorResult.success();
	}
}
