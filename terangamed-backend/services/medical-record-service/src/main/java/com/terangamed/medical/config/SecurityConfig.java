package com.terangamed.medical.config;

import com.terangamed.common.security.JwtAuthConverter;
import com.terangamed.common.security.JwtAuthConverterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration sécurité du medical-record-service.
 *
 * <p>Stratégie identique à patient-service / doctor-service :
 * <ul>
 *   <li>Resource Server JWT (Keycloak) — issuer-uri en config-server</li>
 *   <li>{@link JwtAuthConverter} mappe les realm_access.roles → ROLE_* Spring</li>
 *   <li>Pas de CSRF (API stateless), pas de session</li>
 *   <li>Endpoints publics : actuator/health, OpenAPI, Swagger UI</li>
 * </ul>
 *
 * <p>Le détail des autorisations par endpoint est exprimé via {@code @PreAuthorize}
 * dans les controllers (méthod security activée par {@code @EnableMethodSecurity}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthConverter jwtAuthConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .build();
    }

    @Bean
    public JwtAuthConverter jwtAuthConverter(JwtAuthConverterProperties properties) {
        return new JwtAuthConverter(properties);
    }
}
