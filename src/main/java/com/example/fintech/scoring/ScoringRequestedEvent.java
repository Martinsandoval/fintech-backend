package com.example.fintech.scoring;

import java.util.UUID;

/**
 * Publicado dentro de la transacción que mueve la solicitud a
 * EN_EVALUACION. El listener sólo dispara la llamada externa si esa
 * transacción confirma (@TransactionalEventListener AFTER_COMMIT) — ver
 * feature-specs/2-implementar-sagas.md sección 1.
 */
public record ScoringRequestedEvent(UUID solicitudId, UUID clienteId, String cuit) {
}
