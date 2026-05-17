package com.terangamed.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Registry — Netflix Eureka standalone (single-node).
 *
 * <p>Tous les microservices TerangaMed s'enregistrent ici à leur démarrage et
 * découvrent leurs pairs (par exemple : {@code appointment-service} appelle
 * {@code patient-service} via le nom logique {@code patient-service} résolu
 * par Eureka, jamais via une URL en dur).
 *
 * <p><b>Auto-registration désactivée</b> : le registry ne s'inscrit pas lui-même
 * comme client (pas de cluster Eureka pour cette première version).
 *
 * <p><b>Sécurité</b> : HTTP Basic obligatoire pour les endpoints {@code /eureka/**}
 * et le dashboard. {@code /actuator/health} reste public pour les healthchecks.
 *
 * <p>Dashboard : <a href="http://localhost:8761">http://localhost:8761</a>
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
