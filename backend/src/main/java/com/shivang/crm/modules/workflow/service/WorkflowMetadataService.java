package com.shivang.crm.modules.workflow.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse;
import com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.EntityMetadata;
import com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.EventMetadata;

import static com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.entity;
import static com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.event;
import static com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.relationship;
import static com.shivang.crm.modules.workflow.dto.WorkflowMetadataResponse.entityWithRelationships;

/**
 * Single source of truth for the workflow builder vocabulary. Mirrors the
 * canonical event producers (BE-EVENT-1..6) and the tenant-aware entity
 * context providers. Tenant-specific values are deliberately NOT included.
 */
@Component
public class WorkflowMetadataService {

    private static final List<String> OPERATORS = List.of(
        "EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_THAN_OR_EQUAL",
        "LESS_THAN", "LESS_THAN_OR_EQUAL", "CONTAINS", "NOT_CONTAINS",
        "IS_NULL", "IS_NOT_NULL", "IN", "NOT_IN"
    );

    private static final List<String> ACTIONS = List.of(
        "NO_OP", "SET_CONTEXT_VALUE", "UPDATE_ENTITY_FIELD",
        "ASSIGN_OWNER", "CREATE_TASK", "CLICK_TO_CALL", "HTTP_API"
    );

    public WorkflowMetadataResponse getMetadata() {
        return new WorkflowMetadataResponse(entities(), ACTIONS, OPERATORS);
    }

    private List<EntityMetadata> entities() {
        return List.of(
            entityWithRelationships("LEAD", "Lead", List.of(
                event("CREATED", "Created"),
                event("STATUS_CHANGED", "Status Changed",
                    "previousStatusId", "newStatusId", "previousStatus", "newStatus"),
                event("OWNER_CHANGED", "Owner Changed", "previousOwnerId", "newOwnerId"),
                event("CONVERTED", "Converted", "accountId", "contactId")
            ), List.of(
                "id", "ownerId", "firstName", "lastName", "fullName", "email", "phone",
                "company", "status", "statusId", "source", "sourceId", "score",
                "isConverted", "convertedAccountId", "convertedContactId",
                "convertedAccount", "convertedContact",
                "createdAt", "updatedAt"
            ), List.of(
                relationship("convertedAccount", "Converted Account", "ACCOUNT",
                    List.of("id", "name", "ownerId", "customFields"), true),
                relationship("convertedContact", "Converted Contact", "CONTACT",
                    List.of("id", "email", "ownerId", "customFields"), true)
            )),
            entityWithRelationships("CONTACT", "Contact", List.of(
                event("CREATED", "Created"),
                event("UPDATED", "Updated"),
                event("OWNER_CHANGED", "Owner Changed", "previousOwnerId", "newOwnerId")
            ), List.of(
                "id", "ownerId", "accountId", "leadId", "firstName", "lastName",
                "email", "phone", "mobile", "jobTitle", "department",
                "isPrimary", "isActive", "account", "createdAt", "updatedAt"
            ), List.of(
                relationship("account", "Account", "ACCOUNT",
                    List.of("id", "name", "industry", "ownerId", "customFields"), true)
            )),
            entity("ACCOUNT", "Account", List.of(
                event("CREATED", "Created"),
                event("UPDATED", "Updated"),
                event("OWNER_CHANGED", "Owner Changed", "previousOwnerId", "newOwnerId")
            ), List.of(
                "id", "ownerId", "name", "website", "industry", "phone", "email",
                "annualRevenue", "employeeCount", "description", "country", "state",
                "city", "addressLine1", "postalCode", "leadId", "isActive",
                "createdAt", "updatedAt"
            )),
            entityWithRelationships("DEAL", "Deal", List.of(
                event("CREATED", "Created"),
                event("STAGE_TRANSITIONED", "Stage Transitioned",
                    "previousStageId", "newStageId", "previousStage", "newStage"),
                event("OWNER_CHANGED", "Owner Changed", "previousOwnerId", "newOwnerId")
            ), List.of(
                "id", "ownerId", "name", "accountId", "contactId", "leadId",
                "stage", "stageId", "recordCategory", "amount", "expectedCloseDate",
                "probability", "forecastCategory", "closedDate", "wonReason",
                "lostReason", "description", "isWon", "isLost", "isClosed",
                "account", "contact", "lead",
                "createdAt", "updatedAt"
            ), List.of(
                relationship("account", "Account", "ACCOUNT",
                    List.of("id", "name", "industry", "ownerId", "customFields"), true),
                relationship("contact", "Contact", "CONTACT",
                    List.of("id", "email", "ownerId", "customFields"), true),
                relationship("lead", "Lead", "LEAD",
                    List.of("id", "status", "ownerId", "customFields"), true)
            )),
            entityWithRelationships("TASK", "Task", List.of(
                event("CREATED", "Created"),
                event("COMPLETED", "Completed", "previousStatus", "newStatus"),
                event("STATUS_CHANGED", "Status Changed", "previousStatus", "newStatus")
            ), List.of(
                "id", "ownerId", "createdBy", "subject", "description", "status",
                "priority", "entityType", "entityId", "dueDate", "remindAt",
                "isClosed", "completedAt", "related", "createdAt", "updatedAt"
            ), List.of(
                relationship("related", "Related Record", null,
                    List.of("id", "type", "name", "status", "ownerId", "customFields"), true)
            )),
            entityWithRelationships("MEETING", "Meeting", List.of(
                event("CREATED", "Created"),
                event("STATUS_CHANGED", "Status Changed", "previousStatus", "newStatus")
            ), List.of(
                "id", "ownerId", "createdBy", "subject", "meetingType", "startTime",
                "endTime", "status", "attendees", "assignedTo", "entityType",
                "entityId", "related", "createdAt", "updatedAt"
            ), List.of(
                relationship("related", "Related Record", null,
                    List.of("id", "type", "name", "status", "ownerId", "customFields"), true)
            )),
            entityWithRelationships("CALL", "Call", List.of(
                event("CREATED", "Created"),
                event("COMPLETED", "Completed", "previousStatus", "newStatus")
            ), List.of(
                "id", "ownerId", "createdBy", "subject", "callType", "status",
                "phoneNumber", "startTime", "endTime", "disposition", "notes",
                "nextAction", "followUpAt", "entityType", "entityId",
                "related", "createdAt", "updatedAt"
            ), List.of(
                relationship("related", "Related Record", null,
                    List.of("id", "type", "name", "disposition", "ownerId", "customFields"), true)
            ))
        );
    }

    private EntityMetadata entityMeta(
        String entityType,
        String label,
        List<EventMetadata> events,
        List<String> fields
    ) {
        return new EntityMetadata(entityType, label, events, fields, true, List.of());
    }
}





