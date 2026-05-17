package com.terangamed.doctor.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.finance.FinanceProperties;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.pagination.SortValidator;
import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.DoctorDto;
import com.terangamed.doctor.dto.DoctorSearchCriteria;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.event.DoctorCreated;
import com.terangamed.doctor.event.DoctorStatusChanged;
import com.terangamed.doctor.event.DoctorUpdated;
import com.terangamed.doctor.mapper.DoctorMapper;
import com.terangamed.doctor.repository.DoctorRepository;
import com.terangamed.doctor.specification.DoctorSpecifications;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Couche métier pour les opérations sur les médecins.
 *
 * <p>Mêmes patterns que {@code PatientService} :
 * <ul>
 *   <li>Recherche paginée via Specifications + tri whitelisté</li>
 *   <li>Génération du n° d'ordre médical {@code MED-YYYY-NNNNN}</li>
 *   <li>Validation unicité email</li>
 *   <li>Statut initial ACTIVE à la création</li>
 *   <li>Endpoint dédié pour le changement de statut (mise en congé / retraite)</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
public class DoctorService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "lastName", "firstName", "specialty", "yearsOfExperience",
            "consultationFee", "createdAt", "licenseNumber", "status"
    );

    private static final DateTimeFormatter LICENSE_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final String LICENSE_PREFIX = "MED-";

    private final DoctorRepository repository;
    private final DoctorMapper mapper;
    private final FinanceProperties finance;
    private final OutboxEventPublisher outboxPublisher;

    public DoctorService(DoctorRepository repository, DoctorMapper mapper,
                         FinanceProperties finance,
                         OutboxEventPublisher outboxPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.finance = finance;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorDto> search(DoctorSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Doctor> page = repository.findAll(DoctorSpecifications.withCriteria(criteria), safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorDto> searchActive(DoctorSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Doctor> page = repository.findAll(
                DoctorSpecifications.withCriteria(criteria).and(DoctorSpecifications.onlyActive()),
                safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public DoctorDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public DoctorDto findByLicenseNumber(String licenseNumber) {
        Doctor doctor = repository.findByLicenseNumber(licenseNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor with licenseNumber '" + licenseNumber + "' not found"));
        return mapper.toDto(doctor);
    }

    /**
     * Résout le profil médecin du DOCTOR connecté (claim {@code sub} du JWT
     * passé en argument). Utilisé par {@code GET /api/doctors/me}.
     *
     * @throws ResourceNotFoundException si aucun médecin n'est lié à ce sub —
     *         cas du compte Keycloak DOCTOR créé sans correspondance Doctor
     *         (l'admin doit faire un PUT avec keycloakSubject pour la liaison).
     */
    @Transactional(readOnly = true)
    public DoctorDto findByKeycloakSubject(UUID keycloakSubject) {
        Doctor doctor = repository.findByKeycloakSubject(keycloakSubject)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun médecin lié au compte Keycloak '" + keycloakSubject + "'"));
        return mapper.toDto(doctor);
    }

    public DoctorDto create(CreateDoctorRequest request) {
        validateEmailUnique(request.email(), null);
        validateKeycloakSubjectUnique(request.keycloakSubject(), null);

        Doctor doctor = mapper.toEntity(request);
        doctor.setLicenseNumber(generateLicenseNumber());
        doctor.setStatus(DoctorStatus.ACTIVE);

        // Cohérence devise/montant : si un tarif est fourni mais pas la devise,
        // on applique la devise par défaut de l'instance (XOF/FCFA pour le Sénégal).
        if (doctor.getConsultationFee() != null && doctor.getConsultationFeeCurrency() == null) {
            doctor.setConsultationFeeCurrency(finance.getDefaultCurrency());
        }

        normalizeBlankFields(doctor);

        Doctor saved = repository.save(doctor);
        publishCreated(saved);
        log.info("Médecin créé : id={}, license={}, fee={} {}",
                saved.getId(), saved.getLicenseNumber(),
                saved.getConsultationFee(), saved.getConsultationFeeCurrency());
        return mapper.toDto(saved);
    }

    public DoctorDto update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = getOrThrow(id);

        if (request.email() != null
                && !request.email().equalsIgnoreCase(doctor.getEmail())) {
            validateEmailUnique(request.email(), id);
        }
        if (request.keycloakSubject() != null
                && !request.keycloakSubject().equals(doctor.getKeycloakSubject())) {
            validateKeycloakSubjectUnique(request.keycloakSubject(), id);
        }

        mapper.updateEntity(request, doctor);
        normalizeBlankFields(doctor);
        Doctor saved = repository.save(doctor);
        publishUpdated(saved);
        log.info("Médecin mis à jour : id={}, license={}", saved.getId(), saved.getLicenseNumber());
        return mapper.toDto(saved);
    }

    /**
     * Met le médecin en congé (idempotent si déjà ON_LEAVE).
     */
    public void putOnLeave(Long id) {
        Doctor doctor = getOrThrow(id);
        if (doctor.getStatus() == DoctorStatus.ON_LEAVE) {
            return;
        }
        if (doctor.getStatus() == DoctorStatus.RETIRED) {
            throw new ConflictException("DOCTOR_RETIRED",
                    "Impossible de mettre en congé un médecin retraité");
        }
        DoctorStatus previous = doctor.getStatus();
        doctor.setStatus(DoctorStatus.ON_LEAVE);
        Doctor saved = repository.save(doctor);
        publishStatusChanged(saved, previous);
        log.info("Médecin en congé : id={}", id);
    }

    /**
     * Acte la retraite du médecin (terminal — non réversible via cet endpoint).
     */
    public void retire(Long id) {
        Doctor doctor = getOrThrow(id);
        if (doctor.getStatus() == DoctorStatus.RETIRED) {
            return;
        }
        DoctorStatus previous = doctor.getStatus();
        doctor.setStatus(DoctorStatus.RETIRED);
        Doctor saved = repository.save(doctor);
        publishStatusChanged(saved, previous);
        log.info("Médecin retraité : id={}", id);
    }

    /**
     * Réactive un médecin (retour à ACTIVE) depuis n'importe quel état non-ACTIVE.
     *
     * <p>Transitions autorisées :
     * <ul>
     *   <li>{@code ON_LEAVE → ACTIVE} : reprise après congé (cas standard)</li>
     *   <li>{@code RETIRED → ACTIVE} : sortie de retraite (cas exceptionnel —
     *       décision UI 9.7, validée admin)</li>
     * </ul>
     *
     * <p>Idempotent : si le médecin est déjà ACTIVE, l'appel est un no-op
     * (pas d'écriture en base, pas d'événement publié) — aligné avec le
     * comportement de {@link #retire(Long)}.
     */
    public void reactivate(Long id) {
        Doctor doctor = getOrThrow(id);
        if (doctor.getStatus() == DoctorStatus.ACTIVE) {
            return;
        }
        DoctorStatus previous = doctor.getStatus();
        doctor.setStatus(DoctorStatus.ACTIVE);
        Doctor saved = repository.save(doctor);
        publishStatusChanged(saved, previous);
        log.info("Médecin réactivé : id={} (depuis {})", id, previous);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Doctor", id);
        }
        repository.deleteById(id);
        log.warn("Médecin supprimé physiquement : id={}", id);
    }

    // ───────────────────────── Helpers privés ─────────────────────────

    /**
     * Normalise les champs string optionnels — convertit {@code ""} en {@code null}.
     *
     * <p><b>Pourquoi ?</b> Le partial unique index {@code uk_doctor_email
     * WHERE email IS NOT NULL} (cf. V3 Flyway) tolère plusieurs médecins sans
     * email à condition que la valeur soit {@code NULL} et non {@code ""}.
     * Sans cette normalisation, le frontend qui envoie {@code email: ""} pour
     * vider un champ provoquerait une violation de contrainte au save.
     */
    private void normalizeBlankFields(Doctor doctor) {
        if (doctor.getEmail() != null && doctor.getEmail().isBlank()) {
            doctor.setEmail(null);
        }
        if (doctor.getPhone() != null && doctor.getPhone().isBlank()) {
            doctor.setPhone(null);
        }
    }

    private Doctor getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", id));
    }

    private void validateEmailUnique(String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<Doctor> existing = repository.findByEmailIgnoreCase(email);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), excludeId)) {
            throw new ConflictException("DOCTOR_EMAIL_DUPLICATE",
                    "Un médecin avec l'email '%s' existe déjà".formatted(email));
        }
    }

    private void validateKeycloakSubjectUnique(UUID keycloakSubject, Long excludeId) {
        if (keycloakSubject == null) {
            return;
        }
        Optional<Doctor> existing = repository.findByKeycloakSubject(keycloakSubject);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), excludeId)) {
            throw new ConflictException("DOCTOR_KEYCLOAK_SUBJECT_DUPLICATE",
                    "Un médecin est déjà lié au compte Keycloak '%s'".formatted(keycloakSubject));
        }
    }

    /**
     * Génère le prochain n° d'ordre médical au format {@code MED-YYYY-NNNNN}.
     */
    String generateLicenseNumber() {
        String year = LocalDate.now().format(LICENSE_YEAR_FORMAT);
        int nextSequence = repository.findMaxSequenceForYear(year).orElse(0) + 1;
        String licenseNumber = "%s%s-%05d".formatted(LICENSE_PREFIX, year, nextSequence);

        if (repository.existsByLicenseNumber(licenseNumber)) {
            log.warn("Conflit n° d'ordre détecté : {} — réessayez", licenseNumber);
            throw new ConflictException("LICENSE_GENERATION_CONFLICT",
                    "Conflit lors de la génération du n° d'ordre (réessayez)");
        }
        return licenseNumber;
    }

    // ───────────────────────── Publication d'events (Outbox) ─────────────────────────

    private void publishCreated(Doctor d) {
        DoctorCreated event = DoctorCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setDoctorId(d.getId())
                .setLicenseNumber(d.getLicenseNumber())
                .setLastName(d.getLastName())
                .setFirstName(d.getFirstName())
                .setSpecialty(d.getSpecialty().name())
                .setEmail(d.getEmail())
                .setPhone(d.getPhone())
                .build();
        publish(event, d.getId(), "doctor.created");
    }

    private void publishUpdated(Doctor d) {
        DoctorUpdated event = DoctorUpdated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setDoctorId(d.getId())
                .setLicenseNumber(d.getLicenseNumber())
                .setLastName(d.getLastName())
                .setFirstName(d.getFirstName())
                .setSpecialty(d.getSpecialty().name())
                .setEmail(d.getEmail())
                .setPhone(d.getPhone())
                .setStatus(d.getStatus().name())
                .build();
        publish(event, d.getId(), "doctor.updated");
    }

    private void publishStatusChanged(Doctor d, DoctorStatus previousStatus) {
        DoctorStatusChanged event = DoctorStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setDoctorId(d.getId())
                .setLicenseNumber(d.getLicenseNumber())
                .setPreviousStatus(previousStatus.name())
                .setNewStatus(d.getStatus().name())
                .setChangedBy(currentUsername())
                .build();
        publish(event, d.getId(), "doctor.status-changed");
    }

    private void publish(SpecificRecord event, Long doctorId, String eventType) {
        outboxPublisher.publish(
                TerangaMedTopics.DOCTOR_EVENTS,
                String.valueOf(doctorId),
                "Doctor", String.valueOf(doctorId),
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
