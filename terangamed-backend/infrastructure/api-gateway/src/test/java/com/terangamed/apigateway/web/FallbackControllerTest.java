package com.terangamed.apigateway.web;

import com.terangamed.apigateway.filter.CorrelationIdFilter;
import com.terangamed.common.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire du contrôleur de fallback en isolation totale.
 *
 * <p><b>Pourquoi {@link WebTestClient#bindToController}</b> et non {@code @WebFluxTest} ?<br>
 * On évite tout chargement de contexte Spring : pas d'autoconfig sécurité reactive
 * (qui voudrait un {@code ReactiveJwtDecoder}), pas de slice WebFlux, pas
 * d'interaction avec Spring Cloud Gateway. Le test est rapide, déterministe, et
 * ne dépend que du contrôleur instancié manuellement.
 *
 * <p>Spring instancie automatiquement les codecs JSON par défaut (Jackson +
 * {@code JavaTimeModule}) — la sérialisation de {@link java.time.OffsetDateTime}
 * dans {@link ApiError} fonctionne nativement.
 */
class FallbackControllerTest {

    private final WebTestClient webTestClient = WebTestClient
            .bindToController(new FallbackController())
            .build();

    @Test
    void get_fallback_should_return_503_with_apierror_payload() {
        ApiError body = webTestClient.get()
                .uri("/fallback/patient-service")
                .header(CorrelationIdFilter.HEADER_NAME, "trace-abc")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(ApiError.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(503);
        assertThat(body.code()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(body.error()).isEqualTo("Service Unavailable");
        assertThat(body.message()).contains("patient-service");
        assertThat(body.path()).isEqualTo("/fallback/patient-service");
        assertThat(body.correlationId()).isEqualTo("trace-abc");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void post_fallback_should_also_return_503() {
        webTestClient.post()
                .uri("/fallback/billing-service")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(ApiError.class)
                .value(payload -> {
                    assertThat(payload.code()).isEqualTo("DOWNSTREAM_UNAVAILABLE");
                    assertThat(payload.message()).contains("billing-service");
                });
    }

    @Test
    void fallback_without_correlation_id_should_have_null_correlation() {
        webTestClient.get()
                .uri("/fallback/doctor-service")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(ApiError.class)
                .value(payload -> assertThat(payload.correlationId()).isNull());
    }
}
