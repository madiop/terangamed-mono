package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.CreatePrescriptionRequest;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.dto.UpdatePrescriptionLineRequest;
import com.terangamed.medical.dto.UpdatePrescriptionRequest;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.Prescription;
import com.terangamed.medical.entity.PrescriptionLine;
import com.terangamed.medical.event.PrescriptionCreated;
import com.terangamed.medical.mapper.PrescriptionMapper;
import com.terangamed.medical.repository.PrescriptionLineRepository;
import com.terangamed.medical.repository.PrescriptionRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Gestion des ordonnances et de leurs lignes.
 *
 * <h3>Règles métier</h3>
 * <ul>
 *   <li>Ordonnance créée par le DOCTOR auteur de la consultation parente uniquement</li>
 *   <li>Une ordonnance par consultation maximum (UNIQUE en base)</li>
 *   <li>N° {@code ORD-YYYY-NNNNN} généré côté serveur</li>
 *   <li>Validité par défaut = +3 mois</li>
 *   <li>Toute modification (lignes/méta) interdite si la consultation parente est signée</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PrescriptionService {

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final String PREFIX = "ORD-";
    private static final int DEFAULT_VALIDITY_MONTHS = 3;

    private final PrescriptionRepository repository;
    private final PrescriptionLineRepository lineRepository;
    private final PrescriptionMapper mapper;
    private final ConsultationService consultationService;
    private final MedicalRecordService medicalRecordService;
    private final CurrentUserProvider currentUser;
    private final OutboxEventPublisher outboxPublisher;

    @Transactional(readOnly = true)
    public PrescriptionDto findById(Long id) {
        Prescription p = getOrThrow(id);
        List<PrescriptionLine> lines = lineRepository.findByPrescriptionIdOrderByIdAsc(id);
        return mapper.toDto(p, lines);
    }

    @Transactional(readOnly = true)
    public PrescriptionDto findByConsultationId(Long consultationId) {
        Prescription p = repository.findByConsultationId(consultationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune ordonnance pour la consultation " + consultationId));
        List<PrescriptionLine> lines = lineRepository.findByPrescriptionIdOrderByIdAsc(p.getId());
        return mapper.toDto(p, lines);
    }

    /**
     * Crée une ordonnance + ses lignes pour une consultation.
     * <p>Vérifie : consultation existe & active & non signée, user = DOCTOR auteur,
     * pas déjà d'ordonnance pour cette consultation.
     */
    public PrescriptionDto create(Long consultationId, CreatePrescriptionRequest request) {
        Consultation consultation = consultationService.findEntityById(consultationId);
        ensureCurrentUserIsAuthor(consultation);
        ensureConsultationModifiable(consultation);

        if (repository.existsByConsultationId(consultationId)) {
            throw new ConflictException("PRESCRIPTION_EXISTS",
                    "Une ordonnance existe déjà pour la consultation " + consultationId);
        }

        Prescription p = Prescription.builder()
                .prescriptionNumber(generatePrescriptionNumber())
                .consultationId(consultationId)
                .issuedAt(Instant.now())
                .validUntil(request.validUntil() != null
                        ? request.validUntil()
                        : LocalDate.now().plusMonths(DEFAULT_VALIDITY_MONTHS))
                .generalInstructions(request.generalInstructions())
                .build();
        Prescription savedPrescription = repository.save(p);

        // Insertion des lignes
        List<PrescriptionLine> savedLines = request.lines().stream()
                .map(line -> {
                    PrescriptionLine pl = mapper.toLineEntity(line);
                    pl.setPrescriptionId(savedPrescription.getId());
                    return lineRepository.save(pl);
                })
                .toList();

        publishCreated(savedPrescription, consultation, savedLines.size());
        log.info("Ordonnance créée : id={}, n°={}, consultationId={}, lignes={}",
                savedPrescription.getId(), savedPrescription.getPrescriptionNumber(),
                consultationId, savedLines.size());
        return mapper.toDto(savedPrescription, savedLines);
    }

    public PrescriptionDto update(Long id, UpdatePrescriptionRequest request) {
        Prescription p = getOrThrow(id);
        ensureLinkedConsultationModifiable(p);
        ensureCurrentUserIsAuthor(consultationService.findEntityById(p.getConsultationId()));

        mapper.updateEntity(request, p);
        Prescription saved = repository.save(p);
        List<PrescriptionLine> lines = lineRepository.findByPrescriptionIdOrderByIdAsc(id);
        return mapper.toDto(saved, lines);
    }

    public void delete(Long id) {
        Prescription p = getOrThrow(id);
        ensureLinkedConsultationModifiable(p);
        ensureCurrentUserIsAuthor(consultationService.findEntityById(p.getConsultationId()));

        // Lignes supprimées en cascade côté SQL — on log pour traçabilité
        repository.delete(p);
        log.info("Ordonnance supprimée : id={}, n°={}", id, p.getPrescriptionNumber());
    }

    // ─────────────────────── Lignes (sous-ressource) ───────────────────────

    public PrescriptionLineDto addLine(Long prescriptionId, CreatePrescriptionLineRequest request) {
        Prescription p = getOrThrow(prescriptionId);
        ensureLinkedConsultationModifiable(p);
        ensureCurrentUserIsAuthor(consultationService.findEntityById(p.getConsultationId()));

        PrescriptionLine line = mapper.toLineEntity(request);
        line.setPrescriptionId(prescriptionId);
        PrescriptionLine saved = lineRepository.save(line);
        log.info("Ligne ajoutée : prescriptionId={}, medication={}",
                prescriptionId, saved.getMedicationName());
        return mapper.toLineDto(saved);
    }

    public PrescriptionLineDto updateLine(Long prescriptionId, Long lineId,
                                          UpdatePrescriptionLineRequest request) {
        Prescription p = getOrThrow(prescriptionId);
        ensureLinkedConsultationModifiable(p);
        ensureCurrentUserIsAuthor(consultationService.findEntityById(p.getConsultationId()));

        PrescriptionLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("PrescriptionLine", lineId));
        if (!Objects.equals(line.getPrescriptionId(), prescriptionId)) {
            throw new ResourceNotFoundException(
                    "PrescriptionLine " + lineId + " n'appartient pas à l'ordonnance " + prescriptionId);
        }
        mapper.updateLineEntity(request, line);
        return mapper.toLineDto(lineRepository.save(line));
    }

    public void deleteLine(Long prescriptionId, Long lineId) {
        Prescription p = getOrThrow(prescriptionId);
        ensureLinkedConsultationModifiable(p);
        ensureCurrentUserIsAuthor(consultationService.findEntityById(p.getConsultationId()));

        PrescriptionLine line = lineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("PrescriptionLine", lineId));
        if (!Objects.equals(line.getPrescriptionId(), prescriptionId)) {
            throw new ResourceNotFoundException(
                    "PrescriptionLine " + lineId + " n'appartient pas à l'ordonnance " + prescriptionId);
        }
        lineRepository.delete(line);
        log.info("Ligne supprimée : id={}, prescriptionId={}", lineId, prescriptionId);
    }

    // ─────────────────────── Helpers privés ───────────────────────

    private Prescription getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", id));
    }

    private void ensureLinkedConsultationModifiable(Prescription p) {
        Consultation c = consultationService.findEntityById(p.getConsultationId());
        if (Boolean.TRUE.equals(c.getSigned())) {
            throw new ConflictException("CONSULTATION_SIGNED",
                    "Consultation parente signée — ordonnance immuable");
        }
    }

    private void ensureConsultationModifiable(Consultation c) {
        if (Boolean.TRUE.equals(c.getSigned())) {
            throw new ConflictException("CONSULTATION_SIGNED",
                    "Consultation " + c.getId() + " signée — création d'ordonnance interdite");
        }
    }

    private void ensureCurrentUserIsAuthor(Consultation c) {
        String currentUsername = currentUser.username();
        if (!Objects.equals(currentUsername, c.getCreatedBy())) {
            throw new ForbiddenException(
                    "Seul le médecin auteur (" + c.getCreatedBy() + ") peut gérer l'ordonnance de cette consultation");
        }
    }

    /** Génère {@code ORD-YYYY-NNNNN} avec sequence par année (pattern doctor-service). */
    String generatePrescriptionNumber() {
        String year = LocalDate.now().format(YEAR_FORMAT);
        int nextSequence = repository.findMaxSequenceForYear(year).orElse(0) + 1;
        String number = "%s%s-%05d".formatted(PREFIX, year, nextSequence);

        if (repository.existsByPrescriptionNumber(number)) {
            log.warn("Conflit n° d'ordonnance : {} — réessayer", number);
            throw new ConflictException("PRESCRIPTION_NUMBER_CONFLICT",
                    "Conflit lors de la génération du n° d'ordonnance");
        }
        return number;
    }

    // ───────────────── Publication d'events (Outbox) ─────────────────

    private void publishCreated(Prescription p, Consultation parentConsultation, int lineCount) {
        // patientId résolu via le dossier (pas de Feign — le record est local)
        MedicalRecord record = medicalRecordService.findEntityById(parentConsultation.getMedicalRecordId());

        PrescriptionCreated event = PrescriptionCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPrescriptionId(p.getId())
                .setPrescriptionNumber(p.getPrescriptionNumber())
                .setConsultationId(p.getConsultationId())
                .setPatientId(record.getPatientId())
                .setDoctorId(parentConsultation.getDoctorId())
                .setIssuedAt(p.getIssuedAt())
                .setValidUntil(p.getValidUntil())
                .setLineCount(lineCount)
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.MEDICAL_EVENTS,
                String.valueOf(p.getId()),
                "Prescription", String.valueOf(p.getId()),
                "prescription.created", event);
    }
}
