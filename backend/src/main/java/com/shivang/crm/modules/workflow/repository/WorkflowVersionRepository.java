package com.shivang.crm.modules.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.WorkflowVersion;
import com.shivang.crm.modules.workflow.entity.WorkflowStatus;
import com.shivang.crm.modules.workflow.entity.WorkflowVersionStatus;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, UUID> {

    Optional<WorkflowVersion> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<WorkflowVersion> findByWorkflowIdAndTenantIdAndDeletedFalseOrderByVersionNumberDesc(UUID workflowId, UUID tenantId);

    Optional<WorkflowVersion> findFirstByWorkflowIdAndTenantIdAndStatusAndDeletedFalse(UUID workflowId, UUID tenantId, WorkflowVersionStatus status);

    List<WorkflowVersion> findByTenantIdAndStatusAndDeletedFalse(UUID tenantId, WorkflowVersionStatus status);

    @Query("""
        SELECT version
        FROM WorkflowVersion version
        JOIN FETCH version.workflow workflow
        WHERE version.tenantId = :tenantId
          AND workflow.tenantId = :tenantId
          AND workflow.status = :workflowStatus
          AND version.status = :versionStatus
          AND version.triggerEntityType = :entityType
          AND version.triggerEventType = :eventType
          AND version.deleted = false
          AND workflow.deleted = false
        """)
    List<WorkflowVersion> findActiveMatches(
        @Param("tenantId") UUID tenantId,
        @Param("entityType") String entityType,
        @Param("eventType") String eventType,
        @Param("workflowStatus") WorkflowStatus workflowStatus,
        @Param("versionStatus") WorkflowVersionStatus versionStatus
    );
}