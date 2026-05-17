package com.terangamed.appointment.mapper;

import com.terangamed.appointment.dto.AppointmentDto;
import com.terangamed.appointment.dto.UpdateAppointmentRequest;
import com.terangamed.appointment.entity.Appointment;
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
public interface AppointmentMapper {

    AppointmentDto toDto(Appointment entity);

    /**
     * Update partiel — seuls startTime, durationMinutes, reason, notes sont modifiables.
     * Les autres champs (patientId, doctorId, snapshots, status) ne sont jamais
     * écrasés par cette opération (le service les gère explicitement).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patientId", ignore = true)
    @Mapping(target = "doctorId", ignore = true)
    @Mapping(target = "patientNameSnapshot", ignore = true)
    @Mapping(target = "doctorNameSnapshot", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateAppointmentRequest request, @MappingTarget Appointment entity);
}
