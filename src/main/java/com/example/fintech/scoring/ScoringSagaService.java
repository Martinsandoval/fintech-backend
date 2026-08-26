package com.example.fintech.scoring;

import com.example.fintech.idempotencia.IdempotencyKeyService;
import com.example.fintech.solicitud.EstadoSolicitud;
import com.example.fintech.solicitud.SolicitudCredito;
import com.example.fintech.solicitud.SolicitudCreditoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Orquesta la saga de scoring crediticio. Ver
 * feature-specs/2-implementar-sagas.md secciones 1 y 5.
 */
@Service
public class ScoringSagaService {

	static final String OPERACION = "scoring_nosis";

	private static final Logger log = LoggerFactory.getLogger(ScoringSagaService.class);

	private final SolicitudCreditoService solicitudCreditoService;
	private final IdempotencyKeyService idempotencyKeyService;
	private final ApplicationEventPublisher eventPublisher;
	private final NosisScoringClient nosisScoringClient;
	private final ScoringResultProcessor scoringResultProcessor;

	public ScoringSagaService(SolicitudCreditoService solicitudCreditoService,
			IdempotencyKeyService idempotencyKeyService, ApplicationEventPublisher eventPublisher,
			NosisScoringClient nosisScoringClient, ScoringResultProcessor scoringResultProcessor) {
		this.solicitudCreditoService = solicitudCreditoService;
		this.idempotencyKeyService = idempotencyKeyService;
		this.eventPublisher = eventPublisher;
		this.nosisScoringClient = nosisScoringClient;
		this.scoringResultProcessor = scoringResultProcessor;
	}

	/**
	 * Mueve la solicitud a EN_EVALUACION y publica el evento que dispara el
	 * scoring. Idempotente: si ya se disparó para esta solicitud (misma key
	 * en idempotency_keys), no dispara de nuevo.
	 */
	@Transactional
	public SolicitudCredito iniciarEvaluacion(UUID solicitudId) {
		SolicitudCredito solicitud = solicitudCreditoService.findById(solicitudId);
		if (solicitud.getEstado() != EstadoSolicitud.INICIADA) {
			throw new IllegalArgumentException(
					"la solicitud debe estar INICIADA para arrancar la evaluación (está " + solicitud.getEstado()
							+ ")");
		}
		if (!idempotencyKeyService.reservar(OPERACION, solicitudId.toString())) {
			return solicitud;
		}

		SolicitudCredito enEvaluacion = solicitudCreditoService.actualizarEstado(solicitudId,
				EstadoSolicitud.EN_EVALUACION);
		eventPublisher.publishEvent(new ScoringRequestedEvent(solicitudId, enEvaluacion.getCliente().getId(),
				enEvaluacion.getCliente().getCuit()));
		return enEvaluacion;
	}

	@Async("sagaTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onScoringRequested(ScoringRequestedEvent event) {
		dispatch(event.solicitudId(), event.cuit());
	}

	/**
	 * Llama al proveedor externo y delega el procesamiento del resultado.
	 * Público para que el job de reconciliación pueda volver a dispararlo
	 * sobre una solicitud que quedó esperando — es el mismo camino, no hay
	 * lógica de reintento separada.
	 */
	public void dispatch(UUID solicitudId, String cuit) {
		long inicio = System.currentTimeMillis();
		try {
			ScoreResponse respuesta = nosisScoringClient.consultarScore(cuit);
			long duracionMs = System.currentTimeMillis() - inicio;
			scoringResultProcessor.procesarExito(solicitudId, cuit, respuesta, duracionMs);
		} catch (Exception e) {
			long duracionMs = System.currentTimeMillis() - inicio;
			log.warn("scoring Nosis falló para solicitud {} (cuit {}): {}", solicitudId, cuit, e.getMessage());
			scoringResultProcessor.procesarFallo(solicitudId, cuit, e, duracionMs);
		}
	}
}
