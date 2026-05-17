package com.terangamed.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés pour {@link JwtAuthConverter}.
 *
 * <p>Configuration YAML :
 * <pre>
 * terangamed:
 *   security:
 *     jwt:
 *       principal-attribute: preferred_username
 *       resource-id: terangamed-backend
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "terangamed.security.jwt")
public class JwtAuthConverterProperties {

    /**
     * Claim utilisé comme principal name (par défaut : {@code preferred_username}).
     * Si le claim n'existe pas dans le JWT, fallback sur {@code sub}.
     */
    private String principalAttribute = "preferred_username";

    /**
     * Identifiant du client Keycloak utilisé pour extraire les rôles client
     * depuis {@code resource_access.<resourceId>.roles}.
     * Si vide, seuls les rôles realm ({@code realm_access.roles}) sont extraits.
     */
    private String resourceId;
}
