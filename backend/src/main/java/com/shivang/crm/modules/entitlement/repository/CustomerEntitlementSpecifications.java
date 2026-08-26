package com.shivang.crm.modules.entitlement.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;

import jakarta.persistence.criteria.Predicate;

public class CustomerEntitlementSpecifications {

    /**
     * RBAC-7: mirrors the CRM-wide scope semantics used by lead/contact/
     * account/deal specifications. Unknown scopes yield no rows (fail-closed).
     */
    public static Specification<CustomerEntitlement> visibleToUser(
            String scope,
            UUID userId,
            java.util.List<UUID> teamUserIds) {
        return (root, query, cb) -> switch (scope) {
            case "ALL" -> cb.conjunction();
            case "TEAM" -> cb.or(
                    root.get("ownerId").in(teamUserIds),
                    cb.equal(root.get("ownerId"), userId),
                    cb.equal(root.get("createdBy"), userId));
            case "OWN" -> cb.or(
                    cb.equal(root.get("ownerId"), userId),
                    cb.equal(root.get("createdBy"), userId));
            default -> cb.disjunction();
        };
    }

    public static Specification<CustomerEntitlement> buildSpecification(
            UUID tenantId,
            UUID accountId,
            UUID contactId,
            UUID offeringId,
            EntitlementStatus status,
            UUID ownerUserId,
            Boolean renewable,
            LocalDate endDateFrom,
            LocalDate endDateTo,
            String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("deleted")));

            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (contactId != null) {
                predicates.add(cb.equal(root.get("contactId"), contactId));
            }
            if (offeringId != null) {
                predicates.add(cb.equal(root.get("offeringId"), offeringId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("ownerId"), ownerUserId));
            }
            if (renewable != null) {
                predicates.add(cb.equal(root.get("renewable"), renewable));
            }
            if (endDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), endDateFrom));
            }
            if (endDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDateTo));
            }
            if (search != null && !search.isBlank()) {
                String likePattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("code")), likePattern),
                        cb.like(cb.lower(root.get("description")), likePattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<CustomerEntitlement> buildUpcomingRenewalSpecification(
            UUID tenantId,
            UUID accountId,
            UUID ownerUserId,
            EntitlementStatus status,
            LocalDate endDateFrom,
            LocalDate endDateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.isTrue(root.get("renewable")));
            predicates.add(cb.isNotNull(root.get("endDate")));
            predicates.add(root.get("status").in(List.of(EntitlementStatus.ACTIVE, EntitlementStatus.PENDING)));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), endDateFrom));
            predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDateTo));
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (ownerUserId != null) {
                predicates.add(cb.equal(root.get("ownerId"), ownerUserId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
