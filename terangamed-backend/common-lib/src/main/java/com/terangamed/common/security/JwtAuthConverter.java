package com.terangamed.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convertit un JWT Keycloak en {@link JwtAuthenticationToken} Spring Security.
 *
 * <p>Extrait :
 * <ul>
 *   <li>les <b>scopes</b> OAuth2 standard (préfixés {@code SCOPE_})</li>
 *   <li>les <b>rôles realm</b> Keycloak depuis {@code realm_access.roles}
 *       (préfixés {@code ROLE_} pour {@code hasRole(...)})</li>
 *   <li>les <b>rôles client</b> Keycloak depuis {@code resource_access.<resourceId>.roles}
 *       si {@code resourceId} est configuré (préfixés {@code ROLE_})</li>
 * </ul>
 *
 * <p>Le principal est extrait du claim configuré (par défaut {@code preferred_username}).
 * Fallback sur {@code sub} si le claim est absent.
 *
 * <p>Cette classe est thread-safe et immutable une fois construite.
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final JwtGrantedAuthoritiesConverter SCOPES_CONVERTER = new JwtGrantedAuthoritiesConverter();

    private final JwtAuthConverterProperties properties;

    public JwtAuthConverter(JwtAuthConverterProperties properties) {
        this.properties = properties;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> scopes = SCOPES_CONVERTER.convert(jwt);
        Set<GrantedAuthority> roles = extractRoles(jwt);

        Set<GrantedAuthority> authorities = Stream
                .concat(scopes == null ? Stream.empty() : scopes.stream(), roles.stream())
                .collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, principalClaimName(jwt));
    }

    private String principalClaimName(Jwt jwt) {
        String claim = properties.getPrincipalAttribute();
        return StringUtils.hasText(claim) && jwt.hasClaim(claim)
                ? jwt.getClaim(claim)
                : jwt.getSubject();
    }

    private Set<GrantedAuthority> extractRoles(Jwt jwt) {
        Set<GrantedAuthority> roles = new HashSet<>();

        // Rôles realm : realm_access.roles
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap
                && realmMap.get("roles") instanceof Collection<?> realmRoles) {
            addAsRoles(realmRoles, roles);
        }

        // Rôles client : resource_access.<resourceId>.roles
        String clientId = properties.getResourceId();
        if (StringUtils.hasText(clientId)) {
            Object resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess instanceof Map<?, ?> resourceMap
                    && resourceMap.get(clientId) instanceof Map<?, ?> client
                    && client.get("roles") instanceof Collection<?> clientRoles) {
                addAsRoles(clientRoles, roles);
            }
        }

        return roles;
    }

    private static void addAsRoles(Collection<?> source, Set<GrantedAuthority> target) {
        for (Object item : source) {
            if (item instanceof String role && !role.isBlank()) {
                target.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
            }
        }
    }
}
