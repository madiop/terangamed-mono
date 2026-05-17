package com.terangamed.medical.mapper;

import com.terangamed.medical.dto.AntecedentDto;
import com.terangamed.medical.dto.CreateAntecedentRequest;
import com.terangamed.medical.dto.UpdateAntecedentRequest;
import com.terangamed.medical.entity.Antecedent;
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
public interface AntecedentMapper {

    AntecedentDto toDto(Antecedent entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    Antecedent toEntity(CreateAntecedentRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "medicalRecordId", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateAntecedentRequest request, @MappingTarget Antecedent entity);
}
