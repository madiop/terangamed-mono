package com.terangamed.apigateway.web;

import com.terangamed.apigateway.filter.CorrelationIdFilter;
import com.terangamed.common.exception.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Fallbacks invoqués par le filtre {@code CircuitBreaker} de Spring Cloud Gateway
 * lorsqu'un service downstream est indisponible (timeout, circuit ouvert, erreur).
 *
 * <p>Format de réponse aligné sur {@link ApiError} (pour cohérence avec les
 * microservices métier qui utilisent {@code GlobalExceptionHandler} de {@code common-lib}).
 *
 * <p>Code HTTP : {@code 503 Service Unavailable} — sémantiquement correct,
 * permet aux clients de retry intelligemment.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback générique paramétré par nom de service. Mappé depuis YAML :
     * <pre>
     *   filters:
     *     - name: CircuitBreaker
     *       args:
     *         name: patient-service-cb
     *         fallbackUri: forward:/fallback/patient-service
     * </pre>
     */
    @GetMapping(value = "/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiError>> get(@PathVariable String service, ServerWebExchange exchange) {
        return buildFallback(service, exchange);
    }

    @PostMapping(value = "/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ApiError>> post(@PathVariable String service, ServerWebExchange exchange) {
        return buildFallback(service, exchange);
    }

    private Mono<ResponseEntity<ApiError>> buildFallback(String service, ServerWebExchange exchange) {
        String correlationId = Optional.ofNullable(
                        exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
                .orElse(null);

        ApiError body = ApiError.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .code("DOWNSTREAM_UNAVAILABLE")
                .message("%s is temporarily unavailable. Please retry shortly.".formatted(service))
                .path(exchange.getRequest().getPath().value())
                .correlationId(correlationId)
                .build();

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
