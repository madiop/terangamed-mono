package com.terangamed.medical.service;

import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.AntecedentDto;
import com.terangamed.medical.dto.CreateAntecedentRequest;
import com.terangamed.medical.dto.UpdateAntecedentRequest;
import com.terangamed.medical.entity.Antecedent;
import com.terangamed.medical.entity.AntecedentType;
import com.terangamed.medical.mapper.AntecedentMapper;
import com.terangamed.medical.repository.AntecedentRepository;
import com.terangamed.medical.specification.AntecedentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des antécédents médicaux. Hard-delete autorisé : un antécédent supprimé
 * disparaît du dossier (pas un événement médico-légal comme une consultation).
 *
 * <p>Pour "désactiver" un antécédent sans le perdre, utiliser {@code active=false}.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AntecedentService {

    private final AntecedentRepository repository;
    private final AntecedentMapper mapper;
    private final MedicalRecordService medicalRecordService;

    @Transactional(readOnly = true)
    public AntecedentDto findById(Long id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AntecedentDto> listByMedicalRecord(Long medicalRecordId, AntecedentType typeFilter,
                                                    boolean onlyActive) {
        // Garantit que le dossier existe (et n'est pas soft-deleted)
        medicalRecordService.findEntityById(medicalRecordId);

        Specification<Antecedent> spec = Specification
                .where(AntecedentSpecifications.byMedicalRecordId(medicalRecordId))
                .and(AntecedentSpecifications.byType(typeFilter));
        if (onlyActive) {
            spec = spec.and(AntecedentSpecifications.onlyActive());
        }
        return repository.findAll(spec).stream().map(mapper::toDto).toList();
    }

    public AntecedentDto create(CreateAntecedentRequest request) {
        medicalRecordService.findEntityById(request.medicalRecordId());

        Antecedent entity = mapper.toEntity(request);
        if (entity.getActive() == null) {
            entity.setActive(true); // défaut métier
        }
        Antecedent saved = repository.save(entity);
        log.info("Antécédent créé : id={}, recordId={}, type={}",
                saved.getId(), saved.getMedicalRecordId(), saved.getType());
        return mapper.toDto(saved);
    }

    public AntecedentDto update(Long id, UpdateAntecedentRequest request) {
        Antecedent ant = getOrThrow(id);
        mapper.updateEntity(request, ant);
        Antecedent saved = repository.save(ant);
        log.info("Antécédent mis à jour : id={}", saved.getId());
        return mapper.toDto(saved);
    }

    public void delete(Long id) {
        Antecedent ant = getOrThrow(id);
        repository.delete(ant);
        log.info("Antécédent supprimé : id={}, recordId={}", id, ant.getMedicalRecordId());
    }

    private Antecedent getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedent", id));
    }
}
