package com.shivang.crm.modules.records.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.records.entity.RecordWebhook;

public interface RecordWebhookRepository extends JpaRepository<RecordWebhook, UUID> {

    Optional<RecordWebhook> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<RecordWebhook> findByTenantIdAndWebhookKeyAndDeletedFalse(UUID tenantId, String webhookKey);

    boolean existsByTenantIdAndWebhookKeyAndDeletedFalse(UUID tenantId, String webhookKey);

    boolean existsByTenantIdAndWebhookKeyAndDeletedFalseAndIdNot(UUID tenantId, String webhookKey, UUID id);

    Page<RecordWebhook> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    Page<RecordWebhook> findByTenantIdAndRecordTypeIdAndDeletedFalse(UUID tenantId, UUID recordTypeId, Pageable pageable);

    Page<RecordWebhook> findByTenantIdAndIsActiveAndDeletedFalse(UUID tenantId, Boolean isActive, Pageable pageable);

    Optional<RecordWebhook> findByWebhookKeyAndDeletedFalseAndIsActiveTrue(String webhookKey);

    java.util.List<RecordWebhook> findByWebhookKeyAndDeletedFalse(String webhookKey);
}
