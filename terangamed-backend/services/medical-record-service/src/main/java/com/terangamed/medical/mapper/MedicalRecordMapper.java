package com.terangamed.medical.mapper;

import com.terangamed.medical.dto.CreateMedicalRecordRequest;
import com.terangamed.medical.dto.MedicalRecordDto;
import com.terangamed.medical.dto.UpdateMedicalRecordRequest;
import com.terangamed.medical.entity.MedicalRecord;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface MedicalRecordMapper {

    MedicalRecordDto toDto(MedicalRecord entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "softDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    MedicalRecord toEntity(CreateMedicalRecordRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patientId", ignore = true)
    @Mapping(target = "softDeleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateMedicalRecordRequest request, @MappingTarget MedicalRecord entity);
}
