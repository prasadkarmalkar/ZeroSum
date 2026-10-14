package com.zerosum.zerosum.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	/**
	 * Every operation requires a bearer token by default, which enables Swagger UI's Authorize button.
	 * Public operations opt out with an empty {@code @SecurityRequirements}.
	 */
	@Bean
	public OpenAPI zerosumOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Zerosum API")
						.description("REST API for managing accounts and recording ledger transactions.")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Paste an access token (without the \"Bearer \" prefix).")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
