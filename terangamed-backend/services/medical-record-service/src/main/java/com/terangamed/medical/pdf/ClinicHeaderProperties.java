package com.terangamed.medical.pdf;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Données du cabinet médical imprimées en en-tête de chaque ordonnance PDF.
 *
 * <p>Préfixe : {@code terangamed.clinic.*}
 *
 * <pre>
 * terangamed:
 *   clinic:
 *     name: Cabinet Médical TerangaMed
 *     address-line-1: Avenue Cheikh Anta Diop
 *     address-line-2: Dakar, Sénégal
 *     phone: +221 33 000 00 00
 *     email: contact@terangamed.sn
 *     logo-classpath: classpath:static/pdf/clinic-logo.svg
 *   public-base-url: https://app.terangamed.sn
 * </pre>
 *
 * <p>Override par déploiement (chaque cabinet client a ses propres valeurs via
 * variables d'environnement ou Config Server). En prod multi-tenants ultérieur,
 * ces valeurs viendront d'une table {@code clinics} en BDD.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "terangamed.clinic")
public class ClinicHeaderProperties {

    /** Nom du cabinet (gras, taille 14, centré en haut du PDF). */
    @NotBlank
    private String name;

    /** Première ligne d'adresse (rue, numéro). */
    @NotBlank
    private String addressLine1;

    /** Seconde ligne (ville, pays). Optionnelle mais recommandée. */
    private String addressLine2;

    /** Téléphone affiché en pied de page. Format libre. */
    @NotBlank
    private String phone;

    /** Email de contact affiché en pied de page. */
    @NotBlank
    private String email;

    /**
     * Chemin classpath ou file du logo affiché en en-tête. Format SVG ou PNG.
     * {@code classpath:static/pdf/clinic-logo.svg} par défaut (placeholder embarqué).
     * Override via volume Docker pour brander une instance.
     */
    private String logoClasspath = "classpath:static/pdf/clinic-logo.svg";
}
