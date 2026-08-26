package com.example.fintech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor dedicado para las sagas (ver feature-specs/2-implementar-sagas.md):
 * las llamadas a proveedores externos nunca corren en el thread que atendió
 * el request HTTP ni en el pool de scheduling.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "sagaTaskExecutor")
	public Executor sagaTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("saga-");
		executor.initialize();
		return executor;
	}
}
