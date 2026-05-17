package com.terangamed.medical.pdf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Modèle de données consommé par le template Thymeleaf
 * {@code templates/pdf/prescription.html}.
 *
 * <p>Volontairement plat et immuable (record) — c'est la "vue" finale du PDF,
 * découplée des entités JPA. L'orchestrateur (étape 6) construit ce modèle
 * à partir de la prescription, des lignes, et des snapshots Feign patient/doctor.
 *
 * <p><b>Conventions</b> :
 * <ul>
 *   <li>Aucun champ ne peut être {@code null} : si une donnée manque côté upstream
 *       (ex: patient sans email), l'orchestrateur substitue {@code "—"}.</li>
 *   <li>Les dates sont déjà formatées en {@link String} dans le modèle pour
 *       contrôler le rendu exact (locale fr-FR, formats médicaux standards).</li>
 *   <li>{@code logoBase64} et {@code qrCodeBase64} : chaînes prêtes à coller
 *       dans un attribut {@code src="data:image/...;base64,..."}.</li>
 * </ul>
 */
public record PrescriptionPdfModel(
        // ─── En-tête cabinet ───
        ClinicView clinic,

        // ─── Méta ordonnance ───
        String prescriptionNumber,       // "ORD-2026-00042"
        String issuedAt,                  // "12 mai 2026 à 14:35"
        String validUntil,                // "12 août 2026"
        String generalInstructions,       // texte libre, peut être ""

        // ─── Patient / Médecin ───
        PatientView patient,
        DoctorView doctor,

        // ─── Médicaments ───
        List<LineView> lines,

        // ─── Vérification (QR) ───
        String verificationUrl,           // affichée en clair sous le QR
        String qrCodeBase64,              // base64 PNG (sans le préfixe data:)

        // ─── Trace ───
        String renderedAtFormatted        // "Imprimé le 12/05/2026 à 14:35"
) {

    /** Vue du cabinet médical (mappée depuis {@link ClinicHeaderProperties}). */
    public record ClinicView(
            String name,
            String addressLine1,
            String addressLine2,
            String phone,
            String email,
            String logoBase64,            // base64 PNG/SVG inline, ou null si absent
            String logoMimeType           // "image/png" ou "image/svg+xml"
    ) {}

    /** Vue partielle du patient (snapshot Feign). */
    public record PatientView(
            String fullName,              // "Diop Madiop"
            String medicalRecordNumber,   // "MR-2026-00007"
            String birthDate,             // "—" en V1 (champ absent du snapshot)
            String sexe                   // "—" en V1
    ) {}

    /** Vue partielle du médecin (snapshot Feign). */
    public record DoctorView(
            String fullName,              // "Dr Aïssatou Ndiaye"
            String licenseNumber,         // "RPPS-12345"
            String specialty              // "Médecine générale"
    ) {}

    /** Ligne médicament — une par {@code PrescriptionLine}. */
    public record LineView(
            int index,                    // 1, 2, 3… (pour numérotation tableau)
            String medicationName,
            String dosage,                // "500 mg" ou "—"
            String frequency,             // "3 fois par jour"
            String duration,              // "7 jours"
            String route,                 // "Voie orale" ou "—"
            String quantity,              // "2 boîtes" ou "—"
            String instructions           // "À prendre pendant les repas" ou ""
    ) {}

    /**
     * Factory helper : produit un modèle minimal pour les tests unitaires de
     * rendu — pas de Feign, pas de DB, juste un PDF imprimable.
     */
    public static PrescriptionPdfModel minimalForTest(String prescriptionNumber) {
        return new PrescriptionPdfModel(
                new ClinicView("Cabinet Test", "1 rue Test", "Dakar",
                        "+221 00", "test@test.sn", null, null),
                prescriptionNumber,
                LocalDateTime.now().toString(),
                LocalDate.now().plusMonths(3).toString(),
                "",
                new PatientView("Patient Test", "MR-TEST", "—", "—"),
                new DoctorView("Dr Test", "RPPS-TEST", "Médecine générale"),
                List.of(new LineView(1, "Paracétamol 500mg", "1 cp", "3 fois par jour",
                        "5 jours", "Voie orale", "1 boîte", "")),
                "http://test/verify",
                null,
                "Imprimé le " + LocalDate.now()
        );
    }
}
