package com.terangamed.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Implémentation de {@link AuditorAware} qui extrait l'utilisateur courant
 * depuis le {@code SecurityContext} de Spring Security.
 *
 * <p>Le nom retourné est {@code authentication.getName()} — pour un JWT Keycloak
 * passé par {@link com.terangamed.common.security.JwtAuthConverter}, c'est le
 * claim configuré comme principal (par défaut {@code preferred_username}).
 *
 * <p>Retourne {@link Optional#empty()} si :
 * <ul>
 *   <li>aucune authentification dans le contexte (job batch, scheduler, …)</li>
 *   <li>l'authentification est anonyme</li>
 *   <li>l'authentification n'est pas marquée {@code authenticated}</li>
 * </ul>
 * Dans ces cas, les champs {@code created_by} / {@code updated_by} seront
 * laissés à {@code null}, ce qui est acceptable pour des opérations système.
 */
public class JwtAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }
}
