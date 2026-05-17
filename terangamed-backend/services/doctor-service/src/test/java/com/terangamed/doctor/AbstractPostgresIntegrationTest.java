package com.terangamed.doctor;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container PostgreSQL singleton-JVM partagé entre tous les tests d'intégration.
 * Pattern identique à patient-service (cf. justification dans son AbstractPostgresIntegrationTest).
 */
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("doctor_db_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "tc-postgres-shutdown"));
    }
}
