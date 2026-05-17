package com.terangamed.notification.mapper;

import com.terangamed.notification.dto.NotificationDto;
import com.terangamed.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {
    NotificationDto toDto(Notification entity);
}
