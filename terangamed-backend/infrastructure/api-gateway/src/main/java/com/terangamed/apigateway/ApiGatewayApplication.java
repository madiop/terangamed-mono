package com.terangamed.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Edge service TerangaMed — Spring Cloud Gateway (stack reactive WebFlux).
 *
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Point d'entrée unique pour le frontend Angular ({@code /api/**})</li>
 *   <li>Validation du JWT Keycloak (Resource Server) avant routage</li>
 *   <li>Routage par nom logique via Eureka ({@code lb://service-name})</li>
 *   <li>CORS autorisé pour le frontend Angular ({@code http://localhost:4200} par défaut)</li>
 *   <li>Filtre {@code CorrelationIdFilter} : trace inter-services unifiée</li>
 *   <li>Circuit breaker + fallback contrôlé pour chaque service downstream</li>
 * </ul>
 *
 * <p><b>Routes définies dans {@code application.yml}</b> — éditer ce fichier
 * (et non du code Java) pour ajouter/modifier des routes.
 *
 * <p><b>Note technique</b> : la stack est reactive (Netty + WebFlux),
 * donc le {@code GlobalExceptionHandler} servlet de {@code common-lib} n'est pas activé.
 * Les erreurs de routage et fallbacks utilisent {@link com.terangamed.apigateway.web.FallbackController}.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
