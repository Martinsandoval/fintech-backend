package com.example.fintech.scoring;

import com.example.fintech.idempotencia.IdempotencyKeyEntry;
import com.example.fintech.idempotencia.IdempotencyKeyService;
import com.example.fintech.solicitud.SolicitudCreditoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ver feature-specs/2-implementar-sagas.md sección 4: no confiamos en que
 * la respuesta de Nosis siempre vuelva. Esto es lo que rescata una solicitud
 * que quedó en EN_EVALUACION sin resolverse.
 */
@Component
public class ScoringReconciliationJob {

	private static final Logger log = LoggerFactory.getLogger(ScoringReconciliationJob.class);

	private final IdempotencyKeyService idempotencyKeyService;
	private final SolicitudCreditoService solicitudCreditoService;
	private final ScoringSagaService scoringSagaService;
	private final long umbralSegundos;

	public ScoringReconciliationJob(IdempotencyKeyService idempotencyKeyService,
			SolicitudCreditoService solicitudCreditoService, ScoringSagaService scoringSagaService,
			@Value("${app.scoring.reconciliacion.umbral-segundos:300}") long umbralSegundos) {
		this.idempotencyKeyService = idempotencyKeyService;
		this.solicitudCreditoService = solicitudCreditoService;
		this.scoringSagaService = scoringSagaService;
		this.umbralSegundos = umbralSegundos;
	}

	@Scheduled(fixedDelayString = "${app.scoring.reconciliacion.intervalo-ms:60000}")
	public void reconciliar() {
		OffsetDateTime antesDe = OffsetDateTime.now().minusSeconds(umbralSegundos);
		List<IdempotencyKeyEntry> pendientes = idempotencyKeyService.findPendientesAntiguos(
				ScoringSagaService.OPERACION, antesDe);

		for (IdempotencyKeyEntry entry : pendientes) {
			UUID solicitudId = UUID.fromString(entry.getKey());
			log.warn("scoring Nosis para solicitud {} sigue sin resolverse después de {}s, reintentando",
					solicitudId, umbralSegundos);
			try {
				String cuit = solicitudCreditoService.obtenerCuitCliente(solicitudId);
				scoringSagaService.dispatch(solicitudId, cuit);
			} catch (RuntimeException e) {
				log.error("no se pudo reintentar el scoring para la solicitud {}", solicitudId, e);
			}
		}
	}
}
