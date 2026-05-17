package com.terangamed.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void should_have_highest_precedence_order() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void should_generate_uuid_when_header_absent() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/patients").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedHeader = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedHeader.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(capturedHeader.get())
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo(capturedHeader.get());
    }

    @Test
    void should_propagate_existing_correlation_id() {
        String existing = "client-supplied-trace-123";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/patients")
                .header(CorrelationIdFilter.HEADER_NAME, existing)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedHeader = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedHeader.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(capturedHeader.get()).isEqualTo(existing);
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo(existing);
    }

    @Test
    void should_generate_new_id_when_existing_is_blank() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/patients")
                .header(CorrelationIdFilter.HEADER_NAME, "  ")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<String> capturedHeader = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedHeader.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME));
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(capturedHeader.get())
                .isNotBlank();
    }
}
