package com.shivang.crm.modules.records.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.RecordWebhookDelivery;

public interface RecordWebhookDeliveryRepository extends JpaRepository<RecordWebhookDelivery, UUID> {

    Optional<RecordWebhookDelivery> findByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(
            UUID tenantId, UUID webhookId, String idempotencyKey);

    boolean existsByTenantIdAndWebhookIdAndIdempotencyKeyAndDeletedFalse(
            UUID tenantId, UUID webhookId, String idempotencyKey);
}
