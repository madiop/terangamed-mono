package com.terangamed.medical.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Moteur de rendu HTML → PDF/A pour les ordonnances.
 *
 * <p>Pipeline en deux étapes :
 * <ol>
 *   <li><b>Thymeleaf</b> consomme {@link PrescriptionPdfModel} et produit un
 *       HTML/CSS strict (XHTML pour respect OpenHTMLtoPDF).</li>
 *   <li><b>OpenHTMLtoPDF</b> rend ce HTML en PDF via Apache PDFBox.</li>
 * </ol>
 *
 * <p><b>Polices</b> : le moteur tente de charger {@code DejaVu Sans} depuis le
 * classpath ({@code static/pdf/fonts/}) pour garantir l'UTF-8 complet (français
 * + caractères wolof éventuels). Si les TTF sont absents, fallback automatique
 * via CSS sur {@code sans-serif} → Helvetica WinAnsi de PDFBox (couvre tout le
 * français standard mais pas les caractères exotiques).
 *
 * <p><b>Pas d'I/O réseau, pas de DB</b> : ce service est pur (input model →
 * output bytes). L'orchestrateur ({@code PrescriptionPdfService}, étape 6)
 * s'occupe des appels Feign et de la persistance MinIO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionPdfRenderer {

    private static final String TEMPLATE_PATH = "pdf/prescription";
    private static final String DEJAVU_REGULAR_CP = "static/pdf/fonts/DejaVuSans.ttf";
    private static final String DEJAVU_BOLD_CP = "static/pdf/fonts/DejaVuSans-Bold.ttf";
    private static final String FONT_FAMILY = "DejaVu Sans";

    /**
     * Paths filesystem où chercher DejaVu si absent du classpath. Couvre :
     * <ul>
     *   <li>Alpine Linux ({@code apk add ttf-dejavu}) — utilisé dans le Dockerfile du service</li>
     *   <li>Debian / Ubuntu ({@code apt-get install fonts-dejavu-core})</li>
     * </ul>
     * Ordre = priorité de recherche (Alpine d'abord car c'est le runtime cible).
     */
    private static final List<String> DEJAVU_REGULAR_FS_PATHS = List.of(
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    );
    private static final List<String> DEJAVU_BOLD_FS_PATHS = List.of(
            "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    );

    private final SpringTemplateEngine templateEngine;

    /**
     * Rend l'ordonnance en PDF binaire.
     *
     * @param model modèle pré-construit par l'orchestrateur (jamais {@code null})
     * @return bytes PDF/A prêts à streamer ou à stocker
     * @throws PdfStorageException si le rendu échoue (IOException, parsing HTML, etc.)
     */
    public byte[] render(PrescriptionPdfModel model) {
        long start = System.currentTimeMillis();
        String html = renderHtml(model);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            // PDF/A-2-U exige TOUTES les fonts embedded. Sans les TTF DejaVu, on
            // produit un PDF standard (lisible partout) plutôt que de planter en NPE
            // quand OpenHTMLtoPDF tente de subset une font null.
            boolean fontsEmbedded = registerFontsIfAvailable(builder);
            if (fontsEmbedded) {
                builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_2_U);
            }
            builder.toStream(out);
            builder.run();
            byte[] pdf = out.toByteArray();
            log.info("PDF rendu : prescription={}, taille={} octets, pdfA={}, durée={} ms",
                    model.prescriptionNumber(), pdf.length, fontsEmbedded,
                    System.currentTimeMillis() - start);
            return pdf;
        } catch (IOException e) {
            throw new PdfStorageException(
                    "Échec rendu PDF (prescription=" + model.prescriptionNumber() + ")", e);
        }
    }

    /**
     * Étape Thymeleaf isolée — utile pour les tests qui veulent inspecter le HTML
     * sans payer le coût du rendu PDFBox.
     */
    String renderHtml(PrescriptionPdfModel model) {
        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("m", model);
        return templateEngine.process(TEMPLATE_PATH, ctx);
    }

    // ─────────────────────────── Fonts ───────────────────────────

    /**
     * Enregistre {@code DejaVu Sans} (Regular + Bold) via une double stratégie :
     * <ol>
     *   <li>Classpath ({@code static/pdf/fonts/}) — utile en dev local si les TTF
     *       sont commitées ou téléchargées via download-maven-plugin.</li>
     *   <li>Filesystem (paths Linux standard) — utilisé en prod Docker via
     *       {@code apk add ttf-dejavu} dans le Dockerfile.</li>
     * </ol>
     *
     * @return {@code true} si AU MOINS la regular ET la bold ont été enregistrées —
     *         seul cas où on peut activer la conformité PDF/A en toute sécurité.
     */
    private boolean registerFontsIfAvailable(PdfRendererBuilder builder) {
        // Marker INFO pour identifier la version du renderer en runtime — utile
        // pour confirmer qu'un JAR à jour est bien en exécution (vs image Docker stale).
        log.info("[PdfRenderer v2] Recherche polices DejaVu — classpath puis filesystem");

        boolean regularCp = registerFontFromClasspath(builder, DEJAVU_REGULAR_CP, 400);
        boolean regularFs = !regularCp && registerFontFromFilesystem(builder, DEJAVU_REGULAR_FS_PATHS, 400);
        boolean boldCp = registerFontFromClasspath(builder, DEJAVU_BOLD_CP, 700);
        boolean boldFs = !boldCp && registerFontFromFilesystem(builder, DEJAVU_BOLD_FS_PATHS, 700);

        boolean regularOk = regularCp || regularFs;
        boolean boldOk = boldCp || boldFs;
        log.info("[PdfRenderer v2] Sources retenues — regular: {} | bold: {}",
                regularCp ? "classpath" : (regularFs ? "filesystem" : "AUCUNE"),
                boldCp ? "classpath" : (boldFs ? "filesystem" : "AUCUNE"));

        if (!regularOk || !boldOk) {
            log.warn("Polices DejaVu Sans introuvables — ni classpath ({}, {}), ni filesystem ({}, {}). " +
                            "Le rendu PDF plantera (NPE PDFont). En prod : Dockerfile doit faire " +
                            "`apk add ttf-dejavu`. En dev local : télécharger les TTF dans " +
                            "src/main/resources/static/pdf/fonts/ ou installer fonts-dejavu-core.",
                    DEJAVU_REGULAR_CP, DEJAVU_BOLD_CP,
                    DEJAVU_REGULAR_FS_PATHS, DEJAVU_BOLD_FS_PATHS);
            return false;
        }
        return true;
    }

    /** Taille minimale plausible pour un TTF DejaVu Sans (~750 Ko réels pour Regular,
     *  ~700 Ko pour Bold). Seuil à 500 Ko : rejette les fichiers tronqués (incident V1
     *  où des TTF à 306 Ko se sont retrouvés dans le JAR — accepté par l'ancien seuil
     *  à 50 Ko, mais non-parsable par OpenHTMLtoPDF → NPE sur willBeSubset()). */
    private static final long MIN_VALID_TTF_BYTES = 500_000L;

    /**
     * Tente le chargement depuis le classpath. Renvoie {@code false} si absent OU
     * si le fichier est suspect (taille trop petite — typiquement HTML 404 récupéré
     * par un curl mal aiguillé). Le caller bascule alors sur le fallback filesystem.
     */
    private boolean registerFontFromClasspath(PdfRendererBuilder builder, String classpathPath, int weight) {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        if (!resource.exists()) {
            return false;
        }
        // Validation taille : un TTF DejaVu fait ~700 Ko. Un fichier de quelques
        // centaines d'octets dans static/pdf/fonts/ est forcément HTML d'erreur.
        // On le rejette explicitement pour bénéficier du fallback filesystem.
        try {
            long size = resource.contentLength();
            if (size < MIN_VALID_TTF_BYTES) {
                log.warn("Police classpath '{}' suspecte (taille={} octets, attendu > {} Ko) — ignorée, " +
                                "fallback filesystem activé",
                        classpathPath, size, MIN_VALID_TTF_BYTES / 1000);
                return false;
            }
        } catch (IOException e) {
            log.warn("Impossible de lire la taille de '{}' : {} — ignoré", classpathPath, e.getMessage());
            return false;
        }
        builder.useFont(() -> {
            try {
                return resource.getInputStream();
            } catch (IOException e) {
                throw new PdfStorageException("Lecture police impossible: " + classpathPath, e);
            }
        }, FONT_FAMILY, weight, BaseRendererBuilder.FontStyle.NORMAL, true);
        log.debug("Police chargée depuis classpath : {}", classpathPath);
        return true;
    }

    /**
     * Tente le chargement depuis le filesystem du container/host. Itère sur les
     * paths candidats (Alpine, Debian/Ubuntu) et utilise le premier trouvé.
     */
    private boolean registerFontFromFilesystem(PdfRendererBuilder builder, List<String> candidatePaths, int weight) {
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                builder.useFont(f, FONT_FAMILY, weight, BaseRendererBuilder.FontStyle.NORMAL, true);
                log.debug("Police chargée depuis filesystem : {}", path);
                return true;
            }
        }
        return false;
    }
}
