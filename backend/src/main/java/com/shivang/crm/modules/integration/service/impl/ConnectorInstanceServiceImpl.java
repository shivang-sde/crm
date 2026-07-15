package com.shivang.crm.modules.integration.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ConnectorInstanceRepository;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.shared.exception.BusinessException;

@Service
public class ConnectorInstanceServiceImpl implements ConnectorInstanceService {

    private final ConnectorInstanceRepository repository;
    private final ProviderDefinitionRepository providerRepository;
    private final TenantRepository tenantRepository;

    public ConnectorInstanceServiceImpl(ConnectorInstanceRepository repository, ProviderDefinitionRepository providerRepository, TenantRepository tenantRepository) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public ConnectorInstance save(ConnectorInstance connectorInstance) {
        validateTenantScope(connectorInstance.getTenantId(), connectorInstance.getProvider());
        return repository.save(connectorInstance);
    }

    @Override
    @Transactional
    public ConnectorInstance update(ConnectorInstance connectorInstance) {
        if (connectorInstance.getId() == null) {
            throw new BusinessException("INVALID_REQUEST", "Connector instance id is required for update");
        }
        return repository.save(connectorInstance);
    }

    @Override
    @Transactional
    public ConnectorInstance activate(UUID tenantId, UUID connectorInstanceId, boolean active) {
        ConnectorInstance instance = repository.findById(connectorInstanceId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connector instance not found"));
        if (!tenantId.equals(instance.getTenantId())) {
            throw new BusinessException("FORBIDDEN", "Cannot access connector instance from another tenant");
        }
        instance.setIsActive(active);
        return repository.save(instance);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorInstance> findById(UUID tenantId, UUID id) {
        return repository.findById(id)
            .filter(instance -> tenantId.equals(instance.getTenantId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorInstance> findByProviderKeyAndTenantSlug(String providerKey, String tenantSlug) {
        if (providerKey == null || providerKey.isBlank() || tenantSlug == null || tenantSlug.isBlank()) return Optional.empty();
        Optional<Tenant> tenantOpt = tenantRepository.findBySlug(tenantSlug);
        if (tenantOpt.isEmpty()) return Optional.empty();
        Tenant tenant = tenantOpt.get();
        return repository.findByTenantId(tenant.getId()).stream()
            .filter(instance -> Boolean.TRUE.equals(instance.getIsActive()))
            .filter(instance -> instance.getProvider() != null)
            .filter(instance -> providerKey.equals(instance.getProvider().getProviderKey()))
            .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorInstance> findByTenantId(UUID tenantId) {
        return repository.findByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorInstance> findActiveByTenantAndProvider(UUID tenantId, String providerKey) {
        return repository.findByTenantId(tenantId).stream()
            .filter(instance -> Boolean.TRUE.equals(instance.getIsActive()))
            .filter(instance -> instance.getProvider() != null)
            .filter(instance -> providerKey.equals(instance.getProvider().getProviderKey()))
            .findFirst();
    }

    private void validateTenantScope(UUID tenantId, ProviderDefinition provider) {
        if (tenantId == null) {
            throw new BusinessException("INVALID_REQUEST", "Tenant id is required");
        }
        if (provider == null || provider.getId() == null) {
            throw new BusinessException("INVALID_REQUEST", "Provider is required");
        }
        providerRepository.findById(provider.getId())
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
    }
}
