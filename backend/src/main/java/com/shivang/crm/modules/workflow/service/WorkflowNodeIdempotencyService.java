package com.shivang.crm.modules.workflow.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeIdempotency;
import com.shivang.crm.modules.workflow.entity.WorkflowNodeIdempotencyStatus;
import com.shivang.crm.modules.workflow.repository.WorkflowNodeIdempotencyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowNodeIdempotencyService {

    private final WorkflowNodeIdempotencyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkflowNodeIdempotencyClaim claim(UUID tenantId, UUID executionId, UUID nodeExecutionId) {
        String key = executionId + ":" + nodeExecutionId;
        int inserted = repository.claimIfAbsent(tenantId, executionId, nodeExecutionId, key);
        if (inserted == 1) {
            WorkflowNodeIdempotency record = loadOrThrow(tenantId, executionId, nodeExecutionId);
            log.debug("Workflow node idempotency claimed executionId={} nodeExecutionId={} status=IN_PROGRESS", executionId, nodeExecutionId);
            return new WorkflowNodeIdempotencyClaim(record, true);
        }

        WorkflowNodeIdempotency record = loadOrThrow(tenantId, executionId, nodeExecutionId);
        if (record.getStatus() == WorkflowNodeIdempotencyStatus.COMPLETED) {
            log.info("Reusing completed workflow node result executionId={} nodeExecutionId={}", executionId, nodeExecutionId);
            return new WorkflowNodeIdempotencyClaim(record, false);
        }
        if (record.getStatus() == WorkflowNodeIdempotencyStatus.FAILED) {
            if (repository.reclaimFailed(record.getId()) == 1) {
                record.setStatus(WorkflowNodeIdempotencyStatus.IN_PROGRESS);
                log.debug("Workflow node idempotency retry reclaimed executionId={} nodeExecutionId={} status=FAILED->IN_PROGRESS", executionId, nodeExecutionId);
                return new WorkflowNodeIdempotencyClaim(record, true);
            }
            // Lost the reclaim race; re-check in case the other claimant already completed it.
            record = loadOrThrow(tenantId, executionId, nodeExecutionId);
            if (record.getStatus() == WorkflowNodeIdempotencyStatus.COMPLETED) {
                return new WorkflowNodeIdempotencyClaim(record, false);
            }
        }
        throw new WorkflowRuntimeException(
            "WORKFLOW_IDEMPOTENCY_IN_PROGRESS",
            "Workflow node side effect has an ambiguous in-progress outcome"
        );
    }

    @Transactional
    public void complete(WorkflowNodeIdempotency record, Map<String, Object> result) {
        record.setStatus(WorkflowNodeIdempotencyStatus.COMPLETED);
        record.setResult(result);
        record.setCompletedAt(Instant.now());
        record.setLastErrorCode(null);
        record.setLastErrorMessage(null);
        repository.save(record);
        log.info("Workflow node idempotency completed key={} status=COMPLETED", shortKey(record.getIdempotencyKey()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(WorkflowNodeIdempotency record, String errorCode, String errorMessage) {
        record.setStatus(WorkflowNodeIdempotencyStatus.FAILED);
        record.setLastErrorCode(errorCode);
        record.setLastErrorMessage(errorMessage);
        repository.save(record);
        log.info("Workflow node idempotency failed key={} status=FAILED errorCode={}", shortKey(record.getIdempotencyKey()), errorCode);
    }

    private WorkflowNodeIdempotency loadOrThrow(UUID tenantId, UUID executionId, UUID nodeExecutionId) {
        return repository
            .findByTenantIdAndWorkflowExecutionIdAndWorkflowNodeExecutionId(tenantId, executionId, nodeExecutionId)
            .orElseThrow(() -> new WorkflowRuntimeException("WORKFLOW_IDEMPOTENCY_CLAIM_FAILED", "Idempotency record could not be loaded"));
    }

    private String shortKey(String key) {
        return key == null || key.length() <= 12 ? key : key.substring(0, 12);
    }

    public record WorkflowNodeIdempotencyClaim(WorkflowNodeIdempotency record, boolean execute) { }
}