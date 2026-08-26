package com.example.fintech.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Firma los JWT que emite /api/auth/login. Misma clave y mismo algoritmo
 * (HS256, bytes UTF-8 de app.jwt.secret) que SecurityConfig.jwtDecoder usa
 * para validarlos del otro lado — si esto cambia, tiene que cambiar ahí
 * también.
 */
@Component
public class JwtIssuer {

	private final byte[] secretBytes;
	private final long expirationMs;

	public JwtIssuer(@Value("${app.jwt.secret}") String jwtSecret,
			@Value("${app.jwt.expiration-ms}") long expirationMs) {
		this.secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
		if (this.secretBytes.length < 32) {
			throw new IllegalStateException(
					"app.jwt.secret debe tener al menos 32 bytes (256 bits) para HS256; tiene "
							+ this.secretBytes.length);
		}
		this.expirationMs = expirationMs;
	}

	public String issue(UUID usuarioId, String email, String rol) {
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.subject(email)
					.jwtID(UUID.randomUUID().toString())
					.claim("usuarioId", usuarioId.toString())
					.claim("roles", List.of(rol))
					.issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusMillis(expirationMs)))
					.build();
			SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			signedJwt.sign(new MACSigner(secretBytes));
			return signedJwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("no se pudo firmar el JWT", e);
		}
	}

	public long getExpirationMs() {
		return expirationMs;
	}
}
