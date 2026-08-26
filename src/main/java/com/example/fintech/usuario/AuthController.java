package com.example.fintech.usuario;

import com.example.fintech.security.TokenRevocationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final TokenRevocationService tokenRevocationService;

	public AuthController(AuthService authService, TokenRevocationService tokenRevocationService) {
		this.authService = authService;
		this.tokenRevocationService = tokenRevocationService;
	}

	@SecurityRequirements
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		AuthResponse response = authService.registrar(request.email(), request.nombre(), request.apellido(),
				request.password());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@SecurityRequirements
	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.email(), request.password());
	}

	/**
	 * Requiere el propio token del caller (no está en permitAll) — revoca
	 * exactamente ese token, no "todos los tokens del usuario". Ver
	 * feature-specs/5-revocacion-tokens.md.
	 */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
		UUID jti = UUID.fromString(jwt.getId());
		UUID usuarioId = UUID.fromString(jwt.getClaimAsString("usuarioId"));
		OffsetDateTime expiraEn = OffsetDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC);
		tokenRevocationService.revocar(jti, usuarioId, expiraEn);
		return ResponseEntity.noContent().build();
	}

	public record RegisterRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(max = 100) String nombre,
			@NotBlank @Size(max = 100) String apellido,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}
}
