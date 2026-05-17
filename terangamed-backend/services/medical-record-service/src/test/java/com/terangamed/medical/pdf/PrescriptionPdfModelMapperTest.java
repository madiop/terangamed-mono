package com.terangamed.medical.pdf;

import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.entity.MedicationRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du {@link PrescriptionPdfModelMapper} — formatage des champs, fallback
 * sur valeurs absentes, mapping des voies d'administration, pluralisation des
 * quantités, chargement du logo classpath.
 */
class PrescriptionPdfModelMapperTest {

    private PrescriptionPdfModelMapper mapper;
    private ClinicHeaderProperties clinic;

    @BeforeEach
    void setUp() {
        clinic = new ClinicHeaderProperties();
        clinic.setName("Cabinet Test");
        clinic.setAddressLine1("Avenue Test");
        clinic.setAddressLine2("Dakar");
        clinic.setPhone("+221 33 000 00 00");
        clinic.setEmail("test@terangamed.sn");
        clinic.setLogoClasspath("classpath:static/pdf/clinic-logo.svg"); // existe dans resources

        ResourceLoader rl = new DefaultResourceLoader();
        VerificationQrService qr = new VerificationQrService();
        PublicUrlProvider urlProvider = new PublicUrlProvider("https://test.terangamed.sn");

        mapper = new PrescriptionPdfModelMapper(clinic, qr, rl, urlProvider);
    }

    @Test
    @DisplayName("Build complet : tous les champs critiques présents")
    void buildModel_allFieldsPopulated() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "Paracétamol", MedicationRoute.ORAL, 2)
        ), patient(), doctor());

        assertThat(m.prescriptionNumber()).isEqualTo("ORD-2026-00042");
        assertThat(m.clinic().name()).isEqualTo("Cabinet Test");
        assertThat(m.patient().fullName()).isEqualTo("Diop Madiop");
        assertThat(m.doctor().fullName()).isEqualTo("Dr Ndiaye Aïssatou");
        assertThat(m.lines()).hasSize(1);
        assertThat(m.lines().get(0).medicationName()).isEqualTo("Paracétamol");
        assertThat(m.qrCodeBase64()).isNotBlank();
        assertThat(m.verificationUrl()).contains("ORD-2026-00042");
    }

    @Test
    @DisplayName("Mapping voie ORAL → 'Voie orale' (FR)")
    void routeOral_mappedToFrench() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)
        ), patient(), doctor());

        assertThat(m.lines().get(0).route()).isEqualTo("Voie orale");
    }

    @Test
    @DisplayName("Mapping voie INHALATION → 'Inhalation' (FR)")
    void routeInhalation_mappedToFrench() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.INHALATION, 1)
        ), patient(), doctor());

        assertThat(m.lines().get(0).route()).isEqualTo("Inhalation");
    }

    @Test
    @DisplayName("Route null → '—'")
    void routeNull_dash() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", null, 1)
        ), patient(), doctor());

        assertThat(m.lines().get(0).route()).isEqualTo("—");
    }

    @Test
    @DisplayName("Quantité 1 → 'boîte' (singulier)")
    void quantitySingular() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)
        ), patient(), doctor());

        assertThat(m.lines().get(0).quantity()).isEqualTo("1 boîte");
    }

    @Test
    @DisplayName("Quantité 3 → 'boîtes' (pluriel)")
    void quantityPlural() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 3)
        ), patient(), doctor());

        assertThat(m.lines().get(0).quantity()).isEqualTo("3 boîtes");
    }

    @Test
    @DisplayName("Quantité null → '—'")
    void quantityNull_dash() {
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, null)
        ), patient(), doctor());

        assertThat(m.lines().get(0).quantity()).isEqualTo("—");
    }

    @Test
    @DisplayName("Patient sans n° dossier → '—' (pas null dans le modèle)")
    void patientWithoutMedicalRecordNumber_dash() {
        PatientSnapshotDto incomplete = new PatientSnapshotDto(
                1L, null, "Doe", "John", null, "ACTIVE", "sub"
        );
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)
        ), incomplete, doctor());

        assertThat(m.patient().medicalRecordNumber()).isEqualTo("—");
        assertThat(m.patient().fullName()).isEqualTo("Doe John");
    }

    @Test
    @DisplayName("Logo SVG skippé en V1 (manque openhtmltopdf-svg-support)")
    void svgLogoSkippedInV1() {
        // En V1 : le mapper refuse explicitement les SVG (nécessiteraient Batik+10Mo).
        // Pour avoir un logo, fournir un PNG/JPG. Ce comportement protège du warning
        // OpenHTMLtoPDF "Unrecognized image format" + risque NPE downstream.
        PrescriptionPdfModel m = mapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)
        ), patient(), doctor());

        assertThat(m.clinic().logoBase64()).isNull();
        assertThat(m.clinic().logoMimeType()).isNull();
    }

    @Test
    @DisplayName("Logo absent → champs base64/mime null mais pas d'exception")
    void absentLogo_nullFields() {
        clinic.setLogoClasspath("classpath:static/pdf/inexistant.svg");
        ResourceLoader rl = new DefaultResourceLoader();
        VerificationQrService qr = new VerificationQrService();
        PublicUrlProvider urlProvider = new PublicUrlProvider("https://t");
        PrescriptionPdfModelMapper localMapper = new PrescriptionPdfModelMapper(clinic, qr, rl, urlProvider);

        PrescriptionPdfModel m = localMapper.buildModel(prescriptionWith(
                samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)
        ), patient(), doctor());

        assertThat(m.clinic().logoBase64()).isNull();
        assertThat(m.clinic().logoMimeType()).isNull();
    }

    @Test
    @DisplayName("validUntil null → '—'")
    void validUntilNull_dash() {
        PrescriptionDto p = new PrescriptionDto(
                42L, "ORD-2026-00042", 100L, Instant.parse("2026-05-12T14:35:00Z"),
                null, "",
                List.of(samplePrescriptionLine(1L, "X", MedicationRoute.ORAL, 1)),
                Instant.now(), Instant.now(), "dr-test", "dr-test", 0L
        );

        PrescriptionPdfModel m = mapper.buildModel(p, patient(), doctor());

        assertThat(m.validUntil()).isEqualTo("—");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static PrescriptionDto prescriptionWith(PrescriptionLineDto... lines) {
        return new PrescriptionDto(
                42L, "ORD-2026-00042", 100L, Instant.parse("2026-05-12T14:35:00Z"),
                LocalDate.of(2026, 8, 12), "", List.of(lines),
                Instant.now(), Instant.now(), "dr-test", "dr-test", 0L
        );
    }

    private static PrescriptionLineDto samplePrescriptionLine(Long id, String med,
                                                              MedicationRoute route, Integer qty) {
        return new PrescriptionLineDto(id, 42L, med, "500 mg", "3 fois/j",
                "5 jours", route, "", qty, Instant.now(), Instant.now(),
                "dr-test", "dr-test", 0L);
    }

    private static PatientSnapshotDto patient() {
        return new PatientSnapshotDto(1L, "MR-2026-00007", "Diop", "Madiop",
                "madiop@test.sn", "ACTIVE", "sub-1");
    }

    private static DoctorSnapshotDto doctor() {
        return new DoctorSnapshotDto(500L, "RPPS-12345", "Ndiaye", "Aïssatou",
                "Médecine générale", "ACTIVE");
    }
}
