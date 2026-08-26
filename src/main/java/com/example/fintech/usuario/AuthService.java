package com.example.fintech.usuario;

import com.example.fintech.security.JwtIssuer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El registro público siempre crea el usuario con rol ANALISTA — nunca
 * toma un "rol" del request, para que nadie pueda auto-otorgarse ADMIN
 * llamando a /api/auth/register. Promover a alguien a ADMIN necesita un
 * camino aparte (no existe todavía).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtIssuer jwtIssuer;

	public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtIssuer = jwtIssuer;
	}

	@Transactional
	public AuthResponse registrar(String email, String nombre, String apellido, String rawPassword) {
		String emailNormalizado = email.toLowerCase();
		if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
			throw new IllegalArgumentException("ya existe un usuario con email " + emailNormalizado);
		}

		Usuario usuario = new Usuario();
		usuario.setEmail(emailNormalizado);
		usuario.setNombre(nombre);
		usuario.setApellido(apellido);
		usuario.setPasswordHash(passwordEncoder.encode(rawPassword));
		usuario.setRol(RolUsuario.ANALISTA);
		usuario = usuarioRepository.save(usuario);

		return toAuthResponse(usuario);
	}

	public AuthResponse login(String email, String rawPassword) {
		Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email.toLowerCase())
				.orElseThrow(() -> new BadCredentialsException("credenciales inválidas"));

		if (!usuario.isActivo() || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())) {
			throw new BadCredentialsException("credenciales inválidas");
		}

		return toAuthResponse(usuario);
	}

	private AuthResponse toAuthResponse(Usuario usuario) {
		String token = jwtIssuer.issue(usuario.getId(), usuario.getEmail(), usuario.getRol().name());
		return new AuthResponse(token, "Bearer", jwtIssuer.getExpirationMs(), UsuarioResponse.from(usuario));
	}
}
