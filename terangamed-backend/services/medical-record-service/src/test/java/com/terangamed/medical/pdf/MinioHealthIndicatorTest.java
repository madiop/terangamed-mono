package com.terangamed.medical.pdf;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioHealthIndicatorTest {

    @Mock
    private MinioClient minioClient;

    private PdfStorageProperties properties;
    private MinioHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        properties = new PdfStorageProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("k");
        properties.setSecretKey("s");
        properties.setBucket("test-bucket");
        properties.setRegion("us-east-1");
        indicator = new MinioHealthIndicator(minioClient, properties);
    }

    @Test
    @DisplayName("Bucket présent → UP avec détails")
    void bucketPresent_isUp() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("bucket", "test-bucket")
                .containsEntry("bucketExists", true)
                .containsEntry("endpoint", "http://localhost:9000");
    }

    @Test
    @DisplayName("Bucket absent → DOWN avec reason explicite")
    void bucketMissing_isDown() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("bucketExists", false)
                .containsKey("reason");
    }

    @Test
    @DisplayName("MinIO inaccessible (exception SDK) → DOWN avec erreur")
    void sdkException_isDown() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
