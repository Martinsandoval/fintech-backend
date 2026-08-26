package com.example.fintech.scoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sólo lectura: los resultados se crean exclusivamente vía
 * ScoringSagaService al procesar la respuesta del proveedor.
 */
@RestController
@RequestMapping("/api/resultados-scoring")
public class ResultadoScoringController {

	private final ResultadoScoringRepository resultadoScoringRepository;

	public ResultadoScoringController(ResultadoScoringRepository resultadoScoringRepository) {
		this.resultadoScoringRepository = resultadoScoringRepository;
	}

	@GetMapping
	public List<ResultadoScoring> findBySolicitud(@RequestParam UUID solicitudId) {
		return resultadoScoringRepository.findBySolicitudId(solicitudId);
	}
}
