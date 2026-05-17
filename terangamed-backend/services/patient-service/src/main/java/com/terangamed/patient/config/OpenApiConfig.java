package com.terangamed.patient.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
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
 * <h3>Pourquoi pas le password flow OAuth2 ?</h3>
 * <p>Springdoc-openapi 2.5.0 embarque Swagger UI 5.13.x dont le password flow
 * est cassé en pratique : le bouton Authorize affiche "Authorized" mais le
 * token n'est jamais attaché aux requêtes — résultat : 401 sur tout. Bug
 * client-side reproductible quel que soit le serveur Resource Server.
 *
 * <p>On contourne en proposant uniquement {@code bearer-auth} : l'utilisateur
 * récupère un JWT via {@code ./scripts/get-token.sh <user>} (qui le copie
 * dans le clipboard) et le colle dans la modale Authorize. Ce mode est
 * invariant aux versions Swagger UI et fonctionne 100% du temps.
 *
 * <p>Quand springdoc/swagger-ui réparera le password flow, on pourra
 * réintroduire un scheme OAuth2. Pour l'instant : KISS.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_BEARER = "bearer-auth";

    @Bean
    public OpenAPI patientServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerangaMed — Patient Service API")
                        .version("1.0.0")
                        .description("""
                                Microservice de gestion des patients du cabinet TerangaMed.

                                ## Authentification
                                1. Récupérer un JWT via le script :
                                ```bash
                                ./scripts/get-token.sh reception   # ou admin / dr.martin
                                ```
                                Le token est copié automatiquement dans le clipboard.

                                2. Cliquer sur **Authorize** ci-dessus → coller le token (Cmd-V) → Authorize.

                                3. Toutes les requêtes suivantes incluront `Authorization: Bearer <jwt>`.

                                Le token expire en 5 min — relancer le script si besoin.
                                """)
                        .contact(new Contact().name("TerangaMed Engineering").email("eng@terangamed.local"))
                        .license(new License().name("Proprietary")))
                // ⚠️ ORDRE IMPORTANT : serveur direct (8081) en PREMIER pour éviter le
                // cross-origin Swagger 8081 → API Gateway 8080 (CORS preflight peut
                // dropper le Bearer). Gateway reste sélectionnable via le dropdown.
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Direct (dev)"),
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
                        puis coller ici (sans préfixe Bearer, Swagger l'ajoute).
                        """);
    }
}
