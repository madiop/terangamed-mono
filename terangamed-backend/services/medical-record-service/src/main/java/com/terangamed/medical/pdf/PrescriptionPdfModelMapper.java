package com.terangamed.medical.pdf;

import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.entity.MedicationRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Construit le {@link PrescriptionPdfModel} consommé par le template Thymeleaf
 * à partir des sources de données (DTO ordonnance, snapshots Feign patient/médecin,
 * propriétés cabinet, QR encodé).
 *
 * <p>Concentré ici par séparation des responsabilités : le service orchestrateur
 * ne fait pas de formatage de date ni de génération de QR, il délègue tout le
 * "shaping" à ce mapper.
 *
 * <p>Le logo cabinet est chargé une seule fois (cache atomique) — évite de relire
 * la ressource classpath à chaque rendu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionPdfModelMapper {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private static final DateTimeFormatter ISSUED_AT_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH).withZone(DAKAR);
    private static final DateTimeFormatter VALID_UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter RENDERED_AT_FORMAT =
            DateTimeFormatter.ofPattern("'Imprimé le' d/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    /** Libellés français des voies d'administration — affichés sur le PDF. */
    private static final Map<MedicationRoute, String> ROUTE_LABELS = Map.of(
            MedicationRoute.ORAL,        "Voie orale",
            MedicationRoute.INJECTION,   "Injection",
            MedicationRoute.TOPICAL,     "Voie cutanée",
            MedicationRoute.INHALATION,  "Inhalation",
            MedicationRoute.OPHTHALMIC,  "Voie ophtalmique",
            MedicationRoute.NASAL,       "Voie nasale",
            MedicationRoute.RECTAL,      "Voie rectale",
            MedicationRoute.OTHER,       "Autre"
    );

    /** Placeholder affiché quand une donnée patient/médecin est absente du snapshot Feign. */
    private static final String MISSING = "—";

    private final ClinicHeaderProperties clinicProps;
    private final VerificationQrService qrService;
    private final ResourceLoader resourceLoader;
    private final PublicUrlProvider publicUrlProvider;

    /** Cache du logo encodé (chargé à la première utilisation, thread-safe). */
    private final AtomicReference<LogoCache> logoCache = new AtomicReference<>();

    /**
     * Construit le modèle complet — pas d'I/O réseau ici, toutes les sources sont
     * déjà résolues par l'orchestrateur.
     */
    public PrescriptionPdfModel buildModel(PrescriptionDto prescription,
                                            PatientSnapshotDto patient,
                                            DoctorSnapshotDto doctor) {
        Objects.requireNonNull(prescription, "prescription");
        Objects.requireNonNull(patient, "patient");
        Objects.requireNonNull(doctor, "doctor");

        String verificationUrl = publicUrlProvider.verificationUrlFor(prescription.prescriptionNumber());
        String qrBase64 = qrService.encodeAsBase64Png(verificationUrl);
        LogoCache logo = resolveLogo();

        return new PrescriptionPdfModel(
                new PrescriptionPdfModel.ClinicView(
                        clinicProps.getName(),
                        clinicProps.getAddressLine1(),
                        clinicProps.getAddressLine2(),
                        clinicProps.getPhone(),
                        clinicProps.getEmail(),
                        logo != null ? logo.base64() : null,
                        logo != null ? logo.mimeType() : null
                ),
                prescription.prescriptionNumber(),
                formatIssuedAt(prescription.issuedAt()),
                formatValidUntil(prescription.validUntil()),
                nullToEmpty(prescription.generalInstructions()),
                new PrescriptionPdfModel.PatientView(
                        formatFullName(patient.lastName(), patient.firstName()),
                        nullToDash(patient.medicalRecordNumber()),
                        MISSING, // date de naissance absente du snapshot Feign V1
                        MISSING  // sexe absent du snapshot Feign V1
                ),
                new PrescriptionPdfModel.DoctorView(
                        formatDoctorName(doctor.lastName(), doctor.firstName()),
                        nullToDash(doctor.licenseNumber()),
                        nullToDash(doctor.specialty())
                ),
                mapLines(prescription.lines()),
                verificationUrl,
                qrBase64,
                RENDERED_AT_FORMAT.format(LocalDateTime.now(DAKAR))
        );
    }

    // ─────────────────────────── Mapping helpers ───────────────────────────

    private List<PrescriptionPdfModel.LineView> mapLines(List<PrescriptionLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        // Index 1-based pour affichage humain
        return java.util.stream.IntStream.range(0, lines.size())
                .mapToObj(i -> mapLine(i + 1, lines.get(i)))
                .toList();
    }

    private PrescriptionPdfModel.LineView mapLine(int index, PrescriptionLineDto dto) {
        return new PrescriptionPdfModel.LineView(
                index,
                dto.medicationName(),
                nullToDash(dto.dosage()),
                nullToDash(dto.frequency()),
                nullToDash(dto.duration()),
                dto.route() != null ? ROUTE_LABELS.getOrDefault(dto.route(), dto.route().name()) : MISSING,
                dto.quantity() != null ? dto.quantity() + " boîte" + (dto.quantity() > 1 ? "s" : "") : MISSING,
                nullToEmpty(dto.instructions())
        );
    }

    private String formatIssuedAt(Instant issuedAt) {
        return issuedAt != null ? ISSUED_AT_FORMAT.format(issuedAt) : MISSING;
    }

    private String formatValidUntil(LocalDate validUntil) {
        return validUntil != null ? VALID_UNTIL_FORMAT.format(validUntil) : MISSING;
    }

    private static String formatFullName(String lastName, String firstName) {
        String last = lastName != null ? lastName : "";
        String first = firstName != null ? firstName : "";
        String combined = (last + " " + first).trim();
        return combined.isEmpty() ? MISSING : combined;
    }

    private static String formatDoctorName(String lastName, String firstName) {
        String name = formatFullName(lastName, firstName);
        return MISSING.equals(name) ? MISSING : "Dr " + name;
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? MISSING : value;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    // ─────────────────────────── Logo cache ───────────────────────────

    private LogoCache resolveLogo() {
        LogoCache cached = logoCache.get();
        if (cached != null) {
            return cached.isAbsent() ? null : cached;
        }
        LogoCache resolved = loadLogo();
        logoCache.compareAndSet(null, resolved);
        return resolved.isAbsent() ? null : resolved;
    }

    private LogoCache loadLogo() {
        String path = clinicProps.getLogoClasspath();
        if (path == null || path.isBlank()) {
            log.info("Aucun logo cabinet configuré (clinic.logo-classpath vide)");
            return LogoCache.ABSENT;
        }
        // SVG : nécessite openhtmltopdf-svg-support + Batik (~10 Mo de deps).
        // Hors scope V1 — on log un warn et on continue sans logo. Pour activer un
        // logo dans le PDF, fournir un PNG/JPG via CLINIC_LOGO (cf. application.yml).
        if (path.toLowerCase().endsWith(".svg")) {
            log.warn("Logo SVG non supporté en V1 (manque openhtmltopdf-svg-support). " +
                    "Le PDF sera généré sans logo. Pour avoir un logo : utiliser un PNG/JPG. Path={}", path);
            return LogoCache.ABSENT;
        }
        Resource res = resourceLoader.getResource(path);
        if (!res.exists()) {
            log.warn("Logo cabinet introuvable : {} — le PDF n'aura pas de logo", path);
            return LogoCache.ABSENT;
        }
        try (InputStream is = res.getInputStream()) {
            byte[] bytes = StreamUtils.copyToByteArray(is);
            String mime = detectMime(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            log.info("Logo cabinet chargé : {} ({} octets, {})", path, bytes.length, mime);
            return new LogoCache(base64, mime);
        } catch (IOException e) {
            log.error("Échec lecture logo cabinet '{}' — le PDF sera sans logo", path, e);
            return LogoCache.ABSENT;
        }
    }

    private static String detectMime(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".svg") || lower.endsWith(".svg.gz")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "image/png"; // fallback PNG (le plus courant)
    }

    /** Cache atomique du logo encodé. {@link #ABSENT} signale "déjà tenté, absent". */
    private record LogoCache(String base64, String mimeType) {
        static final LogoCache ABSENT = new LogoCache(null, null);
        boolean isAbsent() { return base64 == null; }
    }
}
