package com.example.fintech.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * tokens_revocados no necesita crecer para siempre: una vez que expira_en
 * ya pasó, el token sería rechazado por vencido igual, con o sin la fila
 * de revocación. Ver feature-specs/5-revocacion-tokens.md sección 2.
 */
@Component
public class TokenRevocationCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(TokenRevocationCleanupJob.class);

	private final TokenRevocationService tokenRevocationService;

	public TokenRevocationCleanupJob(TokenRevocationService tokenRevocationService) {
		this.tokenRevocationService = tokenRevocationService;
	}

	@Scheduled(fixedDelayString = "${app.security.tokens-revocados.limpieza-intervalo-ms:3600000}")
	public void limpiar() {
		long borrados = tokenRevocationService.purgarExpirados();
		if (borrados > 0) {
			log.info("limpieza de tokens_revocados: {} fila(s) expirada(s) borradas", borrados);
		}
	}
}
