package com.shivang.crm.modules.reminder.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private UUID id;
    private NotificationType notificationType;
    private String title;
    private String message;
    private String referenceType;
    private UUID referenceId;
    private Boolean read;
    private Instant readAt;
    private Instant createdAt;
    private Map<String, Object> metadata;
}
