package com.example.fintech.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResultadoScoringRepository extends JpaRepository<ResultadoScoring, UUID> {

	List<ResultadoScoring> findBySolicitudId(UUID solicitudId);
}
