package com.terangamed.medical.pdf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Construit les URLs publiques utilisées dans le PDF (QR de vérification).
 *
 * <p>Centralisé pour éviter que le mapper concatène des chaînes à la main et
 * pour faciliter un changement futur (par exemple ajout d'un short-URL, ou
 * inclusion d'un token signé).
 *
 * <p>Source : propriété {@code terangamed.public-base-url} — exemple en prod
 * {@code https://app.terangamed.sn}.
 */
@Component
public class PublicUrlProvider {

    private final String baseUrl;

    public PublicUrlProvider(@Value("${terangamed.public-base-url}") String baseUrl) {
        // Normalisation : pas de slash final pour simplifier les concat.
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /**
     * URL de vérification d'une ordonnance imprimée. Le QR encodera cette URL.
     *
     * <p>Format : {@code {baseUrl}/verify-prescription/{number}}. L'endpoint
     * cible côté frontend / backend public n'est pas encore implémenté — c'est
     * une feature ultérieure (anti-falsification). Le QR fonctionnera dès que
     * cette page sera en ligne, sans nécessiter de re-impression des ordonnances
     * (l'URL reste stable).
     */
    public String verificationUrlFor(String prescriptionNumber) {
        String encoded = URLEncoder.encode(prescriptionNumber, StandardCharsets.UTF_8);
        return baseUrl + "/verify-prescription/" + encoded;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
