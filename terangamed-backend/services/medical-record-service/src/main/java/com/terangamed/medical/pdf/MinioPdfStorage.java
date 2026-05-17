package com.terangamed.medical.pdf;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implémentation S3-compatible de {@link PdfStorageService} via le client
 * {@code minio-java}.
 *
 * <h3>Cycle de vie</h3>
 * <p>Au démarrage, {@link #bootstrapBucket()} :
 * <ol>
 *   <li>Vérifie l'existence du bucket (HEAD request)</li>
 *   <li>Si absent et {@code autoCreateBucket=true} → le crée</li>
 *   <li>Si absent et {@code autoCreateBucket=false} → log WARN (assume infra externe)</li>
 * </ol>
 *
 * <h3>Gestion des erreurs</h3>
 * <p>Code de réponse S3 {@code NoSuchKey} → {@code Optional.empty()} (cache miss légitime).<br>
 * Tout autre erreur (réseau, credentials, droits) → {@link PdfStorageException}.
 *
 * <h3>Métadonnées</h3>
 * <p>Les métadonnées custom sont préfixées {@code x-amz-meta-} par le SDK. MinIO
 * les retourne en lowercase et sans préfixe — on les normalise au {@code retrieve}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioPdfStorage implements PdfStorageService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    /** Header MinIO préfixe les user-metadata par "x-amz-meta-" en réponse — on filtre. */
    private static final String USER_META_HEADER_PREFIX = "x-amz-meta-";

    private final MinioClient minioClient;
    private final PdfStorageProperties properties;

    /**
     * Vérifie / crée le bucket au démarrage. Idempotent — peut être appelé plusieurs
     * fois sans effet de bord (utile en tests d'intégration qui restart le contexte).
     *
     * <p>Volontairement {@code package-private} pour permettre l'invocation directe
     * dans les tests unitaires sans déclencher le cycle Spring complet.
     */
    @PostConstruct
    void bootstrapBucket() {
        String bucket = properties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (exists) {
                log.info("MinIO bucket '{}' déjà présent — pas de création nécessaire", bucket);
                return;
            }
            if (Boolean.TRUE.equals(properties.getAutoCreateBucket())) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .region(properties.getRegion())
                                .build()
                );
                log.info("MinIO bucket '{}' créé (region={})", bucket, properties.getRegion());
            } else {
                log.warn("MinIO bucket '{}' absent et autoCreateBucket=false — " +
                        "la persistance d'ordonnances échouera tant que le bucket n'existe pas", bucket);
            }
        } catch (Exception e) {
            // Au démarrage on log mais on ne fait pas planter le service : MinIO peut
            // être temporairement KO pendant un rolling restart. La 1re requête PDF
            // remontera l'erreur clairement via PdfStorageException.
            log.error("Échec du bootstrap MinIO (bucket={}) — le service démarre mais " +
                    "les requêtes PDF retourneront 503 tant que MinIO n'est pas accessible",
                    bucket, e);
        }
    }

    // ─────────────────────────── PdfStorageService ───────────────────────────

    @Override
    public void store(String objectKey, byte[] pdfBytes, Map<String, String> userMetadata) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new PdfStorageException("Tentative de stockage d'un PDF vide (key=" + objectKey + ")");
        }
        Map<String, String> safeMeta = userMetadata != null ? userMetadata : Collections.emptyMap();

        try (ByteArrayInputStream stream = new ByteArrayInputStream(pdfBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .stream(stream, pdfBytes.length, -1)
                            .contentType(PDF_CONTENT_TYPE)
                            .userMetadata(safeMeta)
                            .build()
            );
            log.debug("PDF stocké : bucket={}, key={}, size={} bytes",
                    properties.getBucket(), objectKey, pdfBytes.length);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PdfStorageException(
                    "Échec stockage PDF (key=" + objectKey + ")", e);
        }
    }

    @Override
    public Optional<StoredPdf> retrieve(String objectKey) {
        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .build()
            );
            // headers() expose les en-têtes HTTP de la réponse (Content-Length + user-meta).
            long contentLength = parseContentLength(response);
            Map<String, String> userMeta = extractUserMetadata(response);
            log.debug("PDF récupéré : bucket={}, key={}, size={} bytes",
                    properties.getBucket(), objectKey, contentLength);
            return Optional.of(new StoredPdf(response, contentLength, PDF_CONTENT_TYPE, userMeta));
        } catch (ErrorResponseException e) {
            if (isNoSuchKey(e)) {
                log.debug("PDF absent du storage : key={}", objectKey);
                return Optional.empty();
            }
            throw new PdfStorageException(
                    "Erreur S3 récupération PDF (key=" + objectKey + ", code="
                            + e.errorResponse().code() + ")", e);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PdfStorageException(
                    "Échec récupération PDF (key=" + objectKey + ")", e);
        }
    }

    @Override
    public boolean existsByKey(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .build()
            );
            return stat != null;
        } catch (ErrorResponseException e) {
            if (isNoSuchKey(e)) {
                return false;
            }
            throw new PdfStorageException(
                    "Erreur S3 stat PDF (key=" + objectKey + ", code="
                            + e.errorResponse().code() + ")", e);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new PdfStorageException("Échec stat PDF (key=" + objectKey + ")", e);
        }
    }

    // ─────────────────────────── Helpers privés ───────────────────────────

    /** S3 code {@code NoSuchKey} = objet absent (sémantique cache-miss, pas une vraie erreur). */
    private static boolean isNoSuchKey(ErrorResponseException e) {
        return e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code());
    }

    private static long parseContentLength(GetObjectResponse response) {
        String header = response.headers().get("Content-Length");
        if (header == null) {
            return -1L;
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * Extrait les user-metadata depuis les headers HTTP de la réponse MinIO.
     * Le SDK les sérialise en {@code x-amz-meta-<key>} → on retire le préfixe.
     *
     * <p><b>Pourquoi pas {@code headers.forEach(...)}</b> : {@code okhttp3.Headers}
     * n'est pas une {@code Map}, c'est un {@code Iterable<kotlin.Pair<String,String>>}.
     * Le signature {@code BiConsumer<String,String>} n'est pas applicable en Java →
     * on itère via {@link okhttp3.Headers#size()} + {@link okhttp3.Headers#name(int)} /
     * {@link okhttp3.Headers#value(int)}.
     */
    private static Map<String, String> extractUserMetadata(GetObjectResponse response) {
        Map<String, String> meta = new HashMap<>();
        okhttp3.Headers headers = response.headers();
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if (name != null && name.toLowerCase().startsWith(USER_META_HEADER_PREFIX)) {
                meta.put(name.substring(USER_META_HEADER_PREFIX.length()).toLowerCase(), value);
            }
        }
        return Map.copyOf(meta);
    }
}
