package com.terangamed.apigateway.config;

import com.terangamed.common.security.JwtAuthConverter;
import com.terangamed.common.security.JwtAuthConverterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Sécurité reactive du Gateway — Resource Server JWT Keycloak.
 *
 * <p><b>Endpoints publics</b> :
 * <ul>
 *   <li>{@code /actuator/health/**} et {@code /actuator/info}</li>
 *   <li>{@code /fallback/**} — fallbacks Resilience4j</li>
 *   <li>{@code /v3/api-docs/**} et {@code /swagger-ui/**} — documentation OpenAPI agrégée</li>
 *   <li>Toutes les requêtes {@code OPTIONS} (preflight CORS)</li>
 * </ul>
 *
 * <p><b>Endpoints sécurisés</b> : tout le reste, et notamment {@code /api/**} qui exige
 * un JWT valide. Les rôles métier (ADMIN/DOCTOR/RECEPTIONIST) sont vérifiés <i>côté
 * microservices downstream</i>, pas au niveau du Gateway, pour garder les règles
 * d'autorisation au plus près de la donnée.
 *
 * <p><b>Réutilisation de {@link JwtAuthConverter}</b> de {@code common-lib} via
 * {@link ReactiveJwtAuthenticationConverterAdapter} — un seul code d'extraction
 * de rôles Keycloak, partagé entre stack servlet (services métier) et reactive (Gateway).
 *
 * <p><b>CSRF désactivé</b> — pas pertinent pour une API JSON stateless ; le JWT lui-même
 * est non-cookie (Bearer header), donc immunisé contre CSRF par construction.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/fallback/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverterAdapter jwtConverter) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .build();
    }

    /**
     * Adapte le {@link JwtAuthConverter} servlet (commun à tous les services)
     * en converter reactive utilisable par {@code ServerHttpSecurity}.
     */
    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter(
            JwtAuthConverterProperties properties) {
        return new ReactiveJwtAuthenticationConverterAdapter(new JwtAuthConverter(properties));
    }
}
