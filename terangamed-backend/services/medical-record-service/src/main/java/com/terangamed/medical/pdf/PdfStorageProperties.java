package com.terangamed.medical.pdf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration du stockage MinIO pour les PDFs d'ordonnances.
 *
 * <p>Préfixe : {@code terangamed.pdf.storage.minio.*}
 *
 * <pre>
 * terangamed:
 *   pdf:
 *     storage:
 *       minio:
 *         endpoint: http://minio:9000
 *         access-key: terangamed
 *         secret-key: terangamed-secret-2026
 *         bucket: terangamed-prescriptions
 *         auto-create-bucket: true
 *         region: us-east-1
 * </pre>
 *
 * <p><b>Validation</b> : {@code @Validated} fait planter le démarrage si une
 * propriété critique est manquante ou invalide — pattern fail-fast préférable
 * aux NPE à la première requête PDF.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "terangamed.pdf.storage.minio")
public class PdfStorageProperties {

    /** URL du serveur MinIO. Ex: {@code http://minio:9000} (Docker) ou {@code http://localhost:9000} (local). */
    @NotBlank
    @Pattern(regexp = "^https?://.+", message = "endpoint doit commencer par http:// ou https://")
    private String endpoint;

    /** Access key (équivalent S3 AWS_ACCESS_KEY_ID). */
    @NotBlank
    private String accessKey;

    /** Secret key (équivalent S3 AWS_SECRET_ACCESS_KEY). */
    @NotBlank
    private String secretKey;

    /**
     * Nom du bucket cible. Conventions MinIO/S3 : 3-63 caractères, lowercase,
     * pas d'underscore. Le bucket est créé au démarrage si absent (cf. {@link #autoCreateBucket}).
     */
    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
            message = "bucket doit respecter les conventions S3 : lowercase, 3-63 caractères, sans underscore")
    private String bucket;

    /**
     * Si {@code true}, le bucket est créé au démarrage s'il n'existe pas.
     * À mettre à {@code false} en prod si la création est gérée hors-application
     * (Terraform, Helm) — évite que l'app ait besoin du droit {@code s3:CreateBucket}.
     */
    @NotNull
    private Boolean autoCreateBucket = true;

    /**
     * Région cosmétique — MinIO ne valide pas mais le client minio-java l'utilise
     * pour signer les requêtes (signature v4). Par défaut {@code us-east-1}.
     */
    @NotBlank
    private String region = "us-east-1";
}
