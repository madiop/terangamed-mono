package com.terangamed.medical.pdf;

import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.client.AppointmentServiceClient;
import com.terangamed.medical.client.DoctorServiceClient;
import com.terangamed.medical.client.PatientServiceClient;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.MedicationRoute;
import com.terangamed.medical.entity.Prescription;
import com.terangamed.medical.entity.PrescriptionLine;
import com.terangamed.medical.repository.ConsultationRepository;
import com.terangamed.medical.repository.MedicalRecordRepository;
import com.terangamed.medical.repository.PrescriptionLineRepository;
import com.terangamed.medical.repository.PrescriptionRepository;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test d'intégration end-to-end pour la chaîne de génération de PDF d'ordonnance.
 *
 * <h3>Périmètre</h3>
 * <ul>
 *   <li><b>Réel</b> : PostgreSQL (Testcontainer), MinIO (Testcontainer), Flyway,
 *       Thymeleaf, OpenHTMLtoPDF, PDFBox, MinIO Java SDK, mappers, hash strategy.</li>
 *   <li><b>Mocké</b> : clients Feign vers patient-service / doctor-service /
 *       appointment-service (sinon nécessite Eureka + autres services up).</li>
 * </ul>
 *
 * <h3>Scénarios vérifiés</h3>
 * <ol>
 *   <li>Cache miss : PDF généré, stocké dans MinIO, contenu extrait via PDFBox
 *       contient les données critiques (n° ordonnance, patient, médecin, médicament).</li>
 *   <li>Cache hit : 2e appel ne déclenche pas de nouveau rendu (vérification via
 *       {@code @SpyBean} sur le renderer), et MinIO ne contient toujours qu'un seul objet.</li>
 *   <li>Modification d'une ligne → nouveau hash → nouveau rendu, MinIO contient
 *       maintenant 2 objets (historique préservé pour audit).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PrescriptionPdfServiceIT extends AbstractPostgresIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")
            .withUserName("test-user")
            .withPassword("test-pass-1234");

    /**
     * Bind MinIO Testcontainer aux propriétés Spring que consomme
     * {@link PdfStorageProperties}. Bucket différent à chaque exécution pour
     * éviter les interférences entre tests parallèles.
     */
    @DynamicPropertySource
    static void registerMinio(DynamicPropertyRegistry registry) {
        registry.add("terangamed.pdf.storage.minio.endpoint", MINIO::getS3URL);
        registry.add("terangamed.pdf.storage.minio.access-key", MINIO::getUserName);
        registry.add("terangamed.pdf.storage.minio.secret-key", MINIO::getPassword);
        registry.add("terangamed.pdf.storage.minio.bucket", () -> "prescriptions-it-" + System.currentTimeMillis());
        registry.add("terangamed.pdf.storage.minio.auto-create-bucket", () -> "true");
        // En profil test, on désactive également la validation OAuth2 (pas de Keycloak local)
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:0/jwks");
    }

    @Autowired PrescriptionPdfService pdfService;
    @Autowired MinioClient minioClient;
    @Autowired PdfStorageProperties storageProperties;

    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired PrescriptionLineRepository prescriptionLineRepository;

    @SpyBean PrescriptionPdfRenderer renderer;

    @MockBean PatientServiceClient patientClient;
    @MockBean DoctorServiceClient doctorClient;
    @MockBean AppointmentServiceClient appointmentClient;

    private Long prescriptionId;

    @BeforeEach
    void prepareFixtures() throws Exception {
        // ─── Cleanup état précédent (tests séquentiels — bucket et DB partagés) ───
        cleanupBucket();
        prescriptionLineRepository.deleteAll();
        prescriptionRepository.deleteAll();
        consultationRepository.deleteAll();
        medicalRecordRepository.deleteAll();
        Mockito.reset(renderer); // reset le compteur de @SpyBean entre tests

        // ─── Mocks Feign (snapshots patient + médecin) ───
        when(patientClient.findById(any())).thenReturn(new PatientSnapshotDto(
                1234L, "MR-2026-00777", "Diop", "Madiop",
                "madiop@test.sn", "ACTIVE", "sub-1234"));
        when(doctorClient.findById(any())).thenReturn(new DoctorSnapshotDto(
                500L, "RPPS-12345", "Ndiaye", "Aïssatou",
                "Médecine générale", "ACTIVE"));

        // ─── Insertion entités JPA via repositories (bypass des services pour rester
        //     ciblé sur le service PDF — pas de cascade de validations métier) ───
        MedicalRecord record = medicalRecordRepository.save(MedicalRecord.builder()
                .patientId(1234L)
                .softDeleted(false)
                .build());

        Consultation consultation = consultationRepository.save(Consultation.builder()
                .medicalRecordId(record.getId())
                .doctorId(500L)
                .consultationDate(LocalDateTime.now())
                .motif("Test PDF — fièvre persistante")
                .diagnostic("Infection virale")
                .signed(false)
                .softDeleted(false)
                .build());

        Prescription prescription = prescriptionRepository.save(Prescription.builder()
                .prescriptionNumber("ORD-2026-00777")
                .consultationId(consultation.getId())
                .issuedAt(Instant.now())
                .validUntil(LocalDate.now().plusMonths(3))
                .generalInstructions("Repos 5 jours, hydratation abondante.")
                .build());

        prescriptionLineRepository.save(PrescriptionLine.builder()
                .prescriptionId(prescription.getId())
                .medicationName("Paracétamol 500mg")
                .dosage("1 comprimé")
                .frequency("3 fois par jour")
                .duration("5 jours")
                .route(MedicationRoute.ORAL)
                .quantity(1)
                .instructions("À prendre pendant les repas")
                .build());
        prescriptionLineRepository.save(PrescriptionLine.builder()
                .prescriptionId(prescription.getId())
                .medicationName("Amoxicilline 1g")
                .dosage("1 gélule")
                .frequency("Matin et soir")
                .duration("7 jours")
                .route(MedicationRoute.ORAL)
                .quantity(2)
                .build());

        this.prescriptionId = prescription.getId();
    }

    // ─────────────────────────── Cache miss : rendu + stockage MinIO ───────────────────────────

    @Test
    @DisplayName("1er appel : PDF généré, stocké dans MinIO, contenu textuel valide")
    void firstCall_generatesAndStoresPdf() throws Exception {
        byte[] pdfBytes;
        try (StoredPdf pdf = pdfService.getOrGeneratePdf(prescriptionId)) {
            pdfBytes = pdf.content().readAllBytes();
            assertThat(pdf.contentType()).isEqualTo("application/pdf");
            assertThat(pdf.userMetadata())
                    .containsKey("content-hash")
                    .containsEntry("prescription-number", "ORD-2026-00777");
        }

        // Vérif magic bytes PDF
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");

        // Vérif présence dans le bucket MinIO
        assertThat(countObjectsInBucket()).isEqualTo(1);

        // Vérif extraction texte via PDFBox → contenu critique présent
        String extracted = extractTextFromPdf(pdfBytes);
        assertThat(extracted)
                .contains("ORD-2026-00777")           // numéro d'ordonnance
                .contains("Diop Madiop")              // nom patient
                .contains("Dr Ndiaye Aïssatou")       // nom médecin
                .contains("Paracétamol 500mg")        // médicament 1
                .contains("Amoxicilline 1g")          // médicament 2
                .contains("Médecine générale")        // spécialité
                .contains("ORDONNANCE MÉDICALE");     // titre
    }

    // ─────────────────────────── Cache hit : pas de re-rendu ───────────────────────────

    @Test
    @DisplayName("2e appel identique : cache hit, renderer non rappelé, MinIO contient 1 seul objet")
    void secondCall_isCacheHit() throws Exception {
        // 1er appel
        try (StoredPdf first = pdfService.getOrGeneratePdf(prescriptionId)) {
            first.content().readAllBytes();
        }
        // 2e appel
        try (StoredPdf second = pdfService.getOrGeneratePdf(prescriptionId)) {
            second.content().readAllBytes();
        }

        // Le renderer ne doit avoir été appelé qu'une seule fois
        verify(renderer, times(1)).render(any(PrescriptionPdfModel.class));
        // MinIO contient toujours un seul objet (la 2e écriture aurait été un overwrite
        // mais comme le cache hit court-circuite avant, pas même un putObject)
        assertThat(countObjectsInBucket()).isEqualTo(1);
    }

    // ─────────────────────────── Changement de contenu → nouveau rendu ───────────────────────────

    @Test
    @DisplayName("Modification d'une ligne → nouveau hash → nouveau PDF (ancien préservé)")
    void contentChange_producesNewPdf_keepsOldOne() throws Exception {
        // 1er appel
        try (StoredPdf first = pdfService.getOrGeneratePdf(prescriptionId)) {
            first.content().readAllBytes();
        }
        assertThat(countObjectsInBucket()).isEqualTo(1);

        // Modification d'une ligne médicament (le hash du contenu va changer)
        List<PrescriptionLine> lines = prescriptionLineRepository
                .findByPrescriptionIdOrderByIdAsc(prescriptionId);
        PrescriptionLine first_line = lines.get(0);
        first_line.setDosage("2 comprimés"); // modifié de "1 comprimé"
        prescriptionLineRepository.save(first_line);

        // 2e appel → nouveau rendu, nouvel objet stocké
        try (StoredPdf second = pdfService.getOrGeneratePdf(prescriptionId)) {
            second.content().readAllBytes();
        }

        verify(renderer, times(2)).render(any(PrescriptionPdfModel.class));
        // L'ancien objet est conservé (audit médico-légal) + nouveau objet ajouté
        assertThat(countObjectsInBucket()).isEqualTo(2);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /** Supprime tous les objets du bucket — appelé avant chaque test pour isolation. */
    private void cleanupBucket() throws Exception {
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(storageProperties.getBucket()).build());
        if (!bucketExists) {
            return; // bucket pas encore créé (1er test) — rien à nettoyer
        }
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(storageProperties.getBucket()).recursive(true).build());
        for (Result<Item> r : results) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(r.get().objectName())
                    .build());
        }
    }

    /** Compte les objets actuellement présents dans le bucket configuré. */
    private int countObjectsInBucket() throws Exception {
        // Vérif que le bucket existe (bootstrap réussi)
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(storageProperties.getBucket()).build());
        assertThat(bucketExists).as("bucket auto-créé au démarrage").isTrue();

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(storageProperties.getBucket())
                        .recursive(true)
                        .build());
        List<Item> items = new ArrayList<>();
        for (Result<Item> r : results) {
            items.add(r.get());
        }
        return items.size();
    }

    /** Extrait le texte d'un PDF via Apache PDFBox (déjà transitif via OpenHTMLtoPDF). */
    private static String extractTextFromPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
