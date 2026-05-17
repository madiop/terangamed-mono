package com.terangamed.medical.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.ConsultationDto;
import com.terangamed.medical.dto.ConsultationSearchCriteria;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.UpdateConsultationRequest;
import com.terangamed.medical.security.CurrentUserProvider;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.ConsultationService;
import com.terangamed.medical.service.MedicalRecordService;
import com.terangamed.medical.service.RemoteLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Endpoints REST de consultations.
 *
 * <p><b>Règles métier critiques</b> :
 * <ul>
 *   <li>Création — DOCTOR uniquement, son ID est résolu via lookup Feign vers
 *       doctor-service ({@code keycloakSubject == jwt.sub}). Aucun header
 *       d'app requis (le workaround V1 {@code X-Doctor-Id} est retiré).</li>
 *   <li>Modification — DOCTOR auteur uniquement (vérif programmatique dans le service)</li>
 *   <li>Signature — DOCTOR auteur uniquement, terminal</li>
 *   <li>Soft-delete — ADMIN uniquement</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
@Tag(name = "Consultations", description = "Consultations médicales — signables (terminal) + soft-delete")
public class ConsultationController {

    private final ConsultationService service;
    private final MedicalRecordService medicalRecordService;
    private final RemoteLookupService remoteLookup;
    private final CurrentUserProvider currentUser;
    private final MedicalRecordAccessChecker accessChecker;

    @GetMapping
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
    @Operation(summary = "Recherche paginée de consultations (filtres dynamiques)")
    public PageResponse<ConsultationDto> search(
            @ParameterObject @ModelAttribute ConsultationSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(summary = "Détails d'une consultation (incluant signes vitaux JSONB)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consultation trouvée"),
            @ApiResponse(responseCode = "403", description = "PATIENT non concerné", content = @Content),
            @ApiResponse(responseCode = "404", description = "Consultation inconnue", content = @Content)
    })
    public ConsultationDto findById(@PathVariable Long id) {
        var consultation = service.findEntityById(id);
        var record = medicalRecordService.findEntityById(consultation.getMedicalRecordId());
        accessChecker.ensureCanAccess(record);
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(
            summary = "Crée une nouvelle consultation",
            description = """
                    Le médecin auteur est résolu depuis le JWT — le claim `sub`
                    est mis en correspondance via Feign avec `doctor.keycloak_subject`.
                    Aucun header d'app n'est requis. Si le compte Keycloak n'est pas
                    lié à un Doctor en base, l'appel échoue en 409 (l'admin doit
                    établir la liaison via `PUT /api/doctors/{id}` avec keycloakSubject).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Consultation créée"),
            @ApiResponse(responseCode = "400", description = "Validation",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "MedicalRecord ou Appointment inconnu",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflit (compte non lié à un Doctor, doctor inactif, RDV déjà consommé)",
                    content = @Content)
    })
    public ResponseEntity<ConsultationDto> create(@Valid @RequestBody CreateConsultationRequest request) {
        Long doctorId = resolveCurrentDoctorId();
        ConsultationDto created = service.create(request, doctorId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(
            summary = "Met à jour partiellement une consultation",
            description = "Réservé au DOCTOR créateur ET tant que la consultation n'est pas signée."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Non auteur de la consultation", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation signée — immuable", content = @Content)
    })
    public ConsultationDto update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateConsultationRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(
            summary = "Signe la consultation (terminal)",
            description = "Réservé au DOCTOR créateur. Une fois signée → consultation immuable."
    )
    public ConsultationDto sign(@PathVariable Long id) {
        return service.sign(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete d'une consultation (ADMIN — pour traçabilité médico-légale)")
    public void softDelete(@PathVariable Long id) {
        service.softDelete(id);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Résout le {@code doctorId} du DOCTOR connecté à partir du claim {@code sub}
     * de son JWT, via lookup Feign vers {@code doctor-service}
     * ({@code GET /api/doctors/by-keycloak-subject/{sub}}).
     *
     * <p>Remplace le workaround V1 où le frontend devait fournir un header
     * {@code X-Doctor-Id} (et où la liaison était implicite).
     *
     * @throws ConflictException si :
     *     <ul>
     *       <li>pas de JWT en contexte (ne devrait pas arriver — endpoint sécurisé)</li>
     *       <li>le claim {@code sub} n'est pas un UUID parsable</li>
     *       <li>le médecin est inactif (RETIRED / ON_LEAVE)</li>
     *     </ul>
     *     ou {@code ResourceNotFoundException} si le compte Keycloak n'est lié
     *     à aucun Doctor (l'admin doit faire la liaison via
     *     {@code PUT /api/doctors/{id}} avec {@code keycloakSubject}).
     */
    private Long resolveCurrentDoctorId() {
        String sub = currentUser.subject();
        if (sub == null || sub.isBlank()) {
            throw new ConflictException("JWT_SUBJECT_MISSING",
                    "Impossible de résoudre le médecin : claim 'sub' absent du JWT");
        }
        UUID keycloakSubject;
        try {
            keycloakSubject = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new ConflictException("JWT_SUBJECT_INVALID",
                    "Le claim 'sub' n'est pas un UUID valide : " + sub);
        }

        DoctorSnapshotDto doctor = remoteLookup.fetchDoctorByKeycloakSubject(keycloakSubject);
        if (!doctor.isActive()) {
            throw new ConflictException("DOCTOR_NOT_ACTIVE",
                    "Médecin " + doctor.id() + " inactif (statut : " + doctor.status() + ")");
        }
        return doctor.id();
    }
}
