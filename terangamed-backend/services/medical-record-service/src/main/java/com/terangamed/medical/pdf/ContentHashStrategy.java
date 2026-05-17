package com.terangamed.medical.pdf;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calcule un hash déterministe du contenu imprimé d'une ordonnance.
 *
 * <p>Utilisé par l'orchestrateur pour bâtir une clé d'objet MinIO idempotente :
 * tant que les données qui apparaissent sur le PDF ne changent pas, le hash
 * reste identique → le rendu est lu depuis le cache MinIO sans regénération.
 *
 * <p><b>Stratégie</b> : SHA-256 du modèle {@link PrescriptionPdfModel} sérialisé
 * dans un ordre fixe et stable. On garde les 16 premiers caractères hex (8 octets,
 * espace de 2⁶⁴) — suffisant pour éviter les collisions à l'échelle d'un cabinet
 * (~10⁵ ordonnances/an pendant 10 ans = 10⁶ valeurs, vs 1.8×10¹⁹ valeurs possibles).
 *
 * <p><b>Champs explicitement exclus du hash</b> :
 * <ul>
 *   <li>{@code renderedAtFormatted} — varie à chaque rendu (anti-cache)</li>
 *   <li>{@code qrCodeBase64} — dérivé de {@code verificationUrl}</li>
 *   <li>{@code logoBase64} — un changement de logo cabinet ne doit PAS invalider
 *       le cache des anciennes ordonnances (audit légal)</li>
 * </ul>
 */
@Component
public class ContentHashStrategy {

    private static final int HASH_LENGTH = 16;
    private static final char FIELD_SEP = '';   // ASCII Unit Separator — invisible et improbable dans les champs
    private static final char RECORD_SEP = '';  // ASCII Record Separator — entre lignes médicaments

    /**
     * Produit un hash hex de 16 caractères représentant le contenu imprimable
     * du modèle. Deux modèles identiques (même prescription, même patient/médecin,
     * même cabinet) produisent toujours le même hash.
     */
    public String compute(PrescriptionPdfModel model) {
        StringBuilder sb = new StringBuilder(512);

        // ─── Méta ordonnance ───
        appendField(sb, model.prescriptionNumber());
        appendField(sb, model.issuedAt());
        appendField(sb, model.validUntil());
        appendField(sb, model.generalInstructions());

        // ─── Patient (snapshot) ───
        appendField(sb, model.patient().fullName());
        appendField(sb, model.patient().medicalRecordNumber());
        appendField(sb, model.patient().birthDate());
        appendField(sb, model.patient().sexe());

        // ─── Médecin (snapshot) ───
        appendField(sb, model.doctor().fullName());
        appendField(sb, model.doctor().licenseNumber());
        appendField(sb, model.doctor().specialty());

        // ─── Cabinet (l'en-tête imprimé) ───
        appendField(sb, model.clinic().name());
        appendField(sb, model.clinic().addressLine1());
        appendField(sb, model.clinic().addressLine2());
        appendField(sb, model.clinic().phone());
        appendField(sb, model.clinic().email());

        // ─── URL de vérification (impacte le QR) ───
        appendField(sb, model.verificationUrl());

        // ─── Lignes médicaments — ordre = ordre d'apparition dans le PDF ───
        for (PrescriptionPdfModel.LineView line : model.lines()) {
            sb.append(RECORD_SEP);
            appendField(sb, String.valueOf(line.index()));
            appendField(sb, line.medicationName());
            appendField(sb, line.dosage());
            appendField(sb, line.frequency());
            appendField(sb, line.duration());
            appendField(sb, line.route());
            appendField(sb, line.quantity());
            appendField(sb, line.instructions());
        }

        return sha256Hex(sb.toString()).substring(0, HASH_LENGTH);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static void appendField(StringBuilder sb, String value) {
        sb.append(value != null ? value : "").append(FIELD_SEP);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la spec Java — ne devrait jamais arriver
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }
}
