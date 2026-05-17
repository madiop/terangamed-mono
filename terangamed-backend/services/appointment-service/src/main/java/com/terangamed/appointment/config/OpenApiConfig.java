package com.terangamed.appointment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration OpenAPI / Swagger UI — un seul schéma : <b>{@code bearer-auth}</b>.
 *
 * <p>Le password flow OAuth2 est cassé sur Swagger UI 5.13 (bundlé springdoc 2.5.0)
 * — voir Javadoc complet sur {@code patient-service/OpenApiConfig}. On utilise
 * uniquement bearer-auth, alimenté par {@code ./scripts/get-token.sh}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_BEARER = "bearer-auth";

    @Bean
    public OpenAPI appointmentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerangaMed — Appointment Service API")
                        .version("1.0.0")
                        .description("""
                                Microservice de gestion des rendez-vous médicaux.

                                ## Authentification
                                1. `./scripts/get-token.sh reception` (ou dr.martin / admin)
                                   Le token est copié dans le clipboard.
                                2. Authorize ci-dessus → coller (Cmd-V) → Authorize.
                                """)
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("Direct (dev)"),
                        new Server().url("http://localhost:8080").description("Via API Gateway")))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_BEARER, bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_BEARER));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        JWT Keycloak — récupérer via `./scripts/get-token.sh <user>`
                        puis coller ici (sans préfixe Bearer).
                        """);
    }
}
