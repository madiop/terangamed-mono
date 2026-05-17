package com.terangamed.appointment.controller;

import com.terangamed.appointment.dto.AppointmentDto;
import com.terangamed.appointment.dto.AppointmentSearchCriteria;
import com.terangamed.appointment.dto.CreateAppointmentRequest;
import com.terangamed.appointment.dto.UpdateAppointmentRequest;
import com.terangamed.appointment.service.AppointmentService;
import com.terangamed.common.exception.ApiError;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.security.SecurityRoles;
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

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointments", description = "Gestion des rendez-vous médicaux")
// La security est appliquée globalement via OpenApiConfig.addSecurityItem(...).
// Pas de @SecurityRequirement(name = ...) ici : référencer un scheme non
// déclaré dans Components.securitySchemes ferait perdre l'attache du Bearer
// par Swagger UI (cf. correctif 401 mai 2026).
public class AppointmentController {

    private final AppointmentService service;

    @GetMapping
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Recherche paginée de RDV")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de RDV"),
            @ApiResponse(responseCode = "400", description = "Tri invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content)
    })
    public PageResponse<AppointmentDto> search(
            @ParameterObject @ModelAttribute AppointmentSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(summary = "Détail d'un RDV")
    public AppointmentDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Crée un RDV",
            description = "Valide l'existence du patient et du médecin (Feign + Resilience4j), " +
                    "vérifie qu'il n'y a pas d'overlap pour le médecin, et persiste avec snapshot des noms."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "RDV créé"),
            @ApiResponse(responseCode = "400", description = "Validation"),
            @ApiResponse(responseCode = "404", description = "Patient ou médecin inconnu"),
            @ApiResponse(responseCode = "409", description = "Overlap, médecin inactif, ou service downstream indisponible")
    })
    public ResponseEntity<AppointmentDto> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @Operation(
            summary = "Replanifie un RDV (partial update)",
            description = "Permet de changer date, durée, motif et notes. patientId et doctorId sont " +
                    "immuables après création (annuler et recréer pour changer)."
    )
    public AppointmentDto update(@PathVariable Long id,
                                 @Valid @RequestBody UpdateAppointmentRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirme un RDV (PLANNED → CONFIRMED)")
    public void confirm(@PathVariable Long id) {
        service.confirm(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marque le RDV comme effectué (CONFIRMED → COMPLETED)")
    public void complete(@PathVariable Long id) {
        service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.HAS_ANY_STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annule un RDV (depuis PLANNED ou CONFIRMED)")
    public void cancel(@PathVariable Long id) {
        service.cancel(id);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_RECEPTIONIST)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marque le patient absent (CONFIRMED → NO_SHOW)")
    public void markNoShow(@PathVariable Long id) {
        service.markNoShow(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Suppression PHYSIQUE — ADMIN uniquement (préférer cancel)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
