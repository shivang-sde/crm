package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;

@Component
public class DealWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final DealRepository dealRepository;
    private final WorkflowRelatedRecordResolver relatedRecordResolver;

    public DealWorkflowEntityContextProvider(DealRepository dealRepository, WorkflowRelatedRecordResolver relatedRecordResolver) {
        this.dealRepository = dealRepository;
        this.relatedRecordResolver = relatedRecordResolver;
    }

    @Override
    public String entityType() {
        return "DEAL";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return dealRepository.findByIdAndTenantId(entityId, tenantId)
            .filter(deal -> !Boolean.TRUE.equals(deal.getDeleted()))
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Deal deal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", deal.getId());
        context.put("tenantId", deal.getTenantId());
        context.put("ownerId", deal.getOwnerId());
        context.put("name", deal.getName());
        context.put("accountId", deal.getAccountId());
        context.put("contactId", deal.getContactId());
        context.put("leadId", deal.getLeadId());
        context.put("stage", deal.getStageName());
        context.put("stageId", deal.getStage() == null ? null : deal.getStage().getId());
        context.put("recordCategory", deal.getRecordCategory());
        context.put("amount", deal.getAmount());
        context.put("expectedCloseDate", deal.getExpectedCloseDate());
        context.put("probability", deal.getProbability());
        context.put("expectedRevenue", deal.getExpectedRevenue());
        context.put("forecastCategory", deal.getForecastCategory());
        context.put("nextStep", deal.getNextStep());
        context.put("dealType", deal.getDealType());
        context.put("leadSource", deal.getLeadSource());
        context.put("campaignSource", deal.getCampaignSource());
        context.put("closedDate", deal.getClosedDate());
        context.put("wonReason", deal.getWonReason());
        context.put("lostReason", deal.getLostReason());
        context.put("description", deal.getDescription());
        context.put("isWon", deal.isWon());
        context.put("isLost", deal.isLost());
        context.put("isClosed", deal.getRecordCategory() != null
            && deal.getRecordCategory() != com.shivang.crm.modules.deal.entity.RecordCategory.OPEN);
        context.put("createdAt", deal.getCreatedAt());
        context.put("updatedAt", deal.getUpdatedAt());
        context.put("customFields", deal.getCustomData() == null ? Map.of() : deal.getCustomData());
        // Controlled one-hop relationships: Deal → Account / Contact / Lead.
        context.put("account", relatedRecordResolver
            .account(deal.getTenantId(), deal.getAccountId())
            .orElse(null));
        context.put("contact", relatedRecordResolver
            .contact(deal.getTenantId(), deal.getContactId())
            .orElse(null));
        context.put("lead", relatedRecordResolver
            .lead(deal.getTenantId(), deal.getLeadId())
            .orElse(null));
        return context;
    }
}