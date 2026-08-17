package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;

@Component
public class LeadWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final LeadRepository leadRepository;

    public LeadWorkflowEntityContextProvider(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    @Override
    public String entityType() {
        return "LEAD";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return leadRepository.findByIdAndTenantId(entityId, tenantId)
            .filter(lead -> !Boolean.TRUE.equals(lead.getDeleted()))
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Lead lead) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", lead.getId());
        context.put("tenantId", lead.getTenantId());
        context.put("ownerId", lead.getOwnerId());
        context.put("firstName", lead.getFirstName());
        context.put("lastName", lead.getLastName());
        context.put("fullName", lead.getFullName());
        context.put("email", lead.getEmail());
        context.put("phone", lead.getPhone());
        context.put("company", lead.getCompany());
        context.put("source", lead.getSource() == null ? null : lead.getSource().getName());
        context.put("sourceId", lead.getSource() == null ? null : lead.getSource().getId());
        context.put("status", lead.getStatus() == null ? null : lead.getStatus().getName());
        context.put("statusId", lead.getStatus() == null ? null : lead.getStatus().getId());
        context.put("score", lead.getScore());
        context.put("isConverted", lead.getIsConverted());
        context.put("convertedAt", lead.getConvertedAt());
        context.put("convertedAccountId", lead.getConvertedAccountId());
        context.put("convertedContactId", lead.getConvertedContactId());
        context.put("lastContactedAt", lead.getLastContactedAt());
        context.put("createdAt", lead.getCreatedAt());
        context.put("updatedAt", lead.getUpdatedAt());
        context.put("customFields", lead.getCustomData() == null ? Map.of() : lead.getCustomData());
        return context;
    }
}