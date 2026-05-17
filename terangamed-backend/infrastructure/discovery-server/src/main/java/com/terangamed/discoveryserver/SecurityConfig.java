package com.terangamed.discoveryserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du Discovery Server (Eureka).
 *
 * <p><b>CSRF désactivé</b> — obligatoire pour permettre aux clients Eureka
 * d'effectuer les requêtes {@code POST}/{@code PUT}/{@code DELETE}
 * sur {@code /eureka/apps/...} (registration, heartbeat, deregister).
 *
 * <p><b>Endpoints publics</b> :
 * <ul>
 *   <li>{@code /actuator/health/**} — healthchecks Docker / Kubernetes</li>
 *   <li>{@code /actuator/info} — métadonnées non-sensibles</li>
 * </ul>
 *
 * <p>Tout le reste (registry, dashboard, endpoints actuator détaillés)
 * exige une authentification HTTP Basic.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(b -> {})
                .build();
    }
}
