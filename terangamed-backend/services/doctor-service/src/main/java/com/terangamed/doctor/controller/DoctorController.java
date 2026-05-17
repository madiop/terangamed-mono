package com.terangamed.doctor.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.DoctorDto;
import com.terangamed.doctor.dto.DoctorSearchCriteria;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.service.DoctorService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * Endpoints REST de gestion des médecins.
 *
 * <p><b>Autorisations</b> :
 * <ul>
 *   <li>Lecture (GET) — tout le staff</li>
 *   <li>Création / update — ADMIN uniquement (un médecin n'est pas créé par un réceptionniste)</li>
 *   <li>Statut (congé / retraite / réactivation) — ADMIN uniquement</li>
 *   <li>Suppression physique — ADMIN uniquement</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Gestion des médecins du cabinet TerangaMed")
// La security est appliquée globalement via OpenApiConfig.addSecurityItem(...).
// Pas de @SecurityRequirement(name = ...) ici : référencer un scheme non
// déclaré dans Components.securitySchemes ferait perdre l'attache du Bearer
// par Swagger UI (cf. correctif 401 mai 2026).
public class DoctorController {

    private final DoctorService service;

    @GetMapping
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Recherche paginée de médecins (tous statuts)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de médecins"),
            @ApiResponse(responseCode = "400", description = "Tri sur champ non whitelisté",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant", content = @Content)
    })
    public PageResponse<DoctorDto> search(
            @ParameterObject @ModelAttribute DoctorSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/active")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Médecins ACTIFS uniquement",
            description = "Pratique pour le frontend de prise de RDV : exclut les médecins en congé et retraités."
    )
    public PageResponse<DoctorDto> searchActive(
            @ParameterObject @ModelAttribute DoctorSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.searchActive(criteria, pageable);
    }

    /**
     * Résout le profil Doctor du DOCTOR connecté depuis le claim {@code sub} de
     * son JWT — remplace le workaround V1 où le frontend cherchait par email et
     * où medical-record-service exigeait un header {@code X-Doctor-Id}.
     *
     * <p>404 si aucun médecin n'est lié à ce compte Keycloak — l'admin doit
     * faire {@code PUT /api/doctors/{id}} avec {@code keycloakSubject} pour
     * établir la liaison.
     */
    @GetMapping("/me")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(
            summary = "Profil médecin du DOCTOR connecté",
            description = "Résolution par le claim `sub` du JWT (mapping keycloak_subject)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil médecin trouvé"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Aucun médecin lié à ce compte Keycloak — contacter l'ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DoctorDto findMe(@AuthenticationPrincipal Jwt jwt) {
        UUID subject = UUID.fromString(jwt.getSubject());
        return service.findByKeycloakSubject(subject);
    }

    /**
     * Endpoint interne — résolution par claim Keycloak sans dépendre du JWT
     * passé en argument. Utilisé par medical-record-service via Feign pour
     * lever le besoin du header {@code X-Doctor-Id} lors de la création d'une
     * consultation. Restreint à tout staff authentifié (le caller est lui-même
     * sécurisé en amont).
     */
    @GetMapping("/by-keycloak-subject/{subject}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Récupère un médecin par son sub Keycloak (lookup inter-services)",
            description = "Appelé par medical-record-service via Feign. Le caller doit être authentifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Médecin trouvé"),
            @ApiResponse(responseCode = "404", description = "Aucun médecin lié", content = @Content)
    })
    public DoctorDto findByKeycloakSubject(@PathVariable UUID subject) {
        return service.findByKeycloakSubject(subject);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Récupère un médecin par son identifiant technique")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Médecin trouvé"),
            @ApiResponse(responseCode = "404", description = "Médecin inconnu", content = @Content)
    })
    public DoctorDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/by-license/{licenseNumber}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Récupère un médecin par son n° d'ordre médical")
    public DoctorDto findByLicense(@PathVariable String licenseNumber) {
        return service.findByLicenseNumber(licenseNumber);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @Operation(
            summary = "Crée un nouveau médecin (ADMIN uniquement)",
            description = "Génère automatiquement un n° d'ordre MED-YYYY-NNNNN. Statut initial : ACTIVE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Médecin créé"),
            @ApiResponse(responseCode = "400", description = "Validation Bean Validation", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email déjà utilisé", content = @Content)
    })
    public ResponseEntity<DoctorDto> create(@Valid @RequestBody CreateDoctorRequest request) {
        DoctorDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @Operation(summary = "Met à jour partiellement un médecin (ADMIN uniquement)")
    public DoctorDto update(@PathVariable Long id,
                            @Valid @RequestBody UpdateDoctorRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Met le médecin en congé (ON_LEAVE)")
    public void putOnLeave(@PathVariable Long id) {
        service.putOnLeave(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Réactive un médecin (ON_LEAVE → ACTIVE)")
    public void reactivate(@PathVariable Long id) {
        service.reactivate(id);
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Acte la retraite d'un médecin (statut terminal)")
    public void retire(@PathVariable Long id) {
        service.retire(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Suppression PHYSIQUE — ADMIN uniquement, irréversible")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
