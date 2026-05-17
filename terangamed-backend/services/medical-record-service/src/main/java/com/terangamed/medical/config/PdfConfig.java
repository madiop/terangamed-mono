package com.terangamed.medical.config;

import com.terangamed.medical.pdf.ClinicHeaderProperties;
import com.terangamed.medical.pdf.PdfStorageProperties;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour la génération PDF des ordonnances.
 *
 * <p>Enregistre les {@link PdfStorageProperties} + {@link ClinicHeaderProperties}
 * et expose le bean {@link MinioClient} utilisé par
 * {@code com.terangamed.medical.pdf.MinioPdfStorage}.
 *
 * <p>Pattern projet : pas d'auto-scan des {@code @ConfigurationProperties}
 * (cf. autres services TerangaMed), enregistrement explicite ici → traçabilité
 * complète des sources de configuration.
 *
 * <p>Les beans Thymeleaf (SpringTemplateEngine standalone) et le renderer PDF
 * seront ajoutés à l'étape 5.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({
        PdfStorageProperties.class,
        ClinicHeaderProperties.class
})
public class PdfConfig {

    /**
     * Bean MinioClient unique pour toute l'application. {@code minio-java} gère
     * son propre pool de connexions HTTP (OkHttp), donc une seule instance suffit
     * et est thread-safe.
     *
     * <p>La région est passée pour que le client signe correctement les requêtes
     * en signature v4 — sans elle, MinIO ne valide pas mais AWS S3 (si on bascule)
     * rejette les requêtes avec {@code AuthorizationHeaderMalformed}.
     */
    @Bean
    public MinioClient minioClient(PdfStorageProperties props) {
        log.info("Initialisation MinioClient → endpoint={}, bucket={}, region={}",
                props.getEndpoint(), props.getBucket(), props.getRegion());
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .region(props.getRegion())
                .build();
    }
}
