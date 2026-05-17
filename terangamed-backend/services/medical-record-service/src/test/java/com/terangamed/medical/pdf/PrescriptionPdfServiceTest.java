package com.terangamed.medical.pdf;

import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.security.CurrentUserProvider;
import com.terangamed.medical.service.ConsultationService;
import com.terangamed.medical.service.MedicalRecordService;
import com.terangamed.medical.service.PrescriptionService;
import com.terangamed.medical.service.RemoteLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de l'orchestrateur {@link PrescriptionPdfService}.
 *
 * <p>Tous les collaborateurs sont mockés — on vérifie le flux et la composition
 * des appels (cache miss → render+store, cache hit → pas de render), la
 * construction de la clé d'objet et le contenu des métadonnées.
 */
@ExtendWith(MockitoExtension.class)
// LENIENT : le setUp() prépare des stubs partagés (fixtures) consommés par certains
// tests seulement. Les tests "buildObjectKey_*" sont des helpers utilitaires purs
// qui n'invoquent pas le pipeline complet — sans LENIENT, Mockito 4+ lève
// UnnecessaryStubbing sur eux. Pattern courant pour ce type de test-fixture partagée.
@MockitoSettings(strictness = Strictness.LENIENT)
class PrescriptionPdfServiceTest {

    @Mock PrescriptionService prescriptionService;
    @Mock ConsultationService consultationService;
    @Mock MedicalRecordService medicalRecordService;
    @Mock RemoteLookupService remoteLookupService;
    @Mock PrescriptionPdfModelMapper modelMapper;
    @Mock PrescriptionPdfRenderer renderer;
    @Mock PdfStorageService storage;
    @Mock ContentHashStrategy hashStrategy;
    @Mock CurrentUserProvider currentUser;

    private PrescriptionPdfService service;

    private PrescriptionDto prescription;
    private Consultation consultation;
    private MedicalRecord record;
    private PrescriptionPdfModel model;

    @BeforeEach
    void setUp() {
        service = new PrescriptionPdfService(prescriptionService, consultationService,
                medicalRecordService, remoteLookupService, modelMapper, renderer,
                storage, hashStrategy, currentUser);

        prescription = new PrescriptionDto(
                42L, "ORD-2026-00042", 100L, Instant.parse("2026-05-12T14:35:00Z"),
                LocalDate.of(2026, 8, 12), "", List.of(),
                Instant.now(), Instant.now(), "dr-diop", "dr-diop", 0L
        );
        consultation = new Consultation();
        consultation.setId(100L);
        consultation.setMedicalRecordId(7L);
        consultation.setDoctorId(500L);

        record = new MedicalRecord();
        record.setId(7L);
        record.setPatientId(1234L);

        model = PrescriptionPdfModel.minimalForTest("ORD-2026-00042");

        when(prescriptionService.findById(42L)).thenReturn(prescription);
        when(consultationService.findEntityById(100L)).thenReturn(consultation);
        when(medicalRecordService.findEntityById(7L)).thenReturn(record);
        when(remoteLookupService.fetchPatient(1234L)).thenReturn(samplePatient());
        when(remoteLookupService.fetchDoctor(500L)).thenReturn(sampleDoctor());
        when(modelMapper.buildModel(any(), any(), any())).thenReturn(model);
        when(hashStrategy.compute(model)).thenReturn("abc123def4567890");
        when(currentUser.username()).thenReturn("dr-diop");
    }

    // ─────────────────────────── Cache miss ───────────────────────────

    @Test
    @DisplayName("Cache MISS → renderer + storage.store appelés")
    void cacheMiss_rendersAndStores() {
        when(storage.retrieve(anyString())).thenReturn(Optional.empty());
        byte[] pdf = "%PDF-1.4 fake".getBytes();
        when(renderer.render(model)).thenReturn(pdf);

        StoredPdf result = service.getOrGeneratePdf(42L);

        assertThat(result).isNotNull();
        assertThat(result.contentLength()).isEqualTo(pdf.length);
        assertThat(result.contentType()).isEqualTo("application/pdf");
        verify(renderer).render(model);
        verify(storage).store(eqKey(), any(), any());
    }

    @Test
    @DisplayName("Cache MISS → métadonnées posées sur l'objet stocké")
    void cacheMiss_metadataIsPosted() {
        when(storage.retrieve(anyString())).thenReturn(Optional.empty());
        when(renderer.render(model)).thenReturn("%PDF".getBytes());

        service.getOrGeneratePdf(42L);

        ArgumentCaptor<Map<String, String>> metaCaptor = metadataCaptor();
        verify(storage).store(eqKey(), any(byte[].class), metaCaptor.capture());
        Map<String, String> meta = metaCaptor.getValue();
        assertThat(meta)
                .containsEntry("prescription-id", "42")
                .containsEntry("prescription-number", "ORD-2026-00042")
                .containsEntry("consultation-id", "100")
                .containsEntry("doctor-id", "500")
                .containsEntry("content-hash", "abc123def4567890")
                .containsEntry("rendered-by", "dr-diop")
                .containsKey("rendered-at");
    }

    // ─────────────────────────── Cache hit ───────────────────────────

    @Test
    @DisplayName("Cache HIT → ni renderer ni store appelés, retour direct")
    void cacheHit_returnsWithoutRenderingNorStoring() {
        StoredPdf cached = new StoredPdf(
                new java.io.ByteArrayInputStream("%PDF-cached".getBytes()),
                10L, "application/pdf", Map.of("content-hash", "abc123def4567890")
        );
        when(storage.retrieve(anyString())).thenReturn(Optional.of(cached));

        StoredPdf result = service.getOrGeneratePdf(42L);

        assertThat(result).isSameAs(cached);
        verify(renderer, never()).render(any());
        verify(storage, never()).store(anyString(), any(byte[].class), any());
    }

    // ─────────────────────────── Clé d'objet ───────────────────────────

    @Test
    @DisplayName("buildObjectKey : format ord/{YYYY}/{number}/{hash}.pdf")
    void buildObjectKey_correctFormat() {
        String key = service.buildObjectKey("ORD-2026-00042", "abc123def4567890");
        assertThat(key).isEqualTo("ord/2026/ORD-2026-00042/abc123def4567890.pdf");
    }

    @Test
    @DisplayName("buildObjectKey : numéro mal formé → year='unknown'")
    void buildObjectKey_malformedNumber_unknownYear() {
        String key = service.buildObjectKey("BAD", "hash");
        assertThat(key).isEqualTo("ord/unknown/BAD/hash.pdf");
    }

    @Test
    @DisplayName("buildObjectKey : numéro null → year='unknown'")
    void buildObjectKey_nullNumber_unknownYear() {
        String key = service.buildObjectKey(null, "hash");
        assertThat(key).isEqualTo("ord/unknown/null/hash.pdf");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static String eqKey() {
        return org.mockito.ArgumentMatchers.eq("ord/2026/ORD-2026-00042/abc123def4567890.pdf");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, String>> metadataCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private static PatientSnapshotDto samplePatient() {
        return new PatientSnapshotDto(1234L, "MR-2026-00007",
                "Diop", "Madiop", "madiop@test.sn", "ACTIVE", "sub-1234");
    }

    private static DoctorSnapshotDto sampleDoctor() {
        return new DoctorSnapshotDto(500L, "RPPS-12345", "Ndiaye", "Aïssatou",
                "Médecine générale", "ACTIVE");
    }
}
