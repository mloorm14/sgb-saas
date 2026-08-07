package com.uteq.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global de springdoc-openapi (Módulo 11.1 del roadmap). La
 * dependencia y las rutas ({@code /swagger-ui.html}, {@code /api/docs}) ya
 * estaban en {@code pom.xml}/{@code application.yml} desde antes -- Swagger
 * UI ya funcionaba sin esto, solo mostraba los endpoints "en crudo" (sin
 * título, sin botón de autorizar con Bearer). Este bean agrega metadata de
 * la API y el esquema de seguridad "bearer-jwt" para que Swagger UI muestre
 * el botón "Authorize" y adjunte el header {@code Authorization: Bearer
 * <token>} en cada request de prueba, sin tener que pegarlo a mano en cada
 * endpoint.
 *
 * Lo que falta después de esto (Módulo 11.3, fuera de esta rama): anotar
 * cada controller existente con {@code @Tag}/{@code @Operation}/
 * {@code @ApiResponse} para que la lista de endpoints tenga descripciones,
 * no solo el path y el método HTTP.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER_JWT = "bearer-jwt";

    @Bean
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
