package com.shivang.crm.modules.lead.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.lead.dto.LeadStatusCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadStatusResponse;
import com.shivang.crm.modules.lead.entity.LeadStatus;
import com.shivang.crm.modules.lead.mapper.LeadStatusMapper;
import com.shivang.crm.modules.lead.repository.LeadStatusRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadStatusService {

    private final LeadStatusRepository leadStatusRepository;
    private final LeadStatusMapper leadStatusMapper;

    /**
     * Create a new lead status
     */
    public LeadStatusResponse createStatus(UUID tenantId, LeadStatusCreateRequest request) {
        log.info("Creating lead status for tenant: {}", tenantId);

        LeadStatus status = leadStatusMapper.toEntity(request);
        status.setTenantId(tenantId);

        LeadStatus savedStatus = leadStatusRepository.save(status);
        return leadStatusMapper.toResponse(savedStatus);
    }

    /**
     * Get all statuses for a tenant
     */
    @Transactional(readOnly = true)
    public List<LeadStatusResponse> getStatusesByTenant(UUID tenantId) {
        log.info("Fetching lead statuses for tenant: {}", tenantId);

        List<LeadStatus> statuses = leadStatusRepository.findByTenantIdOrderByDisplayOrder(tenantId);
        return leadStatusMapper.toResponseList(statuses);
    }

    /**
     * Get default status for tenant
     */
    @Transactional(readOnly = true)
    public LeadStatusResponse getDefaultStatus(UUID tenantId) {
        LeadStatus status = leadStatusRepository.findDefaultStatusByTenant(tenantId)
            .orElseThrow(() -> new RuntimeException("Default status not configured for tenant"));

        return leadStatusMapper.toResponse(status);
    }

    /**
     * Update a status
     */
    public LeadStatusResponse updateStatus(UUID id, UUID tenantId, LeadStatusCreateRequest request) {
        log.info("Updating lead status: {} for tenant: {}", id, tenantId);

        LeadStatus status = leadStatusRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Status not found"));

        if (!status.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to status");
        }

        status.setName(request.getName());
        status.setColor(request.getColor());
        status.setDisplayOrder(request.getDisplayOrder());
        status.setIsDefault(request.getIsDefault());
        status.setIsClosed(request.getIsClosed());

        LeadStatus updatedStatus = leadStatusRepository.save(status);
        return leadStatusMapper.toResponse(updatedStatus);
    }

    /**
     * Delete a status
     */
    public void deleteStatus(UUID id, UUID tenantId) {
        log.info("Deleting lead status: {} for tenant: {}", id, tenantId);

        LeadStatus status = leadStatusRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Status not found"));

        if (!status.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to status");
        }

        leadStatusRepository.delete(status);
    }
}
