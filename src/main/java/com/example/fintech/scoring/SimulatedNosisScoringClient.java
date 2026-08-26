package com.example.fintech.scoring;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * No hay credenciales reales de Nosis en este entorno. Score determinístico
 * por CUIT (mismo CUIT → mismo score) para que sea reproducible. Si
 * app.integraciones.nosis.simular-falla-primer-intento está en true
 * (default en dev), el primer intento por CUIT falla — deja a la solicitud
 * "esperando" para que se vea el camino de reconciliación (ver
 * feature-specs/2-implementar-sagas.md sección 5) en vez de que todo
 * funcione siempre a la primera.
 */
@Component
public class SimulatedNosisScoringClient implements NosisScoringClient {

	private final boolean simularFallaPrimerIntento;
	private final ConcurrentHashMap<String, AtomicInteger> intentosPorCuit = new ConcurrentHashMap<>();

	public SimulatedNosisScoringClient(
			@Value("${app.integraciones.nosis.simular-falla-primer-intento:true}") boolean simularFallaPrimerIntento) {
		this.simularFallaPrimerIntento = simularFallaPrimerIntento;
	}

	@Override
	@CircuitBreaker(name = "nosis", fallbackMethod = "consultarScoreFallback")
	public ScoreResponse consultarScore(String cuit) {
		simularLatenciaDeRed();

		int intento = intentosPorCuit.computeIfAbsent(cuit, c -> new AtomicInteger(0)).incrementAndGet();
		if (simularFallaPrimerIntento && intento == 1) {
			throw new NosisUnavailableException("Nosis no respondió (simulado) para CUIT " + cuit);
		}

		int score = Math.floorMod(cuit.hashCode(), 1000);
		return new ScoreResponse(score, Map.of("cuit", cuit, "score", score, "fuente", "simulado"));
	}

	@SuppressWarnings("unused")
	private ScoreResponse consultarScoreFallback(String cuit, Throwable t) {
		throw new NosisUnavailableException("Nosis no disponible para CUIT " + cuit, t);
	}

	private void simularLatenciaDeRed() {
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
