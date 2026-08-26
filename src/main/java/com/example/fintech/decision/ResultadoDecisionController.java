package com.example.fintech.decision;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sólo lectura: los resultados se crean exclusivamente vía
 * ScoringSagaService.
 */
@RestController
@RequestMapping("/api/resultados-decision")
public class ResultadoDecisionController {

	private final ResultadoDecisionRepository resultadoDecisionRepository;

	public ResultadoDecisionController(ResultadoDecisionRepository resultadoDecisionRepository) {
		this.resultadoDecisionRepository = resultadoDecisionRepository;
	}

	@GetMapping
	public List<ResultadoDecision> findBySolicitud(@RequestParam UUID solicitudId) {
		return resultadoDecisionRepository.findBySolicitudId(solicitudId);
	}
}
