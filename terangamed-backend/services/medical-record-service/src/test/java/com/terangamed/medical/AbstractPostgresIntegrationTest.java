package com.terangamed.medical;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container PostgreSQL singleton-JVM partagé entre tous les tests d'intégration.
 * Pattern identique à doctor-service / patient-service.
 *
 * <p>L'image {@code postgres:16-alpine} supporte nativement les types JSONB
 * utilisés par {@code consultations.vital_signs}.
 */
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("medical_record_db_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "tc-postgres-shutdown"));
    }
}
