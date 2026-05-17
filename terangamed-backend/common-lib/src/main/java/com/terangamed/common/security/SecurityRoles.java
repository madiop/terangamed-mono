package com.terangamed.common.security;

/**
 * Constantes des rôles métier TerangaMed et expressions SpEL prêtes à l'emploi
 * pour {@code @PreAuthorize}.
 *
 * <p>Convention : les rôles sont stockés sans préfixe {@code ROLE_} dans Keycloak ;
 * Spring ajoute le préfixe automatiquement via {@link JwtAuthConverter}.
 *
 * <p>Exemple :
 * <pre>
 * &#064;PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
 * public PatientDto getPatient(Long id) { ... }
 * </pre>
 */
public final class SecurityRoles {

    public static final String ADMIN = "ADMIN";
    public static final String DOCTOR = "DOCTOR";
    public static final String RECEPTIONIST = "RECEPTIONIST";
    /** Rôle d'auto-service — un patient connecté via Keycloak peut lire son propre dossier. */
    public static final String PATIENT = "PATIENT";

    /** SpEL : utilisateur ADMIN uniquement. */
    public static final String HAS_ADMIN = "hasRole('" + ADMIN + "')";

    /** SpEL : utilisateur DOCTOR uniquement. */
    public static final String HAS_DOCTOR = "hasRole('" + DOCTOR + "')";

    /** SpEL : utilisateur RECEPTIONIST uniquement. */
    public static final String HAS_RECEPTIONIST = "hasRole('" + RECEPTIONIST + "')";

    /** SpEL : utilisateur PATIENT uniquement. */
    public static final String HAS_PATIENT = "hasRole('" + PATIENT + "')";

    /** SpEL : ADMIN, DOCTOR ou RECEPTIONIST (tout staff). */
    public static final String HAS_ANY_STAFF =
            "hasAnyRole('" + ADMIN + "','" + DOCTOR + "','" + RECEPTIONIST + "')";

    /** SpEL : ADMIN ou DOCTOR (accès dossiers médicaux). */
    public static final String HAS_ADMIN_OR_DOCTOR =
            "hasAnyRole('" + ADMIN + "','" + DOCTOR + "')";

    /** SpEL : ADMIN ou RECEPTIONIST (gestion administrative). */
    public static final String HAS_ADMIN_OR_RECEPTIONIST =
            "hasAnyRole('" + ADMIN + "','" + RECEPTIONIST + "')";

    /**
     * SpEL : staff médical OU patient (lecture de son propre dossier).
     * <p>Le check final que le PATIENT accède bien à SES données est fait
     * programmatiquement dans le service via {@code keycloakSubject} matching.
     */
    public static final String HAS_DOCTOR_ADMIN_OR_PATIENT =
            "hasAnyRole('" + ADMIN + "','" + DOCTOR + "','" + PATIENT + "')";

    private SecurityRoles() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }
}
