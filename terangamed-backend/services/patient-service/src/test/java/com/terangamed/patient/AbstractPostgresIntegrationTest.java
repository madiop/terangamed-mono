package com.terangamed.patient;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base pour tests d'intégration nécessitant une vraie PostgreSQL via Testcontainers.
 *
 * <p><b>Pattern utilisé</b> : container <b>singleton JVM-scoped</b> + {@link ServiceConnection}.
 * <ul>
 *   <li>Démarrage UNE fois au premier accès à la classe (static block)</li>
 *   <li>Vit pour toute la durée de la JVM (de Maven Surefire)</li>
 *   <li>Partagé entre toutes les classes de test qui héritent de cette classe —
 *       même port, même URL JDBC pour tous, donc le cache de contexte Spring
 *       (qui mutualise les contextes identiques entre classes de test) reste cohérent</li>
 *   <li>Shutdown hook arrête le container à la fin de la JVM</li>
 *   <li>{@link ServiceConnection} : Spring Boot wire automatiquement le container
 *       comme {@code DataSource} (Spring Boot 3.1+)</li>
 * </ul>
 *
 * <p><b>Pourquoi pas {@code @Testcontainers + @Container}</b> ?<br>
 * L'extension JUnit gère le cycle <i>par classe</i> : start au {@code @BeforeAll},
 * stop au {@code @AfterAll}. Entre 2 classes de test partageant cette base, le
 * container est redémarré sur un port différent → le contexte Spring caché par
 * la 1ère classe garde l'URL ancienne → "Connection refused" sur la 2ème classe.
 * Le pattern singleton-JVM évite ce piège.
 *
 * <p><b>Pré-requis</b> : Docker Desktop (ou équivalent) doit tourner.
 */
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource") // arrêté par le shutdown hook
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("patient_db_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "tc-postgres-shutdown"));
    }
}
