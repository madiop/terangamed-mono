package com.terangamed.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservice de gestion des patients TerangaMed.
 *
 * <p><b>Annotations</b> :
 * <ul>
 *   <li>{@link SpringBootApplication} — bootstrap Spring Boot (composante scan, autoconfig)</li>
 *   <li>{@link EnableDiscoveryClient} — enregistre le service auprès d'Eureka
 *       sous le nom {@code patient-service}</li>
 * </ul>
 *
 * <p><b>Pourquoi {@code @EnableJpaAuditing} n'est PAS ici</b> :
 * placé sur cette classe principale, il serait traité par TOUS les slices de test
 * (incluant {@code @WebMvcTest} qui ne charge pas JPA → erreur sur
 * {@code jpaMappingContext}). Il est isolé dans
 * {@link com.terangamed.patient.config.JpaConfig} qui n'est chargée que
 * dans les contextes JPA réels.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
