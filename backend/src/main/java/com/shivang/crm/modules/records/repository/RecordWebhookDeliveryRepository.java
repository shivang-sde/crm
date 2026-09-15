package com.shivang.crm.modules.records.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;

public interface RecordWebhookDeliveryRepository extends JpaRepository<RecordWebhookDelivery, UUID> {

    Optional<RecordWebhookDelivery> findByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(
            UUID tenantId, UUID webhookId, String idempotencyKey);

    boolean existsByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(
            UUID tenantId, UUID webhookId, String idempotencyKey);

    Optional<RecordWebhookDelivery> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Page<RecordWebhookDelivery> findByTenantIdAndWebhookIdAndDeletedFalse(UUID tenantId, UUID webhookId, Pageable pageable);

    Page<RecordWebhookDelivery> findByTenantIdAndWebhookIdAndStatusAndDeletedFalse(UUID tenantId, UUID webhookId, String status, Pageable pageable);

    @Query("SELECT d FROM RecordWebhookDelivery d WHERE d.tenantId = :tenantId AND d.webhookId = :webhookId AND d.deleted = false ORDER BY d.receivedAt DESC")
    Page<RecordWebhookDelivery> findRecentByTenantAndWebhook(@Param("tenantId") UUID tenantId, @Param("webhookId") UUID webhookId, Pageable pageable);
}
