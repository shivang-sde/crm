package com.shivang.crm.modules.feedback.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

import jakarta.persistence.LockModeType;

/**
 * L3.3-P1: Feedback persistence.
 *
 * <p>Every finder is tenant-scoped and deleted-aware. This is not a stylistic
 * choice: {@code RecordScopeGuard} documents that scope NEVER widens the tenant
 * boundary, so the tenant predicate is the only thing standing between one tenant
 * and another's feedback.
 */
@Repository
public interface FeedbackRepository
        extends JpaRepository<Feedback, UUID>, JpaSpecificationExecutor<Feedback> {

    /**
     * L3.3-P1: the external-reference dedup lookup.
     *
     * <p>Tenant-scoped and deleted-aware, matching every other finder here.
     *
     * <p>Uniqueness is additionally enforced at the database level by a partial
     * unique index, so two concurrent resubmissions of the same
     * {@code externalReference} cannot both insert. This method is the fast path;
     * the index is the guarantee. {@code source} is part of the key because the
     * same identifier from two different systems is two different submissions.
     */
    Optional<Feedback> findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
            UUID tenantId, com.shivang.crm.modules.feedback.entity.FeedbackSource source, String externalReference);

    /** Convenience for the account-scoped list; ordering is newest first. */
    List<Feedback> findByTenantIdAndAccountIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID tenantId, UUID accountId);


  @Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    select f from Feedback f
    where f.id = :id
      and f.tenantId = :tenantId
      and f.deleted = false
    """)
Optional<Feedback> findByIdAndTenantIdAndDeletedFalseForUpdate(
        @Param("id") UUID id,
        @Param("tenantId") UUID tenantId);

    }