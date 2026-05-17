package com.terangamed.medical.controller;

import com.terangamed.common.exception.ApiError;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.CreatePrescriptionRequest;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.dto.UpdatePrescriptionLineRequest;
import com.terangamed.medical.dto.UpdatePrescriptionRequest;
import com.terangamed.medical.pdf.PrescriptionPdfService;
import com.terangamed.medical.pdf.StoredPdf;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.ConsultationService;
import com.terangamed.medical.service.MedicalRecordService;
import com.terangamed.medical.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * Endpoints REST pour les ordonnances.
 *
 * <p>Architecture en sous-ressources : {@code /api/prescriptions/{id}/lines}
 * pour les médicaments. La création d'ordonnance se fait via
 * {@code POST /api/consultations/{cid}/prescription} (côté ConsultationController
 * — non, on l'expose ici pour cohérence REST sur la ressource Prescription).
 *
 * <p>Endpoint PDF actif : {@code GET /api/prescriptions/{id}/pdf} — rend le PDF
 * ordonnance avec cache MinIO idempotent par hash de contenu (cf.
 * {@link com.terangamed.medical.pdf.PrescriptionPdfService}).
 */
@Slf4j
@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
@Tag(name = "Prescriptions", description = "Ordonnances + lignes médicaments")
public class PrescriptionController {

    private final PrescriptionService service;
    private final ConsultationService consultationService;
    private final MedicalRecordService medicalRecordService;
    private final MedicalRecordAccessChecker accessChecker;
    private final PrescriptionPdfService pdfService;

    @PostMapping("/by-consultation/{consultationId}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(
            summary = "Crée une ordonnance pour une consultation",
            description = "Une ordonnance par consultation max. Au moins une ligne médicament requise."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordonnance créée avec ses lignes"),
            @ApiResponse(responseCode = "400", description = "Validation échouée (lignes vides, posologie manquante…)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Consultation inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ordonnance déjà existante pour cette consultation",
                    content = @Content)
    })
    public ResponseEntity<PrescriptionDto> create(
            @PathVariable Long consultationId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionDto created = service.create(consultationId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/prescriptions/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(summary = "Récupère une ordonnance avec ses lignes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordonnance trouvée avec ses lignes"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "PATIENT non concerné par le dossier parent", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance inconnue", content = @Content)
    })
    public PrescriptionDto findById(@PathVariable Long id) {
        PrescriptionDto dto = service.findById(id);
        // Résolution du dossier parent pour le check PATIENT
        var consultation = consultationService.findEntityById(dto.consultationId());
        var record = medicalRecordService.findEntityById(consultation.getMedicalRecordId());
        accessChecker.ensureCanAccess(record);
        return dto;
    }

    @GetMapping("/by-consultation/{consultationId}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(summary = "Récupère l'ordonnance liée à une consultation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordonnance trouvée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "PATIENT non concerné par le dossier parent", content = @Content),
            @ApiResponse(responseCode = "404", description = "Consultation inconnue ou pas d'ordonnance liée",
                    content = @Content)
    })
    public PrescriptionDto findByConsultation(@PathVariable Long consultationId) {
        var consultation = consultationService.findEntityById(consultationId);
        var record = medicalRecordService.findEntityById(consultation.getMedicalRecordId());
        accessChecker.ensureCanAccess(record);
        return service.findByConsultationId(consultationId);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(summary = "Met à jour les méta-données d'une ordonnance (validité, instructions)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordonnance mise à jour"),
            @ApiResponse(responseCode = "400", description = "Validation échouée",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation parente déjà signée — ordonnance immuable",
                    content = @Content)
    })
    public PrescriptionDto update(@PathVariable Long id,
                                  @Valid @RequestBody UpdatePrescriptionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une ordonnance (et ses lignes en cascade)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ordonnance supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation parente signée — suppression interdite",
                    content = @Content)
    })
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ───────────────── Lignes (sous-ressource) ─────────────────

    @PostMapping("/{id}/lines")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(summary = "Ajoute une ligne médicament à une ordonnance")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ligne médicament ajoutée"),
            @ApiResponse(responseCode = "400", description = "Validation échouée (médicament/posologie manquants)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation parente signée — ordonnance immuable",
                    content = @Content)
    })
    public ResponseEntity<PrescriptionLineDto> addLine(
            @PathVariable Long id,
            @Valid @RequestBody CreatePrescriptionLineRequest request) {
        PrescriptionLineDto created = service.addLine(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @Operation(summary = "Met à jour une ligne d'ordonnance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ligne mise à jour"),
            @ApiResponse(responseCode = "400", description = "Validation échouée",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance ou ligne inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation parente signée — ordonnance immuable",
                    content = @Content)
    })
    public PrescriptionLineDto updateLine(
            @PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody UpdatePrescriptionLineRequest request) {
        return service.updateLine(id, lineId, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize(SecurityRoles.HAS_DOCTOR)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une ligne d'ordonnance")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ligne supprimée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle DOCTOR requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Ordonnance ou ligne inconnue", content = @Content),
            @ApiResponse(responseCode = "409", description = "Consultation parente signée — ordonnance immuable",
                    content = @Content)
    })
    public void deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        service.deleteLine(id, lineId);
    }

    // ───────────────── PDF d'ordonnance ─────────────────

    /**
     * Génère (ou récupère depuis le cache MinIO) le PDF d'une ordonnance.
     *
     * <p>Pipeline (cf. {@link com.terangamed.medical.pdf.PrescriptionPdfService}) :
     * <ol>
     *   <li>Vérification d'accès via {@link MedicalRecordAccessChecker}</li>
     *   <li>Résolution patient/médecin via Feign (CB + retry)</li>
     *   <li>Calcul du hash du contenu → clé d'objet idempotente</li>
     *   <li>Cache hit MinIO ? → stream direct. Cache miss ? → rendu + stockage + stream</li>
     * </ol>
     *
     * <p><b>Headers de réponse</b> :
     * <ul>
     *   <li>{@code Content-Type: application/pdf}</li>
     *   <li>{@code Content-Disposition: inline; filename="ORD-YYYY-NNNNN.pdf"} —
     *       ouverture inline (onglet navigateur), le frontend peut forcer en download
     *       via l'attribut {@code download} de la balise {@code <a>}</li>
     *   <li>{@code Cache-Control: private, no-store, must-revalidate} — donnée
     *       médicale sensible, jamais cachée côté proxy/CDN</li>
     *   <li>{@code X-Prescription-Hash: <16-hex>} — hash du contenu pour
     *       diagnostic / déduplication côté frontend si besoin</li>
     * </ul>
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(SecurityRoles.HAS_DOCTOR_ADMIN_OR_PATIENT)
    @Operation(
            summary = "Génère / récupère le PDF d'une ordonnance",
            description = "Retourne le PDF/A-2 de l'ordonnance avec en-tête cabinet, " +
                    "bloc patient/médecin, table des médicaments, zone de signature " +
                    "manuelle et QR de vérification. Les ré-appels avec le même contenu " +
                    "renvoient le PDF cached (idempotence par hash)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "PDF binaire de l'ordonnance",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary",
                                    description = "Flux PDF/A-2 (Apache PDFBox)"))),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403",
                    description = "PATIENT non concerné par le dossier parent",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Ordonnance inconnue",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503",
                    description = "Storage MinIO ou patient/doctor-service indisponible — retry",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<StreamingResponseBody> generatePdf(@PathVariable Long id) {
        // 1. Sécurité : check d'accès AVANT toute génération (économie ressources)
        PrescriptionDto dto = service.findById(id);
        var consultation = consultationService.findEntityById(dto.consultationId());
        var record = medicalRecordService.findEntityById(consultation.getMedicalRecordId());
        accessChecker.ensureCanAccess(record);

        // 2. Récupération ou génération du PDF (cache MinIO derrière)
        StoredPdf storedPdf = pdfService.getOrGeneratePdf(id);

        // 3. Construction de la réponse streaming — le stream sera fermé par
        //    le lambda quand Spring termine d'écrire la réponse (try-with-resources).
        String filename = dto.prescriptionNumber() + ".pdf";
        String contentHash = storedPdf.userMetadata().getOrDefault("content-hash", "");

        StreamingResponseBody body = outputStream -> {
            try (StoredPdf pdf = storedPdf) {
                pdf.content().transferTo(outputStream);
            } catch (IOException e) {
                // Client a fermé la connexion ou erreur réseau — log debug, pas warn
                // (cas fréquent et bénin si l'utilisateur ferme l'onglet).
                log.debug("Stream PDF interrompu pour prescription {} : {}", id, e.getMessage());
                throw e;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", filename);
        // setContentDispositionFormData met "form-data" — on force la bonne disposition :
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
        headers.setCacheControl("private, no-store, must-revalidate");
        headers.set("X-Prescription-Hash", contentHash);
        if (storedPdf.contentLength() > 0) {
            headers.setContentLength(storedPdf.contentLength());
        }

        return ResponseEntity.ok().headers(headers).body(body);
    }
}
