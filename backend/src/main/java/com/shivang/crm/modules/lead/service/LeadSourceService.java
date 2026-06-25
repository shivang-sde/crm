package com.shivang.crm.modules.lead.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.lead.dto.LeadSourceCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadSourceResponse;
import com.shivang.crm.modules.lead.entity.LeadSource;
import com.shivang.crm.modules.lead.mapper.LeadSourceMapper;
import com.shivang.crm.modules.lead.repository.LeadSourceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadSourceService {

    private final LeadSourceRepository leadSourceRepository;
    private final LeadSourceMapper leadSourceMapper;

    /**
     * Create a new lead source
     */
    public LeadSourceResponse createSource(UUID tenantId, LeadSourceCreateRequest request) {
        log.info("Creating lead source for tenant: {}", tenantId);

        LeadSource source = leadSourceMapper.toEntity(request);
        source.setTenantId(tenantId);

        LeadSource savedSource = leadSourceRepository.save(source);
        return leadSourceMapper.toResponse(savedSource);
    }

    /**
     * Get active sources for tenant
     */
    @Transactional(readOnly = true)
    public List<LeadSourceResponse> getActiveSources(UUID tenantId) {
        log.info("Fetching active lead sources for tenant: {}", tenantId);

        List<LeadSource> sources = leadSourceRepository.findActiveSourcesByTenant(tenantId);
        return leadSourceMapper.toResponseList(sources);
    }

    /**
     * Update a source
     */
    public LeadSourceResponse updateSource(UUID id, UUID tenantId, LeadSourceCreateRequest request) {
        log.info("Updating lead source: {} for tenant: {}", id, tenantId);

        LeadSource source = leadSourceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Source not found"));

        if (!source.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to source");
        }

        source.setName(request.getName());
        source.setIsActive(request.getIsActive());

        LeadSource updatedSource = leadSourceRepository.save(source);
        return leadSourceMapper.toResponse(updatedSource);
    }

    /**
     * Delete a source
     */
    public void deleteSource(UUID id, UUID tenantId) {
        log.info("Deleting lead source: {} for tenant: {}", id, tenantId);

        LeadSource source = leadSourceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Source not found"));

        if (!source.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to source");
        }

        leadSourceRepository.delete(source);
    }
}
