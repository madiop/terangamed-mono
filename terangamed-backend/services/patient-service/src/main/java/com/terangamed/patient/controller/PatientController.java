package com.terangamed.patient.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.PatientDto;
import com.terangamed.patient.dto.PatientSearchCriteria;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Endpoints REST de gestion des patients.
 *
 * <p><b>Autorisations</b> (cf. {@link SecurityRoles}) :
 * <ul>
 *   <li>Lecture (GET) + création (POST) + update (PUT) — tout le staff
 *       (ADMIN, DOCTOR, RECEPTIONIST)</li>
 *   <li>Archivage — ADMIN ou RECEPTIONIST (un médecin n'archive pas seul un dossier)</li>
 *   <li>Suppression physique — ADMIN uniquement (cas RGPD ou test)</li>
 * </ul>
 *
 * <p>Tous les endpoints exigent un JWT Keycloak valide. Les refus 401/403 et les
 * erreurs métier (404, 409, 400 validation) sont mappés au format unifié
 * {@link ApiError} par le {@code GlobalExceptionHandler} de {@code common-lib}.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "Patients", description = "Gestion des patients du cabinet TerangaMed")
// La security est appliquée globalement via OpenApiConfig.addSecurityItem(...)
// (schemes "keycloak-password" + "bearer-auth"). NE PAS rajouter ici un
// @SecurityRequirement(name = ...) qui pointerait vers un scheme non déclaré
// dans Components.securitySchemes — Springdoc remplacerait silencieusement la
// security globale par cette ref invalide et Swagger UI cesserait d'envoyer
// le token Bearer sur les opérations de ce controller (cause d'un 401
// systématique observé en mai 2026, cf. correctif).
public class PatientController {

    private final PatientService service;

    // ─────────────────────────── Recherche / Lecture ───────────────────────────

    @GetMapping
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Recherche paginée de patients",
            description = "Retourne une page de patients filtrée par les critères fournis. " +
                    "Tous les paramètres de critères sont optionnels — un paramètre vide est ignoré. " +
                    "Tri autorisé sur : lastName, firstName, birthDate, createdAt, medicalRecordNumber, status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de patients"),
            @ApiResponse(responseCode = "400", description = "Tri sur un champ non whitelisté",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant", content = @Content)
    })
    public PageResponse<PatientDto> search(
            @ParameterObject @ModelAttribute PatientSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Récupère un patient par son identifiant technique")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient trouvé"),
            @ApiResponse(responseCode = "404", description = "Patient inconnu",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant", content = @Content)
    })
    public PatientDto findById(
            @Parameter(description = "Identifiant technique du patient", example = "42")
            @PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-mrn/{mrn}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Récupère un patient par son n° de dossier médical")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient trouvé"),
            @ApiResponse(responseCode = "404", description = "MRN inconnu", content = @Content),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content)
    })
    public PatientDto findByMrn(
            @Parameter(description = "N° de dossier médical au format MR-YYYY-NNNNN", example = "MR-2026-00001")
            @PathVariable String mrn) {
        return service.findByMedicalRecordNumber(mrn);
    }

    // ─────────────────────────── Écriture ───────────────────────────

    @PostMapping
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Crée un nouveau patient",
            description = "Génère automatiquement un n° de dossier MR-YYYY-NNNNN et positionne le statut à ACTIVE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Patient créé — Location pointe vers la ressource"),
            @ApiResponse(responseCode = "400", description = "Données invalides (Bean Validation)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé par un autre patient",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content)
    })
    public ResponseEntity<PatientDto> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Met à jour partiellement un patient",
            description = "Sémantique partial-update : tout champ null dans le payload est ignoré (champ existant préservé)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient mis à jour"),
            @ApiResponse(responseCode = "400", description = "Données invalides", content = @Content),
            @ApiResponse(responseCode = "404", description = "Patient inconnu", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflit email", content = @Content)
    })
    public PatientDto update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePatientRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_RECEPTIONIST)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Archive un dossier patient (idempotent)",
            description = "Passe le statut à ARCHIVED. Si le dossier est déjà archivé, l'opération est sans effet (HTTP 204)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Patient archivé"),
            @ApiResponse(responseCode = "404", description = "Patient inconnu", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé ADMIN/RECEPTIONIST", content = @Content)
    })
    public void archive(@PathVariable Long id) {
        service.archive(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Suppression PHYSIQUE d'un patient — ADMIN uniquement",
            description = "Action irréversible. Préférer l'archivage pour la conformité RGPD/médicale."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Patient supprimé"),
            @ApiResponse(responseCode = "404", description = "Patient inconnu", content = @Content),
            @ApiResponse(responseCode = "403", description = "Réservé ADMIN", content = @Content)
    })
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
