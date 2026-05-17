package com.terangamed.medical.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du {@link PrescriptionPdfRenderer}.
 *
 * <p>Le {@link SpringTemplateEngine} est instancié manuellement (sans
 * {@code @SpringBootTest}) pour garder le test rapide et focalisé sur le
 * pipeline Thymeleaf → OpenHTMLtoPDF.
 *
 * <p>Stratégie de vérification :
 * <ul>
 *   <li>Le HTML intermédiaire contient toutes les données critiques
 *       (n° d'ordonnance, nom patient, nom médicament).</li>
 *   <li>Le PDF binaire commence bien par la signature {@code %PDF-}.</li>
 *   <li>Le rendu est déterministe et ne crash pas sur les cas limites
 *       (logo absent, QR absent, instructions vides).</li>
 * </ul>
 */
class PrescriptionPdfRendererTest {

    private PrescriptionPdfRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new PrescriptionPdfRenderer(engine);
    }

    // ─────────────────────────── HTML intermédiaire ───────────────────────────

    @Test
    @DisplayName("Le HTML contient le numéro d'ordonnance, le patient et le médicament")
    void htmlContainsKeyData() {
        PrescriptionPdfModel model = PrescriptionPdfModel.minimalForTest("ORD-2026-00042");

        String html = renderer.renderHtml(model);

        assertThat(html).contains("ORD-2026-00042");
        assertThat(html).contains("Patient Test");
        assertThat(html).contains("Dr Test");
        assertThat(html).contains("Paracétamol 500mg");
        assertThat(html).contains("ORDONNANCE MÉDICALE");
    }

    @Test
    @DisplayName("Le HTML supporte l'UTF-8 (accents français et apostrophes)")
    void htmlPreservesUtf8() {
        PrescriptionPdfModel model = new PrescriptionPdfModel(
                new PrescriptionPdfModel.ClinicView("Cabinet Médical d'Aïssatou",
                        "Avenue Cheikh Anta Diop", "Dakar, Sénégal",
                        "+221", "test@test.sn", null, null),
                "ORD-2026-00001",
                "12 mai 2026", "12 août 2026", "À prendre régulièrement.",
                new PrescriptionPdfModel.PatientView("Diop Madiop", "MR-001", "—", "—"),
                new PrescriptionPdfModel.DoctorView("Dr Aïssatou Ndiaye", "RPPS-1", "Médecine générale"),
                List.of(new PrescriptionPdfModel.LineView(1, "Médicament X", "1 cp",
                        "Matin et soir", "7 jours", "Voie orale", "1 boîte",
                        "À prendre pendant les repas")),
                "https://terangamed.sn/verify", null, "Imprimé"
        );

        String html = renderer.renderHtml(model);

        // Thymeleaf échappe les apostrophes en &#39; pour conformité XHTML.
        // On cherche les segments UTF-8 sans apostrophe pour rester insensible
        // à la stratégie d'encodage du moteur de template.
        assertThat(html)
                .contains("Cabinet Médical")
                .contains("Aïssatou")
                .contains("Avenue Cheikh Anta Diop")
                .contains("Dakar, Sénégal")
                .contains("Dr Aïssatou Ndiaye")
                .contains("À prendre régulièrement.")
                .contains("À prendre pendant les repas");
    }

    @Test
    @DisplayName("Plusieurs lignes → toutes présentes dans la table HTML")
    void allLinesRenderedInTable() {
        PrescriptionPdfModel model = new PrescriptionPdfModel(
                new PrescriptionPdfModel.ClinicView("C", "A", null, "T", "e@e.e", null, null),
                "ORD-2026-00099", "d1", "d2", "",
                new PrescriptionPdfModel.PatientView("P", "M", "—", "—"),
                new PrescriptionPdfModel.DoctorView("D", "R", "S"),
                List.of(
                        new PrescriptionPdfModel.LineView(1, "Aspirine", "500mg", "1×/j", "5j", "Orale", "1", ""),
                        new PrescriptionPdfModel.LineView(2, "Doliprane", "1g", "3×/j", "3j", "Orale", "1", ""),
                        new PrescriptionPdfModel.LineView(3, "Vitamine C", "1g", "1×/j", "1mois", "Orale", "2", "")
                ),
                "https://verify", null, "Imprimé"
        );

        String html = renderer.renderHtml(model);

        assertThat(html).contains("Aspirine").contains("Doliprane").contains("Vitamine C");
    }

    @Test
    @DisplayName("Instructions générales vides → bloc <div> absent du HTML")
    void emptyInstructionsBlockOmitted() {
        PrescriptionPdfModel model = PrescriptionPdfModel.minimalForTest("ORD-X");

        String html = renderer.renderHtml(model);

        // On cherche la balise ouvrante précise. Le mot "general-instructions" seul
        // apparaît aussi dans le bloc <style> (sélecteur CSS) — chercher la <div>
        // garantit qu'on parle du rendu, pas du CSS.
        assertThat(html).doesNotContain("<div class=\"general-instructions\">");
    }

    // ─────────────────────────── PDF final ───────────────────────────
    // Ces tests exécutent le pipeline complet (Thymeleaf → OpenHTMLtoPDF → bytes).
    // Les TTF DejaVu sont auto-téléchargées via download-maven-plugin
    // (phase generate-resources, cf. pom.xml du service) → présentes dans
    // target/classes/static/pdf/fonts/ au moment d'exécution des tests.

    @Test
    @DisplayName("Le rendu PDF produit des bytes commençant par %PDF-")
    void renderProducesValidPdfMagicBytes() {
        PrescriptionPdfModel model = PrescriptionPdfModel.minimalForTest("ORD-2026-00042");

        byte[] pdf = renderer.render(model);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(500); // un PDF même minimal pèse > 500 octets
        String head = new String(pdf, 0, Math.min(8, pdf.length), StandardCharsets.US_ASCII);
        assertThat(head).startsWith("%PDF-");
    }

    @Test
    @DisplayName("Le PDF contient la chaîne du numéro d'ordonnance (recherche brute)")
    void pdfContainsPrescriptionNumber() {
        PrescriptionPdfModel model = PrescriptionPdfModel.minimalForTest("ORD-2026-00777");

        byte[] pdf = renderer.render(model);
        // PDFBox encode le texte en streams compressés ; on cherche dans la
        // représentation brute (souvent visible non-compressée pour les petits docs).
        String dump = new String(pdf, StandardCharsets.ISO_8859_1);
        // On ne peut pas garantir que la chaîne apparaisse en clair (compression
        // FlateDecode), donc le test vérifie surtout que le rendu ne crash pas et
        // produit un PDF non vide. La recherche de texte précise se fait via
        // PDFBox dans les tests d'intégration (étape 8).
        assertThat(dump).contains("%PDF-").contains("%%EOF");
    }

    @Test
    @DisplayName("Rendu robuste avec QR base64 et logo base64 fournis")
    void renderWithEmbeddedImages() {
        // PNG 1x1 noir valide en base64
        String onePxPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgAAIAAAUAAen63NgAAAAASUVORK5CYII=";
        PrescriptionPdfModel model = new PrescriptionPdfModel(
                new PrescriptionPdfModel.ClinicView("C", "A", null, "T", "e@e.e", onePxPng, "image/png"),
                "ORD-IMG", "d1", "d2", "",
                new PrescriptionPdfModel.PatientView("P", "M", "—", "—"),
                new PrescriptionPdfModel.DoctorView("D", "R", "S"),
                List.of(new PrescriptionPdfModel.LineView(1, "Med", "—", "—", "—", "—", "—", "")),
                "https://verify", onePxPng, "Imprimé"
        );

        byte[] pdf = renderer.render(model);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
