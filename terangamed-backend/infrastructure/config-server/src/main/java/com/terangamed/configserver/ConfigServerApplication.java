package com.terangamed.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server — point d'entrée centralisé pour les configurations
 * de tous les microservices TerangaMed.
 *
 * <p><b>Backend</b> : profil {@code native} (configurations dans
 * {@code classpath:/config-repo/}). Migration vers un backend Git triviale ensuite
 * (changer {@code spring.profiles.active=git} + {@code spring.cloud.config.server.git.uri}).
 *
 * <p><b>Sécurité</b> : authentification HTTP Basic obligatoire pour tous les endpoints
 * sauf {@code /actuator/health}. Identifiants définis via {@code spring.security.user.*}.
 *
 * <p><b>Endpoints</b> :
 * <ul>
 *   <li>{@code GET /{application}/{profile}} — config d'un service pour un profil</li>
 *   <li>{@code GET /{application}/{profile}/{label}} — idem avec label/branch</li>
 *   <li>{@code GET /actuator/health} — health public (Docker, K8s)</li>
 * </ul>
 *
 * <p>Exemple : {@code curl -u config:config http://localhost:8888/patient-service/docker}
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
