package com.terangamed.medical.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Composant central pour extraire l'identité de l'utilisateur appelant.
 *
 * <p>Sources d'identité utilisées :
 * <ul>
 *   <li>{@link #username()} — {@code preferred_username} du JWT — utilisé pour
 *       les colonnes d'audit (createdBy/updatedBy) et le check d'auteur des
 *       consultations</li>
 *   <li>{@link #subject()} — {@code sub} du JWT — utilisé pour matcher un
 *       {@code Patient} avec son JWT (rôle PATIENT)</li>
 *   <li>{@link #hasRole(String)} — vérification de rôle Spring Security</li>
 * </ul>
 *
 * <p>Encapsule la lecture de SecurityContext pour faciliter les tests unitaires
 * (mockable) et la cohérence cross-services.
 */
@Component
public class CurrentUserProvider {

    /**
     * Récupère le {@code preferred_username} du JWT — utilisé partout où l'on
     * veut afficher / comparer un username (audit, vérif d'auteur).
     *
     * @return l'username, ou {@code "anonymous"} si pas de contexte (ne devrait
     *         pas arriver sur un endpoint authentifié, mais évite NPE)
     */
    public String username() {
        Jwt jwt = currentJwt();
        if (jwt == null) {
            return "anonymous";
        }
        Object username = jwt.getClaim("preferred_username");
        return username != null ? username.toString() : jwt.getSubject();
    }

    /**
     * Récupère le {@code sub} (subject) du JWT — identifiant Keycloak unique
     * et stable, utilisé pour la corrélation avec {@code Patient.keycloakSubject}.
     */
    public String subject() {
        Jwt jwt = currentJwt();
        return jwt == null ? null : jwt.getSubject();
    }

    /** {@code true} si l'utilisateur courant a le rôle (sans préfixe ROLE_). */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        String prefixed = "ROLE_" + role;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(prefixed) || a.equals(role));
    }

    private Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }
}
