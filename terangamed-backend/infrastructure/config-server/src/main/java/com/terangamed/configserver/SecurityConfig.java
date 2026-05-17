package com.terangamed.configserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du Config Server.
 *
 * <p>Stratégie : HTTP Basic obligatoire pour servir les configurations,
 * avec endpoint {@code /actuator/health} ouvert pour permettre les
 * healthchecks Docker / Kubernetes sans credentials.
 *
 * <p>Les credentials sont gérés par Spring Boot via {@code spring.security.user.name}
 * et {@code spring.security.user.password} (cf. application.yml).
 *
 * <p>CSRF désactivé : le Config Server est appelé en machine-to-machine,
 * pas par des navigateurs avec cookies.
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
