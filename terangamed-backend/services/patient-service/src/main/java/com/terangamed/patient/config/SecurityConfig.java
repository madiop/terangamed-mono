package com.terangamed.patient.config;

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
 * Configuration de sécurité du patient-service (Resource Server JWT Keycloak).
 *
 * <p><b>Endpoints publics</b> :
 * <ul>
 *   <li>{@code /actuator/health/**}, {@code /actuator/info} — sondes K8s/Docker</li>
 *   <li>{@code /v3/api-docs/**}, {@code /swagger-ui/**} — documentation OpenAPI</li>
 * </ul>
 *
 * <p><b>Endpoints protégés</b> : tout le reste exige un JWT valide. Les rôles
 * métier (ADMIN, DOCTOR, RECEPTIONIST) sont vérifiés au niveau méthode via
 * {@code @PreAuthorize} (cf. {@link EnableMethodSecurity}).
 *
 * <p>Le {@link JwtAuthConverter} extrait les rôles {@code realm_access.roles}
 * du JWT Keycloak et les préfixe {@code ROLE_} pour Spring Security.
 *
 * <p><b>CSRF désactivé</b> : API REST stateless, JWT en header Bearer (immune CSRF).
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

    /**
     * Le {@link JwtAuthConverter} de {@code common-lib} a besoin de
     * {@link JwtAuthConverterProperties} (déjà bindé par
     * {@code CommonLibAutoConfiguration}). On l'instancie ici comme bean Spring
     * pour pouvoir l'injecter dans la SecurityFilterChain.
     */
    @Bean
    public JwtAuthConverter jwtAuthConverter(JwtAuthConverterProperties properties) {
        return new JwtAuthConverter(properties);
    }
}
