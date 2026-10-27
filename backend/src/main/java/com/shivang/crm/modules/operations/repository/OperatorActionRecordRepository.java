package com.shivang.crm.modules.operations.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.operations.entity.OperatorActionRecord;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;

@Repository
public interface OperatorActionRecordRepository extends JpaRepository<OperatorActionRecord, UUID> {

    /**
     * True when a previous action on this target already succeeded.
     *
     * <p>This mirrors the {@code uq_operator_action_success_target} partial unique
     * index. The read is for a fast, friendly 409; the index is the actual
     * guarantee, because a read-then-write race between two operators can both
     * observe {@code false} and both attempt the insert.
     */
    boolean existsByActionTypeAndTargetIdAndResultAndDeletedFalse(
            OperatorActionType actionType, String targetId, OperatorActionResult result);

    @Query("""
        SELECT a FROM OperatorActionRecord a
         WHERE a.actionType = :actionType
           AND a.tenantId = :tenantId
           AND a.deleted = false
         ORDER BY a.createdAt DESC
        """)
    Page<OperatorActionRecord> findHistoryForTenant(@Param("actionType") OperatorActionType actionType,
            @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT a FROM OperatorActionRecord a
         WHERE a.actionType = :actionType
           AND a.deleted = false
         ORDER BY a.createdAt DESC
        """)
    Page<OperatorActionRecord> findHistoryPlatformWide(@Param("actionType") OperatorActionType actionType,
            Pageable pageable);

    Optional<OperatorActionRecord> findFirstByActionTypeAndTargetIdAndResultOrderByCreatedAtDesc(
            OperatorActionType actionType, String targetId, OperatorActionResult result);

    long countByActionTypeAndResultAndDeletedFalse(OperatorActionType actionType, OperatorActionResult result);
}
