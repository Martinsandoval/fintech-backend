package com.example.fintech.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita por IP los dos únicos endpoints públicos de la API
 * (permitAll en SecurityConfig) — sin esto, login/registro no tienen
 * ninguna fricción contra fuerza bruta. Ver
 * feature-specs/7-rate-limiting.md.
 *
 * A propósito NO es un @Component/@Bean: si lo fuera, Spring Boot lo
 * auto-registraría también como filtro de servlet global (mismo problema
 * que evita TokenNoRevocadoValidator no siendo bean) y correría dos
 * veces por request — una vía esa auto-registración y otra vía
 * addFilterBefore en SecurityConfig. Se instancia directo ahí.
 *
 * La ventana es fija por IP (arranca en el primer intento y no se
 * reinicia con cada request dentro de la ventana), no deslizante — así
 * es como funciona expireAfterWrite de Caffeine cuando el valor cacheado
 * se muta in-place en vez de volver a escribirse.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> RUTAS_LIMITADAS = Set.of("/api/auth/login", "/api/auth/register");

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Cache<String, AtomicInteger> intentosPorIp;
	private final int maxIntentos;
	private final long ventanaSegundos;

	public LoginRateLimitFilter(int maxIntentos, long ventanaSegundos) {
		this.maxIntentos = maxIntentos;
		this.ventanaSegundos = ventanaSegundos;
		this.intentosPorIp = Caffeine.newBuilder()
				.expireAfterWrite(ventanaSegundos, TimeUnit.SECONDS)
				.maximumSize(50_000)
				.build();
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equalsIgnoreCase(request.getMethod()) && RUTAS_LIMITADAS.contains(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String ip = request.getRemoteAddr();
		int intentos = intentosPorIp.get(ip, key -> new AtomicInteger(0)).incrementAndGet();

		if (intentos > maxIntentos) {
			rechazar(response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void rechazar(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(ventanaSegundos));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), Map.of(
				"timestamp", OffsetDateTime.now().toString(),
				"status", HttpStatus.TOO_MANY_REQUESTS.value(),
				"error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
				"message", "demasiados intentos desde esta IP, esperá antes de volver a intentar"
		));
	}
}
