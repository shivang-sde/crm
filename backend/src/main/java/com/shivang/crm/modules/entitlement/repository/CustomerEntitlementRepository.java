package com.shivang.crm.modules.entitlement.repository;

import java.time.LocalDate;
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

@Repository
public interface CustomerEntitlementRepository extends JpaRepository<CustomerEntitlement, UUID>, JpaSpecificationExecutor<CustomerEntitlement> {

    Optional<CustomerEntitlement> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByTenantIdAndDealLineItemIdAndDeletedFalse(UUID tenantId, UUID dealLineItemId);

    List<CustomerEntitlement> findByTenantIdAndDealIdAndDeletedFalse(UUID tenantId, UUID dealId);

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
}
