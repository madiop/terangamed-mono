package com.terangamed.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Génère ou propage un {@code X-Correlation-Id} sur chaque requête entrante.
 *
 * <p><b>Comportement</b> :
 * <ul>
 *   <li>Si le header {@code X-Correlation-Id} est présent (cas d'un appel chaîné venant
 *       d'un autre système), il est conservé tel quel</li>
 *   <li>Sinon, un UUID v4 est généré</li>
 *   <li>Le header est ajouté à la requête transmise au microservice cible</li>
 *   <li>Le header est aussi ajouté à la réponse — utile pour le frontend qui peut
 *       l'attacher aux rapports de bug</li>
 * </ul>
 *
 * <p>Côté microservices downstream, un filtre similaire (à implémenter dans
 * {@code common-lib} ou par service) lira ce header et l'injectera dans le MDC SLF4J
 * pour que tous les logs portent la trace.
 *
 * <p><b>Ordre</b> : {@code HIGHEST_PRECEDENCE} pour que le correlation-id soit
 * disponible dans tous les filtres ultérieurs.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(headers -> headers.set(HEADER_NAME, correlationId)))
                .build();

        mutated.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        return chain.filter(mutated);
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
