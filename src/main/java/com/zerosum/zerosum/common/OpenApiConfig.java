package com.zerosum.zerosum.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI zerosumOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Zerosum API")
						.description("REST API for managing accounts and recording ledger transactions.")
						.version("v1"));
	}

}
