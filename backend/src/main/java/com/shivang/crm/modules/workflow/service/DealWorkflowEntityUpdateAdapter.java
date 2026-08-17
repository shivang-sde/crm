package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.deal.dto.DealUpdateRequest;
import com.shivang.crm.modules.deal.entity.DealType;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.deal.service.DealService;

@Component
public class DealWorkflowEntityUpdateAdapter implements WorkflowEntityUpdateAdapter {

    private final DealService dealService;
    private final DealStageRepository stageRepository;
    private final WorkflowCustomFieldValidationService customFieldValidationService;

    public DealWorkflowEntityUpdateAdapter(DealService dealService, DealStageRepository stageRepository, WorkflowCustomFieldValidationService customFieldValidationService) {
        this.dealService = dealService;
        this.stageRepository = stageRepository;
        this.customFieldValidationService = customFieldValidationService;
    }

    @Override public String entityType() { return "DEAL"; }

    @Override
    public WorkflowEntityUpdateResult update(UUID tenantId, UUID actorId, UUID entityId, String field, Object value, Map<String, Object> currentCustomFields) {
        if ("stage".equals(field)) {
            UUID stageId = resolveStage(tenantId, value);
            dealService.changeStage(entityId, tenantId, stageId, actorId);
            return WorkflowUpdateValueSupport.result("DEAL", entityId, field, value);
        }
        DealUpdateRequest.DealUpdateRequestBuilder request = DealUpdateRequest.builder();
        switch (field) {
            case "name" -> request.name(WorkflowUpdateValueSupport.text(value, field));
            case "accountId" -> request.accountId(WorkflowUpdateValueSupport.uuid(value, field));
            case "contactId" -> request.contactId(WorkflowUpdateValueSupport.uuid(value, field));
            case "amount" -> request.amount(WorkflowUpdateValueSupport.decimal(value, field));
            case "expectedCloseDate" -> request.expectedCloseDate(WorkflowUpdateValueSupport.date(value, field));
            case "closedDate" -> request.closedDate(WorkflowUpdateValueSupport.date(value, field));
            case "probability" -> request.probability(WorkflowUpdateValueSupport.integer(value, field));
            case "forecastCategory" -> request.forecastCategory(enumValue(ForecastCategory.class, value, field));
            case "nextStep" -> request.nextStep(WorkflowUpdateValueSupport.text(value, field));
            case "dealType" -> request.dealType(enumValue(DealType.class, value, field));
            case "leadSource" -> request.leadSource(WorkflowUpdateValueSupport.text(value, field));
            case "campaignSource" -> request.campaignSource(WorkflowUpdateValueSupport.text(value, field));
            case "wonReason" -> request.wonReason(WorkflowUpdateValueSupport.text(value, field));
            case "lostReason" -> request.lostReason(WorkflowUpdateValueSupport.text(value, field));
            case "description" -> request.description(WorkflowUpdateValueSupport.text(value, field));
            default -> {
                if (field.startsWith("customFields.")) {
                    String key = WorkflowUpdateValueSupport.customKey(field);
                    customFieldValidationService.validate("DEAL", tenantId, key, value);
                    request.customData(WorkflowUpdateValueSupport.customFields(currentCustomFields, key, value));
                }
                else throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_FIELD_NOT_SUPPORTED", "Deal field is not supported: " + field);
            }
        }
        dealService.updateDeal(entityId, tenantId, actorId, request.build());
        return WorkflowUpdateValueSupport.result("DEAL", entityId, field, value);
    }

    private UUID resolveStage(UUID tenantId, Object value) {
        try { return stageRepository.findByIdAndTenantId(UUID.fromString(String.valueOf(value)), tenantId).orElseThrow().getId(); }
        catch (Exception ignored) { return stageRepository.findByTenantIdAndName(tenantId, String.valueOf(value)).orElseThrow(() -> new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Deal stage not found")).getId(); }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, Object value, String field) {
        try { return Enum.valueOf(type, String.valueOf(value).trim().toUpperCase()); }
        catch (Exception ex) { throw new WorkflowEntityUpdateException("WORKFLOW_UPDATE_VALIDATION_FAILED", "Invalid value for " + field); }
    }
}