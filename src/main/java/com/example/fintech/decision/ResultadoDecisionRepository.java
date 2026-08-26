package com.example.fintech.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResultadoDecisionRepository extends JpaRepository<ResultadoDecision, UUID> {

	List<ResultadoDecision> findBySolicitudId(UUID solicitudId);
}
