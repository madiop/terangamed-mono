package com.terangamed.patient.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.pagination.SortValidator;
import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.PatientDto;
import com.terangamed.patient.dto.PatientSearchCriteria;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.entity.Patient;
import com.terangamed.patient.entity.PatientStatus;
import com.terangamed.patient.event.PatientArchived;
import com.terangamed.patient.event.PatientCreated;
import com.terangamed.patient.event.PatientUpdated;
import com.terangamed.patient.mapper.PatientMapper;
import com.terangamed.patient.repository.PatientRepository;
import com.terangamed.patient.specification.PatientSpecifications;
import lombok.extern.slf4j.Slf4j;
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
 * Couche métier pour les opérations sur les patients.
 *
 * <p><b>Responsabilités</b> :
 * <ul>
 *   <li>Recherche paginée multi-critères via {@link PatientSpecifications}</li>
 *   <li>Validation des règles métier (unicité email, format MRN)</li>
 *   <li>Génération du n° de dossier au format {@code MR-YYYY-NNNNN}</li>
 *   <li>Gestion des transitions de statut (archivage)</li>
 *   <li>Conversion DTO ↔ Entité via {@link PatientMapper}</li>
 * </ul>
 *
 * <p><b>Transactions</b> : la classe est {@code @Transactional} par défaut (read/write).
 * Les méthodes de lecture sont annotées {@code @Transactional(readOnly = true)} pour
 * permettre à Hibernate d'optimiser (pas de dirty checking, snapshot non requis).
 */
@Slf4j
@Service
@Transactional
public class PatientService {

    /**
     * Whitelist des champs autorisés pour le tri (anti-leak / anti-injection).
     * Toute requête de tri sur un autre champ → HTTP 400 via {@link SortValidator}.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "lastName",
            "firstName",
            "birthDate",
            "createdAt",
            "medicalRecordNumber",
            "status"
    );

    private static final DateTimeFormatter MRN_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final String MRN_PREFIX = "MR-";

    private final PatientRepository repository;
    private final PatientMapper mapper;
    private final OutboxEventPublisher outboxPublisher;

    public PatientService(PatientRepository repository, PatientMapper mapper,
                          OutboxEventPublisher outboxPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.outboxPublisher = outboxPublisher;
    }

    // ───────────────────────── Lecture ─────────────────────────

    /**
     * Recherche paginée avec critères dynamiques (Specifications) et tri whitelisté.
     *
     * @throws com.terangamed.common.exception.BadRequestException si le {@code Pageable}
     *         demande un tri sur un champ non autorisé.
     */
    @Transactional(readOnly = true)
    public PageResponse<PatientDto> search(PatientSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Patient> page = repository.findAll(
                PatientSpecifications.withCriteria(criteria),
                safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public PatientDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PatientDto findByMedicalRecordNumber(String mrn) {
        Patient patient = repository.findByMedicalRecordNumber(mrn)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient with medicalRecordNumber '" + mrn + "' not found"));
        return mapper.toDto(patient);
    }

    // ───────────────────────── Écriture ─────────────────────────

    /**
     * Crée un nouveau patient. Génère un MRN unique au format {@code MR-YYYY-NNNNN}
     * et positionne le statut à {@code ACTIVE}.
     *
     * @throws ConflictException si l'email est déjà utilisé par un autre patient.
     */
    public PatientDto create(CreatePatientRequest request) {
        validateEmailUnique(request.email(), null);

        Patient patient = mapper.toEntity(request);
        patient.setMedicalRecordNumber(generateMedicalRecordNumber());
        patient.setStatus(PatientStatus.ACTIVE);
        normalizeBlankFields(patient);

        Patient saved = repository.save(patient);
        publishCreated(saved);
        log.info("Patient créé : id={}, mrn={}", saved.getId(), saved.getMedicalRecordNumber());
        return mapper.toDto(saved);
    }

    /**
     * Met à jour partiellement un patient. Tout champ {@code null} dans la requête
     * est ignoré ({@link com.terangamed.patient.mapper.PatientMapper}).
     *
     * @throws ResourceNotFoundException si l'identifiant est inconnu
     * @throws ConflictException si le nouvel email entre en collision avec un autre patient
     */
    public PatientDto update(Long id, UpdatePatientRequest request) {
        Patient patient = getOrThrow(id);

        // L'email change réellement uniquement si fourni ET différent de l'actuel
        if (request.email() != null
                && !request.email().equalsIgnoreCase(patient.getEmail())) {
            validateEmailUnique(request.email(), id);
        }

        mapper.updateEntity(request, patient);
        normalizeBlankFields(patient);
        Patient saved = repository.save(patient);
        publishUpdated(saved);
        log.info("Patient mis à jour : id={}, mrn={}", saved.getId(), saved.getMedicalRecordNumber());
        return mapper.toDto(saved);
    }

    /**
     * Normalise les champs string optionnels — convertit {@code ""} en {@code null}.
     *
     * <p><b>Pourquoi ?</b> Le partial unique index {@code uk_patient_email
     * WHERE email IS NOT NULL} (cf. V2 Flyway) tolère plusieurs patients sans
     * email à condition que la valeur soit {@code NULL} et non {@code ""}.
     * Sans cette normalisation, le frontend qui envoie {@code email: ""} pour
     * vider un champ provoquerait une violation de contrainte au save.
     *
     * <p>Appelé après {@code mapper.updateEntity()} dans {@link #update(Long, UpdatePatientRequest)}
     * et après le mapping initial dans {@link #create(CreatePatientRequest)}.
     */
    private void normalizeBlankFields(Patient patient) {
        if (patient.getEmail() != null && patient.getEmail().isBlank()) {
            patient.setEmail(null);
        }
        if (patient.getPhone() != null && patient.getPhone().isBlank()) {
            patient.setPhone(null);
        }
    }

    /**
     * Archive un dossier (soft delete logique). Idempotent : un dossier déjà
     * {@code ARCHIVED} reste {@code ARCHIVED}.
     */
    public void archive(Long id) {
        Patient patient = getOrThrow(id);
        if (patient.getStatus() == PatientStatus.ARCHIVED) {
            log.debug("Patient {} déjà archivé — opération idempotente", id);
            return;
        }
        patient.setStatus(PatientStatus.ARCHIVED);
        Patient saved = repository.save(patient);
        publishArchived(saved);
        log.info("Patient archivé : id={}", id);
    }

    /**
     * Suppression physique. <b>Réservé aux ADMIN</b> — préférer {@link #archive(Long)}
     * pour la majorité des cas d'usage (RGPD, traçabilité médicale).
     */
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Patient", id);
        }
        repository.deleteById(id);
        log.warn("Patient supprimé physiquement : id={}", id);
    }

    // ───────────────────────── Helpers privés ─────────────────────────

    private Patient getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /**
     * Vérifie qu'aucun autre patient n'utilise cet email (case-insensitive).
     * Si {@code excludeId} est fourni, il est exclu du contrôle (utile pour update).
     */
    private void validateEmailUnique(String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<Patient> existing = repository.findByEmailIgnoreCase(email);
        if (existing.isPresent()
                && !Objects.equals(existing.get().getId(), excludeId)) {
            throw new ConflictException("PATIENT_EMAIL_DUPLICATE",
                    "Un patient avec l'email '%s' existe déjà".formatted(email));
        }
    }

    /**
     * Génère le prochain n° de dossier au format {@code MR-YYYY-NNNNN}.
     *
     * <p><b>Algorithme</b> : on cherche le plus grand suffixe numérique pour
     * l'année courante puis on incrémente. La contrainte d'unicité au niveau DB
     * sert de filet de sécurité contre les races conditions très improbables
     * (deux INSERTs simultanés dans la même milliseconde).
     *
     * <p>Visibilité {@code package-private} pour pouvoir être testée directement.
     */
    String generateMedicalRecordNumber() {
        String year = LocalDate.now().format(MRN_YEAR_FORMAT);
        int nextSequence = repository.findMaxSequenceForYear(year).orElse(0) + 1;
        String mrn = "%s%s-%05d".formatted(MRN_PREFIX, year, nextSequence);

        if (repository.existsByMedicalRecordNumber(mrn)) {
            // Race condition extrêmement rare — log + bascule sur ConflictException
            // pour que le client retry. La contrainte UNIQUE en DB protège l'intégrité.
            log.warn("Conflit MRN détecté à la génération : {} — réessayez", mrn);
            throw new ConflictException("MRN_GENERATION_CONFLICT",
                    "Conflit lors de la génération du n° dossier (réessayez)");
        }
        return mrn;
    }

    // ───────────────────────── Publication d'events (Outbox) ─────────────────────────
    //
    // Les events sont insérés dans la même transaction que le save() de l'aggregate
    // — atomicité garantie par OutboxEventPublisher (@Transactional MANDATORY).
    // Si le service plante après save() mais avant la fin du commit, AUCUN event
    // n'est émis (l'insert outbox a aussi été rollback). Si le commit réussit puis
    // le service plante, le scheduler relay reprendra l'event au prochain démarrage.
    //

    private void publishCreated(Patient patient) {
        PatientCreated event = PatientCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPatientId(patient.getId())
                .setMedicalRecordNumber(patient.getMedicalRecordNumber())
                .setLastName(patient.getLastName())
                .setFirstName(patient.getFirstName())
                .setEmail(patient.getEmail())
                .setPhone(patient.getPhone())
                .setKeycloakSubject(currentKeycloakSubject())
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.PATIENT_EVENTS,
                String.valueOf(patient.getId()),
                "Patient", String.valueOf(patient.getId()),
                "patient.created", event);
    }

    private void publishUpdated(Patient patient) {
        PatientUpdated event = PatientUpdated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPatientId(patient.getId())
                .setMedicalRecordNumber(patient.getMedicalRecordNumber())
                .setLastName(patient.getLastName())
                .setFirstName(patient.getFirstName())
                .setEmail(patient.getEmail())
                .setPhone(patient.getPhone())
                .setStatus(patient.getStatus().name())
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.PATIENT_EVENTS,
                String.valueOf(patient.getId()),
                "Patient", String.valueOf(patient.getId()),
                "patient.updated", event);
    }

    private void publishArchived(Patient patient) {
        PatientArchived event = PatientArchived.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setPatientId(patient.getId())
                .setMedicalRecordNumber(patient.getMedicalRecordNumber())
                .setArchivedBy(currentUsername())
                .build();
        outboxPublisher.publish(
                TerangaMedTopics.PATIENT_EVENTS,
                String.valueOf(patient.getId()),
                "Patient", String.valueOf(patient.getId()),
                "patient.archived", event);
    }

    /**
     * Récupère le {@code preferred_username} du JWT — null en contexte sans auth (tests).
     * Encapsulé ici pour éviter de dépendre d'un composant externe (CurrentUserProvider
     * n'est pas dans common-lib — pattern différent par service).
     */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        Object name = jwt.getClaim("preferred_username");
        return name != null ? name.toString() : jwt.getSubject();
    }

    private String currentKeycloakSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt.getSubject();
    }
}
