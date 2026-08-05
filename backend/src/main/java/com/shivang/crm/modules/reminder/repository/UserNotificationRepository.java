package com.shivang.crm.modules.reminder.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.entity.UserNotification;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    Optional<UserNotification> findByReminderIdAndUserId(UUID reminderId, UUID userId);

    Page<UserNotification> findByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId, Pageable pageable);

    Page<UserNotification> findByTenantIdAndUserIdAndIsReadOrderByCreatedAtDesc(UUID tenantId, UUID userId, Boolean isRead, Pageable pageable);

    Optional<UserNotification> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    long countByTenantIdAndUserIdAndIsReadFalse(UUID tenantId, UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE UserNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.tenantId = :tenantId AND n.userId = :userId AND n.isRead = false")
    int markAllAsRead(
        @Param("tenantId") UUID tenantId,
        @Param("userId") UUID userId,
        @Param("readAt") Instant readAt
    );
}
