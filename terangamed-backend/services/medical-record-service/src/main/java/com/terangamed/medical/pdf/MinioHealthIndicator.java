package com.terangamed.medical.pdf;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * HealthIndicator Spring Boot Actuator pour MinIO.
 *
 * <p>Exposé sur {@code GET /actuator/health/minio} (et agrégé dans
 * {@code /actuator/health}). Permet aux orchestrateurs (Kubernetes, Compose
 * healthcheck dépendant) de détecter une panne MinIO avant qu'un utilisateur
 * tente de générer une ordonnance PDF.
 *
 * <p>Stratégie : check {@code bucketExists} sur le bucket configuré — bien
 * moins coûteux qu'un upload de test, et révèle aussi bien :
 * <ul>
 *   <li>panne réseau (timeout)</li>
 *   <li>credentials invalides (403)</li>
 *   <li>bucket inexistant (alerte ops à voir)</li>
 * </ul>
 *
 * <p>L'indicator est auto-découvert par Actuator car {@code @Component}. Pas
 * besoin d'enregistrement manuel.
 */
@Component("minio")
@RequiredArgsConstructor
public class MinioHealthIndicator extends AbstractHealthIndicator {

    private final MinioClient minioClient;
    private final PdfStorageProperties properties;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(properties.getBucket()).build()
        );
        builder.withDetail("endpoint", properties.getEndpoint())
                .withDetail("bucket", properties.getBucket())
                .withDetail("bucketExists", exists);
        if (exists) {
            builder.up();
        } else {
            // Bucket inexistant → DOWN (pas OUT_OF_SERVICE) car la fonctionnalité
            // PDF est cassée tant que le bucket n'est pas (re)créé.
            builder.down().withDetail("reason", "Bucket configuré absent — création manuelle requise");
        }
    }
}
