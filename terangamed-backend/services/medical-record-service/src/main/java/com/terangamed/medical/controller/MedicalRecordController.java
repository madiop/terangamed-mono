package com.terangamed.medical.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.CreateMedicalRecordRequest;
import com.terangamed.medical.dto.MedicalRecordDto;
import com.terangamed.medical.dto.UpdateMedicalRecordRequest;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Endpoints REST de gestion des dossiers médicaux.
 *
 * <p>
 * <b>Autorisations</b> :
 * <ul>
 * <li>Création — RECEPTIONIST ou ADMIN (typiquement à l'enregistrement du
 * patient)</li>
 * <li>Lecture — DOCTOR, ADMIN ou PATIENT (uniquement son dossier — check
 * programmatique)</li>
 * <li>Modification — DOCTOR ou ADMIN</li>
 * <li>Soft-delete — ADMIN uniquement</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/medical-records")
@RequiredArgsConstructor
@Tag(name = "MedicalRecords", description = "Dossiers médicaux des patients (1 par patient)")
public class MedicalRecordController {

        private final MedicalRecordService service;
        private final MedicalRecordAccessChecker accessChecker;

        @PostMapping
        @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_RECEPTIONIST)
        @Operation(summary = "Crée un dossier médical pour un patient", description = "Un seul dossier par patient. Valide l'existence du patient via patient-service.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Dossier créé"),
                        @ApiResponse(responseCode = "400", description = "Validation", content = @Content(schema = @Schema(implementation = ApiError.class))),
                        @ApiResponse(responseCode = "404", description = "Patient inconnu", content = @Content),
                        @ApiResponse(responseCode = "409", description = "Dossier déjà existant pour ce patient", content = @Content)
        })
        public ResponseEntity<MedicalRecordDto> create(@Valid @RequestBody CreateMedicalRecordRequest request) {
                MedicalRecordDto created = service.create(request);
                URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                                .path("/{id}").buildAndExpand(created.id()).toUri();
                return ResponseEntity.created(location).body(created);
        }

        @GetMapping("/{id}")
        @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
        @Operation(summary = "Récupère un dossier médical par ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Dossier trouvé"),
                        @ApiResponse(responseCode = "403", description = "PATIENT tentant d'accéder au dossier d'un autre", content = @Content),
                        @ApiResponse(responseCode = "404", description = "Dossier inconnu ou soft-deleted", content = @Content)
        })
        public MedicalRecordDto findById(@PathVariable Long id) {
                var record = service.findEntityById(id);
                accessChecker.ensureCanAccess(record);
                return service.findById(id);
        }

        @GetMapping("/by-patient/{patientId}")
        @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
        @Operation(summary = "Récupère le dossier médical d'un patient par son patientId")
        public MedicalRecordDto findByPatientId(@PathVariable Long patientId) {
                accessChecker.ensureCanAccessPatient(patientId);
                return service.findByPatientId(patientId);
        }

        @PutMapping("/{id}")
        @PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)
        @Operation(summary = "Met à jour partiellement un dossier médical")
        public MedicalRecordDto update(@PathVariable Long id,
                        @Valid @RequestBody UpdateMedicalRecordRequest request) {
                return service.update(id, request);
        }

        @DeleteMapping("/{id}")
        @PreAuthorize(SecurityRoles.HAS_ADMIN)
        @ResponseStatus(HttpStatus.NO_CONTENT)
        @Operation(summary = "Soft-delete d'un dossier (ADMIN uniquement) — réversible côté DB")
        public void softDelete(@PathVariable Long id) {
                service.softDelete(id);
        }
}
