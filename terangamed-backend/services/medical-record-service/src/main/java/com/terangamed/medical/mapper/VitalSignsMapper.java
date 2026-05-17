package com.terangamed.medical.mapper;

import com.terangamed.medical.dto.VitalSignsDto;
import com.terangamed.medical.entity.VitalSigns;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper VitalSigns ↔ VitalSignsDto.
 * <ul>
 *   <li>{@code toDto} : calcule l'IMC à partir du poids et de la taille</li>
 *   <li>{@code toEntity} : ignore l'IMC entrant (read-only en API)</li>
 * </ul>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VitalSignsMapper {

    @Mapping(target = "bmi", expression = "java(VitalSignsDto.computeBmi(entity.getWeightKg(), entity.getHeightCm()))")
    VitalSignsDto toDto(VitalSigns entity);

    /** L'IMC du DTO entrant est ignoré (calculé côté serveur). */
    VitalSigns toEntity(VitalSignsDto dto);
}
