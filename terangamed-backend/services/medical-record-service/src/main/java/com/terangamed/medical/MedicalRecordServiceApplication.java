package com.terangamed.medical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Microservice de gestion des dossiers médicaux TerangaMed.
 *
 * <p>{@code @EnableJpaAuditing} est isolé dans
 * {@link com.terangamed.medical.config.JpaConfig} — pattern doctor-service /
 * patient-service (éviter le crash {@code jpaMappingContext} en {@code @WebMvcTest}).
 *
 * <p>Feign : appels vers patient-service, doctor-service, appointment-service
 * pour valider les références cross-service. Tous les clients Feign vivent dans
 * {@code com.terangamed.medical.client}.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.terangamed.medical.client")
public class MedicalRecordServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalRecordServiceApplication.class, args);
    }
}
