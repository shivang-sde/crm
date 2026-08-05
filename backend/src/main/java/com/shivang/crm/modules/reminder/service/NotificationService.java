package com.shivang.crm.modules.reminder.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.reminder.dto.NotificationResponse;
import com.shivang.crm.modules.reminder.dto.UnreadNotificationCountResponse;
import com.shivang.crm.modules.reminder.mapper.NotificationMapper;
import com.shivang.crm.modules.reminder.repository.UserNotificationRepository;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.modules.reminder.entity.UserNotification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserNotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(Boolean read, Pageable pageable) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();

        return (read == null
            ? notificationRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId, pageable)
            : notificationRepository.findByTenantIdAndUserIdAndIsReadOrderByCreatedAtDesc(tenantId, userId, read, pageable))
            .map(notificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount() {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        long count = notificationRepository.countByTenantIdAndUserIdAndIsReadFalse(tenantId, userId);
        return new UnreadNotificationCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();

        UserNotification notification = notificationRepository
            .findByIdAndTenantIdAndUserId(notificationId, tenantId, userId)
            .orElseThrow(() -> new NotFoundException("Notification", notificationId.toString()));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public long markAllAsRead() {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        Instant now = Instant.now();
        return notificationRepository.markAllAsRead(tenantId, userId, now);
    }
}
