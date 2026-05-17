package com.terangamed.medical.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.AntecedentDto;
import com.terangamed.medical.dto.CreateAntecedentRequest;
import com.terangamed.medical.dto.UpdateAntecedentRequest;
import com.terangamed.medical.entity.AntecedentType;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.AntecedentService;
import com.terangamed.medical.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints REST pour les antécédents médicaux.
 *
 * <p>Lecture autorisée à DOCTOR/ADMIN/PATIENT (filtrage du PATIENT au niveau du
 * dossier parent). Modification réservée à DOCTOR/ADMIN.
 */
@RestController
@RequestMapping("/api/antecedents")
@RequiredArgsConstructor
@Tag(name = "Antecedents", description = "Antécédents médicaux catégorisés (allergies, ATCD, chirurgies…)")
public class AntecedentController {

    private final AntecedentService service;
    private final MedicalRecordService medicalRecordService;
    private final MedicalRecordAccessChecker accessChecker;

    @GetMapping("/by-record/{medicalRecordId}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(summary = "Liste les antécédents d'un dossier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des antécédents (vide si aucun)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "PATIENT non concerné par le dossier", content = @Content),
            @ApiResponse(responseCode = "404", description = "Dossier médical inconnu", content = @Content)
    })
    public List<AntecedentDto> listByRecord(
            @PathVariable Long medicalRecordId,
            @Parameter(description = "Filtre par type — null = tous types")
            @RequestParam(required = false) AntecedentType type,
            @Parameter(description = "true = uniquement actifs (défaut)")
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        var record = medicalRecordService.findEntityById(medicalRecordId);
        accessChecker.ensureCanAccess(record);
        return service.listByMedicalRecord(medicalRecordId, type, onlyActive);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(summary = "Récupère un antécédent par ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Antécédent trouvé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "PATIENT non concerné par le dossier parent", content = @Content),
            @ApiResponse(responseCode = "404", description = "Antécédent inconnu", content = @Content)
    })
    public AntecedentDto findById(@PathVariable Long id) {
        AntecedentDto dto = service.findById(id);
        var record = medicalRecordService.findEntityById(dto.medicalRecordId());
        accessChecker.ensureCanAccess(record);
        return dto;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
    @Operation(summary = "Ajoute un antécédent à un dossier")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Antécédent créé — Location pointe sur la nouvelle ressource"),
            @ApiResponse(responseCode = "400", description = "Validation échouée (type manquant, libellé vide…)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant (DOCTOR/ADMIN requis)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Dossier médical inconnu", content = @Content)
    })
    public ResponseEntity<AntecedentDto> create(@Valid @RequestBody CreateAntecedentRequest request) {
        AntecedentDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/antecedents/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
    @Operation(summary = "Met à jour partiellement un antécédent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Antécédent mis à jour"),
            @ApiResponse(responseCode = "400", description = "Validation échouée",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant", content = @Content),
            @ApiResponse(responseCode = "404", description = "Antécédent inconnu", content = @Content)
    })
    public AntecedentDto update(@PathVariable Long id,
                                @Valid @RequestBody UpdateAntecedentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime un antécédent (hard delete autorisé pour cette ressource)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Antécédent supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant", content = @Content),
            @ApiResponse(responseCode = "404", description = "Antécédent inconnu", content = @Content)
    })
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
