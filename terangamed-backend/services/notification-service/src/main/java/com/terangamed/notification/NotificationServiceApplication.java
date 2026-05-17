package com.terangamed.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Microservice de notifications TerangaMed.
 *
 * <p>Consume les events Kafka émis par les autres services et les persiste
 * dans une table d'audit avec idempotence par event-id. Préparation pour V2 :
 * envoi email/SMS via providers externes (Mailtrap, Twilio).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
