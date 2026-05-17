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
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link MinioPdfStorage} — couvre tous les chemins :
 * bootstrap bucket, store, retrieve (hit/miss), existsByKey, mapping d'erreurs.
 *
 * <p>Le client MinIO est mocké : ces tests ne touchent pas le réseau et tournent
 * en quelques ms. Les tests d'intégration end-to-end (avec un vrai serveur MinIO
 * via Testcontainers) sont à l'étape 8.
 */
@ExtendWith(MockitoExtension.class)
class MinioPdfStorageTest {

    @Mock
    private MinioClient minioClient;

    private PdfStorageProperties properties;

    @InjectMocks
    private MinioPdfStorage storage;

    @BeforeEach
    void setUp() {
        properties = new PdfStorageProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-key");
        properties.setSecretKey("test-secret");
        properties.setBucket("test-bucket");
        properties.setAutoCreateBucket(true);
        properties.setRegion("us-east-1");
        storage = new MinioPdfStorage(minioClient, properties);
    }

    // ─────────────────────────── bootstrapBucket ───────────────────────────

    @Nested
    @DisplayName("bootstrapBucket()")
    class BootstrapBucket {

        @Test
        @DisplayName("Bucket déjà présent → aucun makeBucket")
        void bucketExists_noCreation() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

            storage.bootstrapBucket();

            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Bucket absent + autoCreate=true → makeBucket appelé")
        void bucketMissing_autoCreate_createsBucket() throws Exception {
            properties.setAutoCreateBucket(true);
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

            storage.bootstrapBucket();

            verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Bucket absent + autoCreate=false → aucune création, log warn seulement")
        void bucketMissing_noAutoCreate_skipsCreation() throws Exception {
            properties.setAutoCreateBucket(false);
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

            storage.bootstrapBucket();

            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Erreur réseau au démarrage → service ne crash pas (log error)")
        void bootstrapResilientToFailure() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("connection refused"));

            // Ne doit PAS propager — le service doit pouvoir démarrer même si MinIO est temporairement KO
            storage.bootstrapBucket();

            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }
    }

    // ─────────────────────────── store ───────────────────────────

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("Stocke un PDF valide avec métadonnées")
        void storesValidPdf() throws Exception {
            byte[] pdf = "%PDF-1.4 fake".getBytes();
            Map<String, String> meta = Map.of("prescriptionId", "42", "renderedBy", "dr-diop");

            storage.store("ord/2026/ORD-2026-00042/abc.pdf", pdf, meta);

            verify(minioClient).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("Métadonnées null → traité comme map vide, pas de NPE")
        void nullMetadataDefaultsToEmpty() throws Exception {
            byte[] pdf = "%PDF-1.4".getBytes();

            storage.store("key.pdf", pdf, null);

            verify(minioClient).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("PDF null → PdfStorageException avant tout appel SDK")
        void rejectsNullPdf() throws Exception {
            assertThatThrownBy(() -> storage.store("key.pdf", null, Map.of()))
                    .isInstanceOf(PdfStorageException.class)
                    .hasMessageContaining("PDF vide");
            verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("PDF vide (byte[0]) → PdfStorageException")
        void rejectsEmptyPdf() throws Exception {
            assertThatThrownBy(() -> storage.store("key.pdf", new byte[0], Map.of()))
                    .isInstanceOf(PdfStorageException.class);
            verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("Erreur SDK MinIO (IOException) → encapsulée en PdfStorageException")
        void wrapsSdkException() throws Exception {
            // Le code de prod ne catch que les checked exceptions du SDK (MinioException,
            // IOException, NoSuchAlgorithmException, InvalidKeyException). Simuler une
            // RuntimeException ici ne testerait pas le wrapping réel — on utilise IOException.
            doThrow(new java.io.IOException("network down"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            assertThatThrownBy(() -> storage.store("key.pdf", "x".getBytes(), Map.of()))
                    .isInstanceOf(PdfStorageException.class);
        }
    }

    // ─────────────────────────── retrieve ───────────────────────────

    @Nested
    @DisplayName("retrieve()")
    class Retrieve {

        @Test
        @DisplayName("Objet présent → Optional<StoredPdf> avec contenu et métadonnées")
        void returnsStoredPdfWhenPresent() throws Exception {
            GetObjectResponse response = mockGetObjectResponse(1234L, Map.of(
                    "x-amz-meta-prescriptionid", "42",
                    "x-amz-meta-renderedby", "dr-diop"
            ));
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

            Optional<StoredPdf> result = storage.retrieve("key.pdf");

            assertThat(result).isPresent();
            assertThat(result.get().contentType()).isEqualTo("application/pdf");
            assertThat(result.get().contentLength()).isEqualTo(1234L);
            assertThat(result.get().userMetadata())
                    .containsEntry("prescriptionid", "42")
                    .containsEntry("renderedby", "dr-diop");
        }

        @Test
        @DisplayName("NoSuchKey (objet absent) → Optional.empty() (cache miss)")
        void returnsEmptyOnNoSuchKey() throws Exception {
            ErrorResponseException noSuchKey = errorResponseException("NoSuchKey");
            when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(noSuchKey);

            Optional<StoredPdf> result = storage.retrieve("missing.pdf");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Erreur S3 autre que NoSuchKey → PdfStorageException")
        void throwsOnOtherS3Error() throws Exception {
            ErrorResponseException accessDenied = errorResponseException("AccessDenied");
            when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(accessDenied);

            assertThatThrownBy(() -> storage.retrieve("key.pdf"))
                    .isInstanceOf(PdfStorageException.class)
                    .hasMessageContaining("AccessDenied");
        }
    }

    // ─────────────────────────── existsByKey ───────────────────────────

    @Nested
    @DisplayName("existsByKey()")
    class ExistsByKey {

        @Test
        @DisplayName("Objet présent → true")
        void returnsTrueWhenPresent() throws Exception {
            StatObjectResponse stat = org.mockito.Mockito.mock(StatObjectResponse.class);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

            assertThat(storage.existsByKey("key.pdf")).isTrue();
        }

        @Test
        @DisplayName("NoSuchKey → false (pas d'exception)")
        void returnsFalseOnNoSuchKey() throws Exception {
            // Important : construire l'exception AVANT le when(), sinon les
            // lenient().when() internes au helper s'imbriquent dans le when()
            // principal → UnfinishedStubbing en mode strict.
            ErrorResponseException noSuchKey = errorResponseException("NoSuchKey");
            when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(noSuchKey);

            assertThat(storage.existsByKey("missing.pdf")).isFalse();
        }

        @Test
        @DisplayName("Erreur S3 autre → PdfStorageException")
        void throwsOnOtherS3Error() throws Exception {
            ErrorResponseException internalError = errorResponseException("InternalError");
            when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(internalError);

            assertThatThrownBy(() -> storage.existsByKey("key.pdf"))
                    .isInstanceOf(PdfStorageException.class);
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static GetObjectResponse mockGetObjectResponse(long contentLength, Map<String, String> headers) {
        // Construire des Headers OkHttp réels — plus fidèle qu'un mock complet.
        Headers.Builder hb = new Headers.Builder().add("Content-Length", String.valueOf(contentLength));
        headers.forEach(hb::add);
        return new GetObjectResponse(hb.build(), "bucket", "region", "object",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
    }

    /**
     * Construit une {@link ErrorResponseException} mockée avec un code S3 donné.
     * Le constructeur réel exige des objets complexes (Response OkHttp) — on
     * mocke uniquement les accesseurs utilisés par {@link MinioPdfStorage}.
     */
    private static ErrorResponseException errorResponseException(String code) {
        ErrorResponse errorResponse = org.mockito.Mockito.mock(ErrorResponse.class);
        lenient().when(errorResponse.code()).thenReturn(code);
        ErrorResponseException exception = org.mockito.Mockito.mock(ErrorResponseException.class);
        lenient().when(exception.errorResponse()).thenReturn(errorResponse);
        return exception;
    }
}
