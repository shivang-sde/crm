package com.shivang.crm.modules.feedback.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.feedback.entity.FeedbackRequest;

import jakarta.persistence.LockModeType;

@Repository
public interface FeedbackRequestRepository extends JpaRepository<FeedbackRequest, UUID> {

    Optional<FeedbackRequest> findByTokenHashAndDeletedFalse(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM FeedbackRequest r
            WHERE r.tokenHash = :tokenHash
              AND r.deleted = false
            """)
    Optional<FeedbackRequest> findByTokenHashAndDeletedFalseForUpdate(
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM FeedbackRequest r
            WHERE r.id = :id
              AND r.tenantId = :tenantId
              AND r.ticketId = :ticketId
              AND r.deleted = false
            """)
    Optional<FeedbackRequest> findForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId);

    List<FeedbackRequest> findByTenantIdAndTicketIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID ticketId);

    @Query("""
            SELECT r FROM FeedbackRequest r
            WHERE r.tenantId = :tenantId
              AND r.ticketId = :ticketId
              AND r.deleted = false
              AND r.submittedAt IS NULL
              AND r.revokedAt IS NULL
              AND r.expiresAt > :now
            ORDER BY r.createdAt DESC
            """)
    List<FeedbackRequest> findLivePending(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            @Param("now") Instant now);
}
