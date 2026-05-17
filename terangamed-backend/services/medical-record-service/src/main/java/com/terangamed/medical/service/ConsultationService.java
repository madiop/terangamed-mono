package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.pagination.SortValidator;
import com.terangamed.medical.dto.AppointmentSnapshotDto;
import com.terangamed.medical.dto.ConsultationDto;
import com.terangamed.medical.dto.ConsultationSearchCriteria;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.UpdateConsultationRequest;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.event.ConsultationCreated;
import com.terangamed.medical.event.ConsultationSigned;
import com.terangamed.medical.mapper.ConsultationMapper;
import com.terangamed.medical.repository.ConsultationRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import com.terangamed.medical.specification.ConsultationSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Couche métier des consultations.
 *
 * <h3>Règles métier critiques</h3>
 * <ul>
 *   <li><b>Création</b> : seul le DOCTOR connecté peut créer ; son ID est résolu
 *       via la chaîne (pour cette V1, il est passé par {@code doctorId} déduit
 *       de l'utilisateur — on demandera au doctor-service via username).</li>
 *   <li><b>Modification</b> : DOCTOR créateur uniquement (pas même un ADMIN —
 *       conformité médico-légale validée par le client) ET {@code signed=false}.</li>
 *   <li><b>Signature</b> : DOCTOR créateur uniquement → {@code signed=true} terminal.</li>
 *   <li><b>Suppression</b> : ADMIN uniquement → soft-delete.</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ConsultationService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "consultationDate", "createdAt", "doctorId", "signed"
    );

    private final ConsultationRepository repository;
    private final ConsultationMapper mapper;
    private final MedicalRecordService medicalRecordService;
    private final RemoteLookupService remoteLookup;
    private final CurrentUserProvider currentUser;
    private final OutboxEventPublisher outboxPublisher;

    @Transactional(readOnly = true)
    public PageResponse<ConsultationDto> search(ConsultationSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Consultation> page = repository.findAll(
                ConsultationSpecifications.withCriteria(criteria), safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public ConsultationDto findById(Long id) {
        return mapper.toDto(getActiveOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Consultation findEntityById(Long id) {
        return getActiveOrThrow(id);
    }

    /**
     * Création d'une consultation.
     *
     * @param request payload de création
     * @param doctorId id du médecin auteur — résolu depuis le JWT par le controller
     */
    public ConsultationDto create(CreateConsultationRequest request, Long doctorId) {
        // 1. Le dossier médical doit exister et ne pas être soft-deleted
        MedicalRecord record = medicalRecordService.findEntityById(request.medicalRecordId());

        // 2. Valider l'existence + statut ACTIVE du médecin
        DoctorSnapshotDto doctor = remoteLookup.fetchDoctor(doctorId);
        if (!doctor.isActive()) {
            throw new ConflictException("DOCTOR_NOT_ACTIVE",
                    "Médecin " + doctorId + " inactif (statut " + doctor.status() + ")");
        }

        // 3. Si appointmentId fourni → valider cohérence (patient + doctor)
        if (request.appointmentId() != null) {
            AppointmentSnapshotDto appt = remoteLookup.fetchAppointment(request.appointmentId());
            if (!Objects.equals(appt.patientId(), record.getPatientId())) {
                throw new ConflictException("APPOINTMENT_PATIENT_MISMATCH",
                        "Le RDV " + request.appointmentId() + " ne correspond pas au patient du dossier");
            }
            if (!Objects.equals(appt.doctorId(), doctorId)) {
                throw new ConflictException("APPOINTMENT_DOCTOR_MISMATCH",
                        "Le RDV " + request.appointmentId() + " n'appartient pas au médecin auteur");
            }
            if (repository.existsByAppointmentId(request.appointmentId())) {
                throw new ConflictException("CONSULTATION_FOR_APPOINTMENT_EXISTS",
                        "Une consultation existe déjà pour le RDV " + request.appointmentId());
            }
        }

        Consultation entity = mapper.toEntity(request);
        entity.setDoctorId(doctorId);
        entity.setSigned(false);
        entity.setSoftDeleted(false);

        Consultation saved = repository.save(entity);
        publishCreated(saved, record.getPatientId());
        log.info("Consultation créée : id={}, recordId={}, doctorId={}, appointmentId={}",
                saved.getId(), saved.getMedicalRecordId(), saved.getDoctorId(), saved.getAppointmentId());
        return mapper.toDto(saved);
    }

    public ConsultationDto update(Long id, UpdateConsultationRequest request) {
        Consultation c = getActiveOrThrow(id);
        ensureModifiable(c);
        ensureCurrentUserIsAuthor(c);

        mapper.updateEntity(request, c);
        Consultation saved = repository.save(c);
        log.info("Consultation mise à jour : id={}", saved.getId());
        return mapper.toDto(saved);
    }

    /**
     * Signe une consultation — terminal. Vérifie que l'utilisateur courant est
     * bien l'auteur. Idempotent (no-op si déjà signée par le même médecin).
     */
    public ConsultationDto sign(Long id) {
        Consultation c = getActiveOrThrow(id);
        ensureCurrentUserIsAuthor(c);

        if (Boolean.TRUE.equals(c.getSigned())) {
            return mapper.toDto(c);
        }
        c.setSigned(true);
        c.setSignedAt(Instant.now());
        c.setSignedBy(currentUser.username());
        Consultation saved = repository.save(c);
        publishSigned(saved);
        log.info("Consultation signée : id={}, by={}", id, c.getSignedBy());
        return mapper.toDto(saved);
    }

    /**
     * Soft-delete (réservé à ADMIN — vérifié au niveau controller).
     */
    public void softDelete(Long id) {
        Consultation c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        if (Boolean.TRUE.equals(c.getSoftDeleted())) {
            return;
        }
        c.setSoftDeleted(true);
        c.setDeletedAt(Instant.now());
        c.setDeletedBy(currentUser.username());
        repository.save(c);
        log.warn("Consultation soft-deleted : id={}, by={}", id, c.getDeletedBy());
    }

    // ───────────────── Helpers de règles métier ─────────────────

    private Consultation getActiveOrThrow(Long id) {
        Consultation c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        if (Boolean.TRUE.equals(c.getSoftDeleted())) {
            throw new ResourceNotFoundException("Consultation " + id + " supprimée");
        }
        return c;
    }

    private void ensureModifiable(Consultation c) {
        if (Boolean.TRUE.equals(c.getSigned())) {
            throw new ConflictException("CONSULTATION_SIGNED",
                    "Consultation " + c.getId() + " signée — modification interdite");
        }
    }

    /**
     * Vérifie que le user connecté est bien l'auteur de la consultation
     * (matching sur {@code createdBy = preferred_username}).
     *
     * <p>NB : le {@code createdBy} est rempli par {@code AuditingEntityListener}
     * via {@code JwtAuditorAware} (common-lib) — donc cohérent avec le username
     * exposé par {@link CurrentUserProvider}.
     */
    private void ensureCurrentUserIsAuthor(Consultation c) {
        String currentUsername = currentUser.username();
        if (!Objects.equals(currentUsername, c.getCreatedBy())) {
            throw new ForbiddenException(
                    "Seul le médecin auteur (" + c.getCreatedBy() + ") peut modifier/signer cette consultation");
        }
    }

    // ───────────────── Publication d'events (Outbox) ─────────────────

    private void publishCreated(Consultation c, Long patientId) {
        ConsultationCreated event = ConsultationCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setConsultationId(c.getId())
                .setMedicalRecordId(c.getMedicalRecordId())
                .setPatientId(patientId)
                .setDoctorId(c.getDoctorId())
                .setAppointmentId(c.getAppointmentId())
                .setConsultationDate(c.getConsultationDate()
                        .atZone(java.time.ZoneOffset.UTC).toInstant())
                .setCreatedBy(currentUser.username())
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.MEDICAL_EVENTS,
                String.valueOf(c.getId()),
                "Consultation", String.valueOf(c.getId()),
                "consultation.created", event);
    }

    private void publishSigned(Consultation c) {
        // Résolution du patientId via le dossier médical (cohérence locale, pas de Feign)
        MedicalRecord record = medicalRecordService.findEntityById(c.getMedicalRecordId());
        ConsultationSigned event = ConsultationSigned.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setConsultationId(c.getId())
                .setMedicalRecordId(c.getMedicalRecordId())
                .setPatientId(record.getPatientId())
                .setDoctorId(c.getDoctorId())
                .setSignedAt(c.getSignedAt())
                .setSignedBy(c.getSignedBy())
                .setAppointmentId(c.getAppointmentId())
                .setDiagnostic(c.getDiagnostic())
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.MEDICAL_EVENTS,
                String.valueOf(c.getId()),
                "Consultation", String.valueOf(c.getId()),
                "consultation.signed", event);
    }
}
