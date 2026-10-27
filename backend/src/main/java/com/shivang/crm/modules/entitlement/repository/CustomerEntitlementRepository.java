package com.shivang.crm.modules.entitlement.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;

/**
 * L3.0-P4-L3.4-P1: Customer entitlement persistence.
 *
 * <p>Every finder is tenant-scoped and deleted-aware. The {@code status} and
 * {@code endDate} predicates are ordinary Spring Data JPA method names; they are
 * not ORM-level {@code @Where} filters, so every live read must exclude soft-deleted
 * rows explicitly (the {@code deleted} column is a plain boolean on the entity).
 */
@Repository
public interface CustomerEntitlementRepository extends JpaRepository<CustomerEntitlement, UUID>, JpaSpecificationExecutor<CustomerEntitlement> {

    Optional<CustomerEntitlement> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByTenantIdAndDealLineItemIdAndDeletedFalse(UUID tenantId, UUID dealLineItemId);

    List<CustomerEntitlement> findByTenantIdAndDealIdAndDeletedFalse(UUID tenantId, UUID dealId);

    List<CustomerEntitlement> findByStatusAndEndDateBeforeAndDeletedFalse(EntitlementStatus status, LocalDate date);

    List<CustomerEntitlement> findByRenewableTrueAndStatusInAndRenewalDueDateLessThanEqualAndEndDateGreaterThanEqualAndDeletedFalse(
            Collection<EntitlementStatus> statuses, LocalDate renewalDueDateCutoff, LocalDate endDateCutoff);

    boolean existsByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Page<CustomerEntitlement> findAll(Specification<CustomerEntitlement> spec, Pageable pageable);

    default Page<CustomerEntitlement> findFiltered(
            UUID tenantId,
            UUID accountId,
            UUID contactId,
            UUID offeringId,
            EntitlementStatus status,
            UUID ownerUserId,
            Boolean renewable,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String search,
            Pageable pageable) {
        return findAll(CustomerEntitlementSpecifications.buildSpecification(
                tenantId, accountId, contactId, offeringId, status, ownerUserId, renewable, endDateFrom, endDateTo, search), pageable);
    }

    boolean existsByTenantIdAndOfferingIdAndStatusInAndDeletedFalse(
        UUID tenantId,
        UUID offeringId,
        List<EntitlementStatus> statuses);

    boolean existsByTenantIdAndAccountIdAndDeletedFalse(UUID tenantId, UUID accountId);
    boolean existsByTenantIdAndOfferingIdAndDeletedFalse(UUID tenantId, UUID offeringId);
    boolean existsByTenantIdAndDealIdAndDeletedFalse(UUID tenantId, UUID dealId);
}