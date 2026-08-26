package com.example.fintech.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sin esto, Swagger UI no muestra el botón "Authorize" y try-it-out
 * (springdoc.swagger-ui.try-it-out-enabled=true) no puede probar ningún
 * endpoint protegido — que es casi todos, ver SecurityConfig.
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI fintechOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Fintech API")
						.description("Préstamos, cheques/factoring, cuenta corriente y contabilidad.")
						.version("v0.1"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components()
						.addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
