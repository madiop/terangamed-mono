package com.terangamed.notification.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerangaMed — Notification Service API")
                        .version("1.0.0")
                        .description("""
                                Microservice de notifications.

                                Consume les events Kafka des autres services et expose un endpoint
                                d'audit pour ADMIN. V2 : envoi email/SMS via Mailtrap/Twilio.

                                ## Authentification
                                1. `./scripts/get-token.sh admin`
                                2. Authorize ci-dessus → coller (Cmd-V) → Authorize.

                                ⚠️ Tous les endpoints de ce service exigent le rôle ADMIN.
                                """))
                .components(new Components().addSecuritySchemes(SCHEME_BEARER, bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_BEARER));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        JWT Keycloak — récupérer via `./scripts/get-token.sh admin`
                        puis coller ici (sans préfixe Bearer).
                        """);
    }
}
