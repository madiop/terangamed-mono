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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrateur de la génération du PDF d'une ordonnance.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Charger {@code PrescriptionDto} + consultation + dossier médical (local DB)</li>
 *   <li>Résoudre patient + médecin via Feign ({@code RemoteLookupService} — CB/retry inclus)</li>
 *   <li>Construire le {@link PrescriptionPdfModel} (mapping + QR + logo)</li>
 *   <li>Calculer le {@code contentHash} déterministe → clé d'objet MinIO</li>
 *   <li>Si le PDF existe déjà sous cette clé → cache hit (lecture directe)</li>
 *   <li>Sinon → rendu Thymeleaf+OpenHTMLtoPDF, stockage MinIO, retour du stream</li>
 * </ol>
 *
 * <h3>Idempotence</h3>
 * <p>La clé d'objet inclut le hash → toute modification du contenu visible
 * (lignes, instructions, infos patient/médecin) change la clé → nouveau rendu.
 * Aucune ancienne version n'est écrasée, ce qui préserve l'audit médico-légal.
 *
 * <h3>Sécurité</h3>
 * <p>Ce service ne fait PAS de check d'accès — c'est au contrôleur
 * ({@code PrescriptionController}) de valider via {@code MedicalRecordAccessChecker}
 * <b>avant</b> de l'appeler. Pattern cohérent avec les autres endpoints du service.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PrescriptionPdfService {

    private final PrescriptionService prescriptionService;
    private final ConsultationService consultationService;
    private final MedicalRecordService medicalRecordService;
    private final RemoteLookupService remoteLookupService;
    private final PrescriptionPdfModelMapper modelMapper;
    private final PrescriptionPdfRenderer renderer;
    private final PdfStorageService storage;
    private final ContentHashStrategy hashStrategy;
    private final CurrentUserProvider currentUser;

    /**
     * Récupère un PDF d'ordonnance — depuis le cache si présent, sinon le génère.
     *
     * <p>L'appelant DOIT consommer puis fermer le {@link StoredPdf} (try-with-resources).
     *
     * @param prescriptionId identifiant local de l'ordonnance
     * @return le PDF prêt à streamer (jamais {@code null})
     */
    public StoredPdf getOrGeneratePdf(Long prescriptionId) {
        long start = System.currentTimeMillis();
        log.info("Génération PDF demandée : prescriptionId={}, by={}",
                prescriptionId, currentUser.username());

        // ─── 1. Charge données locales ───
        PrescriptionDto prescription = prescriptionService.findById(prescriptionId);
        Consultation consultation = consultationService.findEntityById(prescription.consultationId());
        MedicalRecord record = medicalRecordService.findEntityById(consultation.getMedicalRecordId());

        // ─── 2. Résolution snapshots Feign (CB/retry gérés par RemoteLookupService) ───
        PatientSnapshotDto patient = remoteLookupService.fetchPatient(record.getPatientId());
        DoctorSnapshotDto doctor = remoteLookupService.fetchDoctor(consultation.getDoctorId());

        // ─── 3. Construction du modèle imprimable ───
        PrescriptionPdfModel model = modelMapper.buildModel(prescription, patient, doctor);

        // ─── 4. Hash + clé d'objet idempotente ───
        String contentHash = hashStrategy.compute(model);
        String objectKey = buildObjectKey(prescription.prescriptionNumber(), contentHash);

        // ─── 5. Cache hit ? ───
        Optional<StoredPdf> cached = storage.retrieve(objectKey);
        if (cached.isPresent()) {
            log.info("PDF cache hit : prescriptionId={}, key={}, durée={} ms",
                    prescriptionId, objectKey, System.currentTimeMillis() - start);
            return cached.get();
        }

        // ─── 6. Cache miss : render + store + return ───
        byte[] pdf = renderer.render(model);
        Map<String, String> metadata = buildMetadata(prescription, consultation, contentHash);
        storage.store(objectKey, pdf, metadata);
        log.info("PDF généré : prescriptionId={}, key={}, taille={} octets, durée={} ms",
                prescriptionId, objectKey, pdf.length, System.currentTimeMillis() - start);

        // Retour direct depuis les bytes en mémoire — évite un second RTT vers MinIO
        return new StoredPdf(
                new ByteArrayInputStream(pdf),
                pdf.length,
                "application/pdf",
                metadata
        );
    }

    /**
     * Construit la clé d'objet : {@code ord/{YYYY}/{prescriptionNumber}/{hash}.pdf}.
     * Partition par année — pratique pour les exports annuels et les politiques de
     * rétention différenciées (archive froide après N années).
     */
    String buildObjectKey(String prescriptionNumber, String contentHash) {
        String year = extractYear(prescriptionNumber);
        return "ord/" + year + "/" + prescriptionNumber + "/" + contentHash + ".pdf";
    }

    /** Extrait l'année du numéro {@code ORD-YYYY-NNNNN}, fallback {@code "unknown"}. */
    private static String extractYear(String prescriptionNumber) {
        if (prescriptionNumber == null) {
            return "unknown";
        }
        // Format : ORD-YYYY-NNNNN — on prend les 4 chars après le premier tiret
        String[] parts = prescriptionNumber.split("-");
        return parts.length >= 3 && parts[1].matches("\\d{4}") ? parts[1] : "unknown";
    }

    /**
     * Métadonnées posées sur l'objet MinIO — utiles pour audit a posteriori
     * sans avoir à parser le PDF.
     *
     * <p>Conservées en {@link LinkedHashMap} pour un ordre stable dans les logs
     * et les tests.
     */
    private Map<String, String> buildMetadata(PrescriptionDto prescription,
                                              Consultation consultation,
                                              String contentHash) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("prescription-id", String.valueOf(prescription.id()));
        meta.put("prescription-number", prescription.prescriptionNumber());
        meta.put("consultation-id", String.valueOf(consultation.getId()));
        meta.put("doctor-id", String.valueOf(consultation.getDoctorId()));
        meta.put("content-hash", contentHash);
        meta.put("rendered-at", Instant.now().toString());
        meta.put("rendered-by", currentUser.username());
        return meta;
    }
}
