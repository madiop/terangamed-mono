package com.terangamed.medical.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHashStrategyTest {

    private final ContentHashStrategy strategy = new ContentHashStrategy();

    @Test
    @DisplayName("Hash longueur 16 caractères hex")
    void hashLengthIs16HexChars() {
        String hash = strategy.compute(PrescriptionPdfModel.minimalForTest("ORD-1"));
        assertThat(hash).hasSize(16).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("Deux appels sur le même modèle → même hash (déterminisme)")
    void deterministicForSameModel() {
        PrescriptionPdfModel m = PrescriptionPdfModel.minimalForTest("ORD-2026-00042");

        assertThat(strategy.compute(m)).isEqualTo(strategy.compute(m));
    }

    @Test
    @DisplayName("Changement du numéro d'ordonnance → hash différent")
    void differentNumberChangesHash() {
        String h1 = strategy.compute(PrescriptionPdfModel.minimalForTest("ORD-A"));
        String h2 = strategy.compute(PrescriptionPdfModel.minimalForTest("ORD-B"));

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("Changement d'une ligne médicament → hash différent")
    void differentLineChangesHash() {
        PrescriptionPdfModel base = PrescriptionPdfModel.minimalForTest("ORD-X");
        PrescriptionPdfModel modified = new PrescriptionPdfModel(
                base.clinic(), base.prescriptionNumber(), base.issuedAt(), base.validUntil(),
                base.generalInstructions(), base.patient(), base.doctor(),
                List.of(new PrescriptionPdfModel.LineView(1, "Médicament DIFFÉRENT", "1 cp",
                        "3 fois par jour", "5 jours", "Voie orale", "1 boîte", "")),
                base.verificationUrl(), base.qrCodeBase64(), base.renderedAtFormatted()
        );

        assertThat(strategy.compute(base)).isNotEqualTo(strategy.compute(modified));
    }

    @Test
    @DisplayName("Changement du QR base64 → MÊME hash (exclu volontairement)")
    void qrCodeChangeDoesNotChangeHash() {
        PrescriptionPdfModel base = PrescriptionPdfModel.minimalForTest("ORD-X");
        PrescriptionPdfModel withQr = withQr(base, "DIFFERENT-QR-BASE64");

        assertThat(strategy.compute(base)).isEqualTo(strategy.compute(withQr));
    }

    @Test
    @DisplayName("Changement du logo cabinet → MÊME hash (exclu volontairement)")
    void logoChangeDoesNotChangeHash() {
        // IMPORTANT : utiliser UN SEUL modèle de base ; chaque appel à minimalForTest()
        // rappelle LocalDateTime.now() → issuedAt différents → hash différents pour de
        // mauvaises raisons. On dérive donc 2 modèles depuis la même base.
        PrescriptionPdfModel base = PrescriptionPdfModel.minimalForTest("ORD-X");
        PrescriptionPdfModel a = withLogo(base, "LOGO1");
        PrescriptionPdfModel b = withLogo(base, "LOGO2");

        assertThat(strategy.compute(a)).isEqualTo(strategy.compute(b));
    }

    @Test
    @DisplayName("Changement de renderedAt → MÊME hash (exclu, anti-cache-bust)")
    void renderedAtChangeDoesNotChangeHash() {
        PrescriptionPdfModel base = PrescriptionPdfModel.minimalForTest("ORD-X");
        PrescriptionPdfModel later = new PrescriptionPdfModel(
                base.clinic(), base.prescriptionNumber(), base.issuedAt(), base.validUntil(),
                base.generalInstructions(), base.patient(), base.doctor(), base.lines(),
                base.verificationUrl(), base.qrCodeBase64(),
                "Imprimé LE LENDEMAIN"
        );

        assertThat(strategy.compute(base)).isEqualTo(strategy.compute(later));
    }

    @Test
    @DisplayName("Changement de l'URL de vérification → hash différent")
    void verificationUrlChangeAffectsHash() {
        PrescriptionPdfModel base = PrescriptionPdfModel.minimalForTest("ORD-X");
        PrescriptionPdfModel other = new PrescriptionPdfModel(
                base.clinic(), base.prescriptionNumber(), base.issuedAt(), base.validUntil(),
                base.generalInstructions(), base.patient(), base.doctor(), base.lines(),
                "https://NEW.terangamed.sn/verify", base.qrCodeBase64(), base.renderedAtFormatted()
        );

        assertThat(strategy.compute(base)).isNotEqualTo(strategy.compute(other));
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static PrescriptionPdfModel withQr(PrescriptionPdfModel m, String qr) {
        return new PrescriptionPdfModel(m.clinic(), m.prescriptionNumber(), m.issuedAt(),
                m.validUntil(), m.generalInstructions(), m.patient(), m.doctor(), m.lines(),
                m.verificationUrl(), qr, m.renderedAtFormatted());
    }

    private static PrescriptionPdfModel withLogo(PrescriptionPdfModel m, String logo) {
        PrescriptionPdfModel.ClinicView c = m.clinic();
        return new PrescriptionPdfModel(
                new PrescriptionPdfModel.ClinicView(c.name(), c.addressLine1(), c.addressLine2(),
                        c.phone(), c.email(), logo, "image/png"),
                m.prescriptionNumber(), m.issuedAt(), m.validUntil(), m.generalInstructions(),
                m.patient(), m.doctor(), m.lines(),
                m.verificationUrl(), m.qrCodeBase64(), m.renderedAtFormatted()
        );
    }
}
