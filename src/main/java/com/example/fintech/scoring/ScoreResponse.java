package com.example.fintech.scoring;

import java.util.Map;

public record ScoreResponse(Integer score, Map<String, Object> respuestaRaw) {
}
