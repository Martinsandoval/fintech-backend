package com.example.fintech.scoring;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.decision.ResultadoDecision;
import com.example.fintech.decision.ResultadoDecisionRepository;
import com.example.fintech.decision.ResultadoDecisionTipo;
import com.example.fintech.integracion.IntegracionLogService;
import com.example.fintech.outbox.OutboxEventService;
import com.example.fintech.solicitud.EstadoSolicitud;
import com.example.fintech.solicitud.SolicitudCredito;
import com.example.fintech.solicitud.SolicitudCreditoService;
import com.example.fintech.idempotencia.IdempotencyKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Bean separado de ScoringSagaService a propósito: dispatch() en
 * ScoringSagaService llama estos métodos como bean distinto para que el
 * proxy de @Transactional se aplique (llamar un método @Transactional desde
 * otro método del mismo bean no pasa por el proxy — mismo problema que
 * OutboxEventPublisher/OutboxEventService en feature-specs/1-consistencia-datos.md).
 */
@Service
public class ScoringResultProcessor {

	private static final int UMBRAL_APROBADO = 700;
	private static final int UMBRAL_RECHAZADO = 400;

	private final ResultadoScoringRepository resultadoScoringRepository;
	private final ResultadoDecisionRepository resultadoDecisionRepository;
	private final SolicitudCreditoService solicitudCreditoService;
	private final IntegracionLogService integracionLogService;
	private final IdempotencyKeyService idempotencyKeyService;
	private final OutboxEventService outboxEventService;

	public ScoringResultProcessor(ResultadoScoringRepository resultadoScoringRepository,
			ResultadoDecisionRepository resultadoDecisionRepository, SolicitudCreditoService solicitudCreditoService,
			IntegracionLogService integracionLogService, IdempotencyKeyService idempotencyKeyService,
			OutboxEventService outboxEventService) {
		this.resultadoScoringRepository = resultadoScoringRepository;
		this.resultadoDecisionRepository = resultadoDecisionRepository;
		this.solicitudCreditoService = solicitudCreditoService;
		this.integracionLogService = integracionLogService;
		this.idempotencyKeyService = idempotencyKeyService;
		this.outboxEventService = outboxEventService;
	}

	@Transactional
	public void procesarExito(UUID solicitudId, String cuit, ScoreResponse respuesta, long duracionMs) {
		integracionLogService.registrar("NOSIS", "/scoring/" + cuit, Map.of("cuit", cuit), respuesta.respuestaRaw(),
				200, true, duracionMs);

		SolicitudCredito solicitud = solicitudCreditoService.findById(solicitudId);
		Cliente cliente = solicitud.getCliente();

		ResultadoScoring resultadoScoring = new ResultadoScoring();
		resultadoScoring.setSolicitud(solicitud);
		resultadoScoring.setCliente(cliente);
		resultadoScoring.setProveedor(ProveedorScoring.NOSIS);
		resultadoScoring.setScore(respuesta.score());
		resultadoScoring.setRespuestaRaw(respuesta.respuestaRaw());
		resultadoScoringRepository.save(resultadoScoring);

		ResultadoDecisionTipo decision = decidir(respuesta.score());
		ResultadoDecision resultadoDecision = new ResultadoDecision();
		resultadoDecision.setSolicitud(solicitud);
		resultadoDecision.setResultado(decision);
		resultadoDecision.setDetalle(Map.of(
				"score", respuesta.score(),
				"umbralAprobado", UMBRAL_APROBADO,
				"umbralRechazado", UMBRAL_RECHAZADO));
		resultadoDecisionRepository.save(resultadoDecision);

		EstadoSolicitud nuevoEstado = switch (decision) {
			case APROBADO -> EstadoSolicitud.APROBADA;
			case RECHAZADO -> EstadoSolicitud.RECHAZADA;
			case DERIVADO_MANUAL -> EstadoSolicitud.REVISION_MANUAL;
		};
		solicitudCreditoService.actualizarEstado(solicitudId, nuevoEstado);

		idempotencyKeyService.completar(ScoringSagaService.OPERACION, solicitudId.toString(),
				Map.of("score", respuesta.score(), "decision", decision.toString()));

		outboxEventService.publicarEvento("SOLICITUD_CREDITO", solicitudId, "SOLICITUD_RESUELTA", Map.of(
				"solicitudId", solicitudId.toString(),
				"clienteId", cliente.getId().toString(),
				"score", respuesta.score(),
				"decision", decision.toString(),
				"estado", nuevoEstado.toString()));
	}

	@Transactional
	public void procesarFallo(UUID solicitudId, String cuit, Throwable error, long duracionMs) {
		integracionLogService.registrar("NOSIS", "/scoring/" + cuit, Map.of("cuit", cuit),
				Map.of("error", String.valueOf(error.getMessage())), null, false, duracionMs);
		// La solicitud queda en EN_EVALUACION y la idempotency key sin
		// completar a propósito: el job de reconciliación la va a
		// re-disparar. No hay retry inmediato acá.
	}

	private ResultadoDecisionTipo decidir(int score) {
		if (score >= UMBRAL_APROBADO) {
			return ResultadoDecisionTipo.APROBADO;
		}
		if (score < UMBRAL_RECHAZADO) {
			return ResultadoDecisionTipo.RECHAZADO;
		}
		return ResultadoDecisionTipo.DERIVADO_MANUAL;
	}
}
