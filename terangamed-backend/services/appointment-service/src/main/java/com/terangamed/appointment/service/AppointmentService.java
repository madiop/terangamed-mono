package com.terangamed.appointment.service;

import com.terangamed.appointment.dto.AppointmentDto;
import com.terangamed.appointment.dto.AppointmentSearchCriteria;
import com.terangamed.appointment.dto.CreateAppointmentRequest;
import com.terangamed.appointment.dto.DoctorSnapshotDto;
import com.terangamed.appointment.dto.PatientSnapshotDto;
import com.terangamed.appointment.dto.UpdateAppointmentRequest;
import com.terangamed.appointment.entity.Appointment;
import com.terangamed.appointment.entity.AppointmentStatus;
import com.terangamed.appointment.event.AppointmentCancelled;
import com.terangamed.appointment.event.AppointmentCompleted;
import com.terangamed.appointment.event.AppointmentConfirmed;
import com.terangamed.appointment.event.AppointmentNoShow;
import com.terangamed.appointment.event.AppointmentScheduled;
import com.terangamed.appointment.mapper.AppointmentMapper;
import com.terangamed.appointment.repository.AppointmentRepository;
import com.terangamed.appointment.specification.AppointmentSpecifications;
import com.terangamed.common.exception.BadRequestException;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.pagination.SortValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * Couche métier des rendez-vous.
 *
 * <p>Responsabilités :
 * <ul>
 *   <li>Création avec validation cross-service (patient existe, médecin actif)</li>
 *   <li>Détection d'overlap pour le même médecin</li>
 *   <li>Snapshot des noms patient/médecin (eventual consistency)</li>
 *   <li>Transitions de statut avec validation (state machine)</li>
 *   <li>Recherche paginée multi-critères</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "startTime", "endTime", "createdAt", "status",
            "patientNameSnapshot", "doctorNameSnapshot"
    );

    private final AppointmentRepository repository;
    private final AppointmentMapper mapper;
    private final RemoteLookupService remoteLookup;
    private final OutboxEventPublisher outboxPublisher;

    // ───────────────────────── Lecture ─────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AppointmentDto> search(AppointmentSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Appointment> page = repository.findAll(
                AppointmentSpecifications.withCriteria(criteria), safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public AppointmentDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    // ───────────────────────── Création ─────────────────────────

    public AppointmentDto create(CreateAppointmentRequest request) {
        // 1. Valider l'existence du patient
        PatientSnapshotDto patient = remoteLookup.fetchPatient(request.patientId());

        // 2. Valider l'existence + statut du médecin
        DoctorSnapshotDto doctor = remoteLookup.fetchDoctor(request.doctorId());
        if (!doctor.isActive()) {
            throw new ConflictException("DOCTOR_NOT_ACTIVE",
                    "Le médecin %s n'est pas actif (statut : %s)"
                            .formatted(doctor.fullName(), doctor.status()));
        }

        // 3. Calculer endTime et vérifier l'overlap
        Instant endTime = request.startTime().plus(request.durationMinutes(), ChronoUnit.MINUTES);
        if (repository.existsOverlapping(doctor.id(), request.startTime(), endTime)) {
            throw new ConflictException("APPOINTMENT_OVERLAP",
                    "Le médecin %s a déjà un rendez-vous sur ce créneau".formatted(doctor.fullName()));
        }

        // 4. Construire et persister
        Appointment appointment = Appointment.builder()
                .patientId(patient.id())
                .doctorId(doctor.id())
                .patientNameSnapshot(patient.fullName())
                .doctorNameSnapshot(doctor.fullName())
                .startTime(request.startTime())
                .endTime(endTime)
                .durationMinutes(request.durationMinutes())
                .reason(request.reason())
                .notes(request.notes())
                .status(AppointmentStatus.PLANNED)
                .build();

        Appointment saved = repository.save(appointment);
        publishScheduled(saved);
        log.info("RDV créé : id={}, patient={}, doctor={}, start={}",
                saved.getId(), patient.id(), doctor.id(), saved.getStartTime());
        return mapper.toDto(saved);
    }

    // ───────────────────────── Update / Replanification ─────────────────────────

    public AppointmentDto update(Long id, UpdateAppointmentRequest request) {
        Appointment appointment = getOrThrow(id);

        if (isTerminal(appointment.getStatus())) {
            throw new ConflictException("APPOINTMENT_TERMINAL_STATUS",
                    "Impossible de modifier un RDV en statut " + appointment.getStatus());
        }

        // Si startTime ou durée changent, recalculer endTime + revérifier overlap
        Instant newStart = request.startTime() != null ? request.startTime() : appointment.getStartTime();
        Integer newDuration = request.durationMinutes() != null
                ? request.durationMinutes() : appointment.getDurationMinutes();
        Instant newEnd = newStart.plus(newDuration, ChronoUnit.MINUTES);

        boolean timeOrDurationChanged =
                !newStart.equals(appointment.getStartTime())
                        || !newDuration.equals(appointment.getDurationMinutes());

        if (timeOrDurationChanged
                && repository.existsOverlappingExcluding(
                        appointment.getDoctorId(), newStart, newEnd, id)) {
            throw new ConflictException("APPOINTMENT_OVERLAP",
                    "Le nouveau créneau chevauche un autre RDV du même médecin");
        }

        // Apply partial update via mapper, puis recalcul des champs derived
        mapper.updateEntity(request, appointment);
        appointment.setStartTime(newStart);
        appointment.setEndTime(newEnd);
        appointment.setDurationMinutes(newDuration);

        Appointment saved = repository.save(appointment);
        log.info("RDV replanifié : id={}, newStart={}", id, newStart);
        return mapper.toDto(saved);
    }

    // ───────────────────────── Transitions de statut ─────────────────────────

    public void confirm(Long id) {
        Appointment a = getOrThrow(id);
        requireStatus(a, AppointmentStatus.PLANNED, "confirm");
        a.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = repository.save(a);
        publishConfirmed(saved);
        log.info("RDV confirmé : id={}", id);
    }

    public void complete(Long id) {
        Appointment a = getOrThrow(id);
        requireStatus(a, AppointmentStatus.CONFIRMED, "complete");
        a.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = repository.save(a);
        publishCompleted(saved);
        log.info("RDV complété : id={}", id);
    }

    public void cancel(Long id) {
        Appointment a = getOrThrow(id);
        if (isTerminal(a.getStatus())) {
            throw new ConflictException("APPOINTMENT_TERMINAL_STATUS",
                    "Impossible d'annuler un RDV en statut " + a.getStatus());
        }
        Instant originalStart = a.getStartTime();
        a.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = repository.save(a);
        publishCancelled(saved, originalStart);
        log.info("RDV annulé : id={}", id);
    }

    public void markNoShow(Long id) {
        Appointment a = getOrThrow(id);
        requireStatus(a, AppointmentStatus.CONFIRMED, "no-show");
        a.setStatus(AppointmentStatus.NO_SHOW);
        Appointment saved = repository.save(a);
        publishNoShow(saved);
        log.info("RDV NO_SHOW : id={}", id);
    }

    // ───────────────────────── Suppression ─────────────────────────

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Appointment", id);
        }
        repository.deleteById(id);
        log.warn("RDV supprimé physiquement : id={}", id);
    }

    // ───────────────────────── Helpers privés ─────────────────────────

    private Appointment getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    private static void requireStatus(Appointment a, AppointmentStatus expected, String operation) {
        if (a.getStatus() != expected) {
            throw new BadRequestException("INVALID_STATUS_TRANSITION",
                    "Impossible d'effectuer '%s' depuis le statut %s (attendu : %s)"
                            .formatted(operation, a.getStatus(), expected));
        }
    }

    private static boolean isTerminal(AppointmentStatus status) {
        return status == AppointmentStatus.COMPLETED
                || status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.NO_SHOW;
    }

    // ───────────────────────── Publication d'events (Outbox) ─────────────────────────

    private void publishScheduled(Appointment a) {
        AppointmentScheduled event = AppointmentScheduled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setAppointmentId(a.getId())
                .setPatientId(a.getPatientId())
                .setDoctorId(a.getDoctorId())
                .setPatientNameSnapshot(a.getPatientNameSnapshot())
                .setDoctorNameSnapshot(a.getDoctorNameSnapshot())
                .setStartTime(a.getStartTime())
                .setEndTime(a.getEndTime())
                .setDurationMinutes(a.getDurationMinutes())
                .setReason(a.getReason())
                .build();
        publish(event, a.getId(), "appointment.scheduled");
    }

    private void publishConfirmed(Appointment a) {
        AppointmentConfirmed event = AppointmentConfirmed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setAppointmentId(a.getId())
                .setPatientId(a.getPatientId())
                .setDoctorId(a.getDoctorId())
                .setStartTime(a.getStartTime())
                .setConfirmedBy(currentUsername())
                .build();
        publish(event, a.getId(), "appointment.confirmed");
    }

    private void publishCompleted(Appointment a) {
        AppointmentCompleted event = AppointmentCompleted.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setAppointmentId(a.getId())
                .setPatientId(a.getPatientId())
                .setDoctorId(a.getDoctorId())
                .setCompletedBy(currentUsername())
                .build();
        publish(event, a.getId(), "appointment.completed");
    }

    private void publishCancelled(Appointment a, Instant originalStart) {
        AppointmentCancelled event = AppointmentCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setAppointmentId(a.getId())
                .setPatientId(a.getPatientId())
                .setDoctorId(a.getDoctorId())
                .setOriginalStartTime(originalStart)
                .setCancelledBy(currentUsername())
                .build();
        publish(event, a.getId(), "appointment.cancelled");
    }

    private void publishNoShow(Appointment a) {
        AppointmentNoShow event = AppointmentNoShow.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setAppointmentId(a.getId())
                .setPatientId(a.getPatientId())
                .setDoctorId(a.getDoctorId())
                .setMissedAt(Instant.now())
                .setMarkedBy(currentUsername())
                .build();
        publish(event, a.getId(), "appointment.no-show");
    }

    private void publish(SpecificRecord event, Long appointmentId, String eventType) {
        outboxPublisher.publish(
                TerangaMedTopics.APPOINTMENT_EVENTS,
                String.valueOf(appointmentId),
                "Appointment", String.valueOf(appointmentId),
                eventType, event);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        Object name = jwt.getClaim("preferred_username");
        return name != null ? name.toString() : jwt.getSubject();
    }
}
