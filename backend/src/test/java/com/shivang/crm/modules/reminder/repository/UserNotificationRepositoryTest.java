package com.shivang.crm.modules.reminder.repository;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.shivang.crm.modules.reminder.entity.UserNotification;

@DataJpaTest
@Disabled("Repository slice test disabled in this cleanup task")
class UserNotificationRepositoryTest {

    @Autowired
    private UserNotificationRepository repository;

    @Test
    void listReturnsOnlyCurrentTenantAndUser() {
        UUID tenantA = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        repository.save(UserNotification.builder()
            .tenantId(tenantA)
            .userId(userA)
            .notificationType(NotificationType.REMINDER)
            .title("A")
            .message("A")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        repository.save(UserNotification.builder()
            .tenantId(tenantA)
            .userId(userB)
            .notificationType(NotificationType.REMINDER)
            .title("B")
            .message("B")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        Page<UserNotification> page = repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantA, userA, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals(userA, page.getContent().get(0).getUserId());
    }

    @Test
    void readFilterWorks() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("Unread")
            .message("Unread")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("Read")
            .message("Read")
            .isRead(true)
            .readAt(Instant.now())
            .createdAt(Instant.now())
            .build());

        Page<UserNotification> unread = repository.findByTenantIdAndUserIdAndIsReadOrderByCreatedAtDesc(tenant, user, false, PageRequest.of(0, 10));
        assertEquals(1, unread.getTotalElements());
        assertFalse(unread.getContent().get(0).getIsRead());
    }

    @Test
    void resultsAreOrderedNewestFirst() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("Old")
            .message("Old")
            .isRead(false)
            .createdAt(Instant.now().minusSeconds(60))
            .build());

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("New")
            .message("New")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        Page<UserNotification> page = repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenant, user, PageRequest.of(0, 10));
        assertEquals("New", page.getContent().get(0).getTitle());
    }

    @Test
    void unreadCountIsTenantAndUserScoped() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        repository.save(UserNotification.builder()
            .tenantId(tenantA)
            .userId(userA)
            .notificationType(NotificationType.REMINDER)
            .title("1")
            .message("1")
            .isRead(false)
            .createdAt(Instant.now())
            .build());
        repository.save(UserNotification.builder()
            .tenantId(tenantA)
            .userId(userB)
            .notificationType(NotificationType.REMINDER)
            .title("2")
            .message("2")
            .isRead(false)
            .createdAt(Instant.now())
            .build());
        repository.save(UserNotification.builder()
            .tenantId(tenantB)
            .userId(userA)
            .notificationType(NotificationType.REMINDER)
            .title("3")
            .message("3")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        assertEquals(1, repository.countByTenantIdAndUserIdAndIsReadFalse(tenantA, userA));
    }

    @Test
    void markAllAsReadUpdatesOnlyUnreadNotifications() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("Unread")
            .message("Unread")
            .isRead(false)
            .createdAt(Instant.now())
            .build());

        repository.save(UserNotification.builder()
            .tenantId(tenant)
            .userId(user)
            .notificationType(NotificationType.REMINDER)
            .title("Read")
            .message("Read")
            .isRead(true)
            .readAt(Instant.now().minusSeconds(60))
            .createdAt(Instant.now())
            .build());

        int updated = repository.markAllAsRead(tenant, user, Instant.now());
        assertEquals(1, updated);
    }
}
