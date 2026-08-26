package com.example.fintech.scoring;

/**
 * Punto de extensión: cambiar a un cliente HTTP real contra la API de Nosis
 * es una implementación nueva de esta interfaz. Ver
 * feature-specs/2-implementar-sagas.md sección 5.
 */
public interface NosisScoringClient {

	ScoreResponse consultarScore(String cuit);
}
