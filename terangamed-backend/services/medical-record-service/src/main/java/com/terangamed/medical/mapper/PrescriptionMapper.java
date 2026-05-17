package com.terangamed.medical.mapper;

import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.dto.UpdatePrescriptionLineRequest;
import com.terangamed.medical.dto.UpdatePrescriptionRequest;
import com.terangamed.medical.entity.Prescription;
import com.terangamed.medical.entity.PrescriptionLine;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PrescriptionMapper {

    /**
     * Conversion Prescription → DTO. Les lignes sont fournies séparément (pas
     * de relation JPA loadée) — voir {@link #toDto(Prescription, List)}.
     */
    @Mapping(target = "lines", ignore = true)
    PrescriptionDto toDto(Prescription entity);

    default PrescriptionDto toDto(Prescription entity, List<PrescriptionLine> lines) {
        return new PrescriptionDto(
                entity.getId(),
                entity.getPrescriptionNumber(),
                entity.getConsultationId(),
                entity.getIssuedAt(),
                entity.getValidUntil(),
                entity.getGeneralInstructions(),
                lines == null ? List.of() : lines.stream().map(this::toLineDto).toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getVersion()
        );
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "prescriptionNumber", ignore = true)
    @Mapping(target = "consultationId", ignore = true)
    @Mapping(target = "issuedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdatePrescriptionRequest request, @MappingTarget Prescription entity);

    // ─────────────── PrescriptionLine ───────────────

    PrescriptionLineDto toLineDto(PrescriptionLine entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "prescriptionId", ignore = true)
    @Mapping(target = "version", ignore = true)
    PrescriptionLine toLineEntity(CreatePrescriptionLineRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "prescriptionId", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateLineEntity(UpdatePrescriptionLineRequest request, @MappingTarget PrescriptionLine entity);
}
