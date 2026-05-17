package com.terangamed.medical.config;

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
    public OpenAPI medicalRecordServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerangaMed — Medical Record Service API")
                        .version("1.0.0")
                        .description("""
                                Microservice de gestion des dossiers médicaux.

                                Gère :
                                - **Dossier médical** (un par patient)
                                - **Antécédents** (allergies, maladies, chirurgies, traitements, familial)
                                - **Consultations** (avec signes vitaux JSONB + signature terminale)
                                - **Ordonnances** + lignes médicaments

                                ## Sécurité
                                - **DOCTOR** créé/modifie SES propres consultations (vérification programmatique)
                                - **Consultation signée** → immuable (médico-légal)
                                - **PATIENT** lit son propre dossier via matching Keycloak `sub` ↔ patient.keycloakSubject
                                - **ADMIN** : full access sauf modification consultation signée
                                - **Soft-delete** uniquement (traçabilité)

                                ## Authentification
                                1. `./scripts/get-token.sh dr.martin` (ou admin / reception)
                                2. Authorize ci-dessus → coller (Cmd-V) → Authorize.

                                `POST /api/consultations` résout le médecin auteur via le claim
                                `sub` du JWT (lookup Feign vers doctor-service). Le compte Keycloak
                                doit être lié à un Doctor (`keycloak_subject` colonne, V4).
                                """)
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8084").description("Direct (dev)"),
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
