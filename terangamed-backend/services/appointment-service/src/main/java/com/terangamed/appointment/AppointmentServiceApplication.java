package com.terangamed.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Microservice de gestion des rendez-vous TerangaMed.
 *
 * <p>{@link EnableFeignClients} active les clients déclaratifs Feign vers
 * patient-service et doctor-service (cf. {@code feign.clients/} package).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.terangamed.appointment.feign")
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
