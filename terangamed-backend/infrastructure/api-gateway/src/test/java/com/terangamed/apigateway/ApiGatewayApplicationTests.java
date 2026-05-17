package com.terangamed.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test : démarre le contexte complet du Gateway (Netty + WebFlux + Security)
 * sur un port aléatoire et vérifie que :
 * <ul>
 *   <li>le {@link SecurityWebFilterChain} reactive est registré</li>
 *   <li>le {@link RouteLocator} a parsé au moins une route depuis le YAML</li>
 * </ul>
 *
 * <p><b>Pourquoi {@code RANDOM_PORT} et non {@code NONE} ?</b><br>
 * Spring Cloud Gateway et {@code @EnableWebFluxSecurity} sont conditionnels sur la
 * présence d'un contexte reactive. Avec {@code WebEnvironment.NONE}, ni le
 * {@code RouteLocator} ni le {@code SecurityWebFilterChain} ne sont instanciés —
 * les autowires échouent. Le démarrage Netty est inévitable pour ces tests
 * d'intégration.
 *
 * <p>Le profil {@code test} fournit un {@code jwk-set-uri} factice (port 65535
 * inaccessible) ; le {@code NimbusReactiveJwtDecoder} étant lazy, aucun appel
 * réseau n'a lieu au démarrage.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @Autowired
    SecurityWebFilterChain securityWebFilterChain;

    @Autowired
    RouteLocator routeLocator;

    @Test
    void context_loads() {
        assertThat(securityWebFilterChain).isNotNull();
        assertThat(routeLocator).isNotNull();
    }

    @Test
    void should_load_at_least_one_route_from_yaml() {
        Long count = routeLocator.getRoutes().count().block();

        assertThat(count)
                .as("Le YAML déclare 6 routes services + d'éventuelles routes Swagger")
                .isNotNull()
                .isGreaterThanOrEqualTo(1);
    }
}
