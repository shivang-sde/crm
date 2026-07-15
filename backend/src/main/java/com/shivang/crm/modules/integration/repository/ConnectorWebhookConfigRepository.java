package com.shivang.crm.modules.integration.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;

@Repository
public interface ConnectorWebhookConfigRepository extends JpaRepository<ConnectorWebhookConfig, UUID> {
    List<ConnectorWebhookConfig> findByTenantId(UUID tenantId);
}
