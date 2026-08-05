package com.shivang.crm.modules.reminder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.reminder.dto.NotificationResponse;
import com.shivang.crm.modules.reminder.dto.UnreadNotificationCountResponse;
import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.shivang.crm.modules.reminder.entity.UserNotification;
import com.shivang.crm.modules.reminder.mapper.NotificationMapper;
import com.shivang.crm.modules.reminder.repository.UserNotificationRepository;
import com.shivang.crm.shared.exception.NotFoundException;

class NotificationServiceTest {

    private UserNotificationRepository repository;
    private NotificationMapper mapper;
    private TenantContext tenantContext;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserNotificationRepository.class);
        mapper = notification -> NotificationResponse.builder()
            .id(notification.getId())
            .notificationType(notification.getNotificationType())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .referenceType(notification.getReferenceType())
            .referenceId(notification.getReferenceId())
            .read(notification.getIsRead())
            .readAt(notification.getReadAt())
            .createdAt(notification.getCreatedAt())
            .metadata(notification.getMetadata())
            .build();
        tenantContext = mock(TenantContext.class);
        when(tenantContext.getTenantId()).thenReturn(UUID.randomUUID());
        when(tenantContext.getUserId()).thenReturn(UUID.randomUUID());
        service = new NotificationService(repository, mapper, tenantContext);
    }

    @Test
    void listNotificationsReturnsPage() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);

        UserNotification notification = UserNotification.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .userId(userId)
            .notificationType(NotificationType.REMINDER)
            .title("Title")
            .message("Message")
            .isRead(false)
            .createdAt(Instant.now())
            .build();

        when(repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(eq(tenantId), eq(userId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(java.util.List.of(notification)));

        Page<NotificationResponse> page = service.listNotifications(null, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void unreadCountReturnsScopedCount() {
        when(repository.countByTenantIdAndUserIdAndIsReadFalse(tenantContext.getTenantId(), tenantContext.getUserId()))
            .thenReturn(5L);

        UnreadNotificationCountResponse response = service.getUnreadCount();
        assertEquals(5, response.getCount());
    }

    @Test
    void markAsReadUpdatesUnreadAndPreservesReadAtOnRepeat() {
        UUID id = UUID.randomUUID();
        UserNotification notification = UserNotification.builder()
            .id(id)
            .tenantId(tenantContext.getTenantId())
            .userId(tenantContext.getUserId())
            .notificationType(NotificationType.REMINDER)
            .title("Title")
            .message("Message")
            .isRead(false)
            .createdAt(Instant.now())
            .build();

        when(repository.findByIdAndTenantIdAndUserId(id, tenantContext.getTenantId(), tenantContext.getUserId()))
            .thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);

        NotificationResponse first = service.markAsRead(id);
        assertTrue(first.getRead());
        assertNotNull(first.getReadAt());

        Instant firstReadAt = first.getReadAt();
        when(repository.findByIdAndTenantIdAndUserId(id, tenantContext.getTenantId(), tenantContext.getUserId()))
            .thenReturn(Optional.of(notification));

        NotificationResponse second = service.markAsRead(id);
        assertEquals(firstReadAt, second.getReadAt());
    }

    @Test
    void markAsReadThrowsNotFoundForAnotherUserOrTenant() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndUserId(id, tenantContext.getTenantId(), tenantContext.getUserId()))
            .thenReturn(Optional.empty());

        try {
            service.markAsRead(id);
        } catch (NotFoundException ex) {
            assertEquals("Notification not found with identifier: " + id, ex.getMessage());
        }
    }

    @Test
    void markAllAsReadReturnsUpdatedCount() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);

        when(repository.markAllAsRead(eq(tenantId), eq(userId), any(Instant.class)))
            .thenReturn(3);

        assertEquals(3, service.markAllAsRead());
    }
}
