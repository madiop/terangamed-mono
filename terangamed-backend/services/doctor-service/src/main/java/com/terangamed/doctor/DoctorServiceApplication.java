package com.terangamed.doctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservice de gestion des médecins TerangaMed.
 *
 * <p>{@code @EnableJpaAuditing} est isolé dans
 * {@link com.terangamed.doctor.config.JpaConfig} (cf. patient-service pour
 * la justification — éviter le crash {@code jpaMappingContext} en {@code @WebMvcTest}).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class DoctorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoctorServiceApplication.class, args);
    }
}
