package com.terangamed.medical.pdf;

/**
 * Exception levée par la couche {@link PdfStorageService} pour signaler tout
 * problème d'accès au stockage objet (MinIO/S3) — réseau, credentials, bucket
 * inaccessible, taille dépassée, etc.
 *
 * <p>Encapsule les exceptions techniques du SDK MinIO ({@code MinioException},
 * {@code IOException}, {@code InvalidKeyException}…) → la couche service métier
 * ne dépend pas directement du SDK et peut catcher une seule exception cohérente.
 *
 * <p>Mappée par {@code GlobalExceptionHandler} (common-lib) en
 * {@code HTTP 503 Service Unavailable} avec errorCode {@code PDF_STORAGE_UNAVAILABLE}.
 */
public class PdfStorageException extends RuntimeException {

    public PdfStorageException(String message) {
        super(message);
    }

    public PdfStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
