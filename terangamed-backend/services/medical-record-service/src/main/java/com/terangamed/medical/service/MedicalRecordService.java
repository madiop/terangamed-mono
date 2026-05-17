package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.CreateMedicalRecordRequest;
import com.terangamed.medical.dto.MedicalRecordDto;
import com.terangamed.medical.dto.UpdateMedicalRecordRequest;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.mapper.MedicalRecordMapper;
import com.terangamed.medical.repository.MedicalRecordRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service métier pour les dossiers médicaux.
 *
 * <ul>
 *   <li>Création : valide existence du patient via Feign + unicité dossier</li>
 *   <li>Lecture : exclut soft-deleted par défaut (sauf via {@link #findByIdIncludingDeleted})</li>
 *   <li>Modification : update partiel via MapStruct (champs null ignorés)</li>
 *   <li>Suppression : soft-delete uniquement (jamais physique)</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository repository;
    private final MedicalRecordMapper mapper;
    private final RemoteLookupService remoteLookup;
    private final CurrentUserProvider currentUser;

    @Transactional(readOnly = true)
    public MedicalRecordDto findById(Long id) {
        MedicalRecord mr = getActiveOrThrow(id);
        return mapper.toDto(mr);
    }

    @Transactional(readOnly = true)
    public MedicalRecord findEntityById(Long id) {
        return getActiveOrThrow(id);
    }

    @Transactional(readOnly = true)
    public MedicalRecordDto findByPatientId(Long patientId) {
        MedicalRecord mr = repository.findByPatientId(patientId)
                .filter(r -> !Boolean.TRUE.equals(r.getSoftDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun dossier médical actif pour le patient " + patientId));
        return mapper.toDto(mr);
    }

    public MedicalRecordDto create(CreateMedicalRecordRequest request) {
        // Valide l'existence du patient via Feign (lève 404 ou ConflictException)
        remoteLookup.fetchPatient(request.patientId());

        if (repository.existsByPatientId(request.patientId())) {
            throw new ConflictException("MEDICAL_RECORD_DUPLICATE",
                    "Un dossier médical existe déjà pour le patient " + request.patientId());
        }

        MedicalRecord entity = mapper.toEntity(request);
        entity.setSoftDeleted(false);
        MedicalRecord saved = repository.save(entity);
        log.info("Dossier médical créé : id={}, patientId={}", saved.getId(), saved.getPatientId());
        return mapper.toDto(saved);
    }

    public MedicalRecordDto update(Long id, UpdateMedicalRecordRequest request) {
        MedicalRecord mr = getActiveOrThrow(id);
        mapper.updateEntity(request, mr);
        MedicalRecord saved = repository.save(mr);
        log.info("Dossier médical mis à jour : id={}", saved.getId());
        return mapper.toDto(saved);
    }

    /**
     * Soft-delete uniquement — aucun delete physique côté service. Idempotent
     * (si déjà soft-deleted, no-op silencieux).
     */
    public void softDelete(Long id) {
        MedicalRecord mr = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", id));
        if (Boolean.TRUE.equals(mr.getSoftDeleted())) {
            return;
        }
        mr.setSoftDeleted(true);
        mr.setDeletedAt(Instant.now());
        mr.setDeletedBy(currentUser.username());
        repository.save(mr);
        log.warn("Dossier médical soft-deleted : id={}, by={}", id, mr.getDeletedBy());
    }

    // ─────────────────────────── Helpers privés ───────────────────────────

    private MedicalRecord getActiveOrThrow(Long id) {
        MedicalRecord mr = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", id));
        if (Boolean.TRUE.equals(mr.getSoftDeleted())) {
            throw new ResourceNotFoundException(
                    "MedicalRecord " + id + " a été supprimé");
        }
        return mr;
    }
}
