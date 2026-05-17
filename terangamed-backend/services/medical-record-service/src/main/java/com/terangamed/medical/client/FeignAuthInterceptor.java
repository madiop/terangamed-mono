package com.terangamed.medical.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propage l'header {@code Authorization} (Bearer JWT) du request HTTP courant
 * vers les appels Feign sortants. Indispensable pour que patient/doctor/
 * appointment-service authentifient l'appelant via la chaîne d'appels.
 *
 * <p>Sans cet interceptor, les appels Feign seraient anonymes → 401 Unauthorized.
 *
 * <p>Pour les appels machine-to-machine (jobs, consumers Kafka), utiliser un
 * flow {@code client_credentials} et un interceptor distinct — non requis ici.
 */
@Configuration
public class FeignAuthInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return; // contexte non-servlet (test, scheduler) : pas de JWT à propager
        }
        HttpServletRequest currentRequest = attributes.getRequest();
        String authorization = currentRequest.getHeader(AUTHORIZATION_HEADER);
        if (authorization != null && !authorization.isBlank()) {
            template.header(AUTHORIZATION_HEADER, authorization);
        }
    }
}
