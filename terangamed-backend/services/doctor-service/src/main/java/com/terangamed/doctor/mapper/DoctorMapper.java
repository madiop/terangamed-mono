package com.terangamed.doctor.mapper;

import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.DoctorDto;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.entity.Doctor;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper MapStruct entre les DTOs et l'entité {@link Doctor}.
 *
 * <p>Champs hérités de {@code BaseAuditEntity} (createdAt, updatedAt, createdBy,
 * updatedBy) — non listés ici car non présents dans {@code Doctor.DoctorBuilder}
 * (Lombok @Builder ne couvre pas les champs hérités). Ils sont peuplés par
 * {@code AuditingEntityListener} et lus via les getters Lombok pour le DTO.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DoctorMapper {

    DoctorDto toDto(Doctor entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "licenseNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    Doctor toEntity(CreateDoctorRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "licenseNumber", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateDoctorRequest request, @MappingTarget Doctor entity);
}
