package com.shivang.crm.modules.workflow.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.workflow.entity.WorkflowExecutionStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowActorType;
import com.shivang.crm.modules.workflow.entity.WorkflowStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowExecutionRepository;
import com.shivang.crm.modules.workflow.repository.WorkflowVersionRepository;
import com.shivang.crm.shared.event.CanonicalCrmEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkflowTriggerService {

    private final WorkflowVersionRepository workflowVersionRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(CanonicalCrmEvent event) {
        List<WorkflowVersion> matches = workflowVersionRepository.findActiveMatches(
            event.tenantId(),
            event.entityType(),
            event.eventType(),
            WorkflowStatus.ACTIVE,
            WorkflowVersionStatus.ACTIVE
        );

        String triggerContext = serializeContext(event);
        UUID actorId = readActorId(event.metadata());
        WorkflowActorType actorType = readActorType(event.metadata());
        for (WorkflowVersion version : matches) {
            workflowExecutionRepository.insertIfAbsent(
                event.tenantId(),
                version.getWorkflow().getId(),
                version.getId(),
                event.eventId(),
                event.entityType(),
                event.entityId(),
                event.eventType(),
                actorId,
                actorType,
                WorkflowExecutionStatus.PENDING.name(),
                triggerContext
            );
        }
    }

    private UUID readActorId(java.util.Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("actorId");
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "Canonical event actorId is invalid");
        }
    }

    private WorkflowActorType readActorType(java.util.Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("actorType");
        if (value == null) return null;
        try {
            return WorkflowActorType.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new WorkflowRuntimeException("WORKFLOW_ACTOR_INVALID", "Canonical event actorType is invalid");
        }
    }

    private String serializeContext(CanonicalCrmEvent event) {
        try {
            return objectMapper.writeValueAsString(event.metadata() == null ? java.util.Map.of() : event.metadata());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize canonical event metadata", ex);
        }
    }
}