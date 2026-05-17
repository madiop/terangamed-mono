package com.terangamed.medical.mapper;

import com.terangamed.medical.dto.ConsultationDto;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.UpdateConsultationRequest;
import com.terangamed.medical.entity.Consultation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = {VitalSignsMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ConsultationMapper {

    ConsultationDto toDto(Consultation entity);

    /**
     * Construit l'entité depuis la requête de création.
     * <p>{@code doctorId} non mappé : il sera renseigné par le service depuis le JWT.
     * {@code signed/softDeleted} restent à false (état initial).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctorId", ignore = true)
    @Mapping(target = "signed", ignore = true)
    @Mapping(target = "signedAt", ignore = true)
    @Mapping(target = "signedBy", ignore = true)
    @Mapping(target = "softDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Consultation toEntity(CreateConsultationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medicalRecordId", ignore = true)
    @Mapping(target = "doctorId", ignore = true)
    @Mapping(target = "appointmentId", ignore = true)
    @Mapping(target = "signed", ignore = true)
    @Mapping(target = "signedAt", ignore = true)
    @Mapping(target = "signedBy", ignore = true)
    @Mapping(target = "softDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateConsultationRequest request, @MappingTarget Consultation entity);
}
