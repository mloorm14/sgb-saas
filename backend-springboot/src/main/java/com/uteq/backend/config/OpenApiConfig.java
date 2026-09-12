package com.uteq.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata de la API + esquema bearer-jwt para el botón Authorize de Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER_JWT = "bearer-jwt";

    @Bean
    /**
     * Handles sgb open api.
     *
     * @return open api with the resulting state after the operation
     */
    public OpenAPI sgbOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SGB-SaaS API")
                        .version("v1")
                        .description("API del Sistema de Gestión de Biblioteca (SIGCB-QR)."))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER_JWT));
    }
}
