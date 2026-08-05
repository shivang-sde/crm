package com.shivang.crm.modules.reminder.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.reminder.dto.NotificationResponse;
import com.shivang.crm.modules.reminder.entity.UserNotification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(source = "isRead", target = "read")
    NotificationResponse toResponse(UserNotification notification);
}
