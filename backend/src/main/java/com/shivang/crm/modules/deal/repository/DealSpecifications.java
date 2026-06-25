package com.shivang.crm.modules.deal.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.RecordCategory;

public class DealSpecifications {

    public static Specification<Deal> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Deal> byStageId(UUID stageId) {
        return (root, query, cb) -> cb.equal(root.get("stage").get("id"), stageId);
    }

    public static Specification<Deal> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Deal> byContactId(UUID contactId) {
        return (root, query, cb) -> cb.equal(root.get("contactId"), contactId);
    }

    public static Specification<Deal> byOwnerUserId(UUID ownerUserId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerUserId);
    }

    public static Specification<Deal> byOwnerUserIds(List<UUID> ownerUserIds) {
        return (root, query, cb) -> root.get("ownerId").in(ownerUserIds);
    }

    public static Specification<Deal> byIsWon(Boolean isWon) {
        return Boolean.TRUE.equals(isWon)
            ? byRecordCategory(RecordCategory.CLOSED_WON)
            : (root, query, cb) -> cb.notEqual(root.get("stage").get("recordCategory"), RecordCategory.CLOSED_WON);
    }

    public static Specification<Deal> byIsLost(Boolean isLost) {
        return Boolean.TRUE.equals(isLost)
            ? byRecordCategory(RecordCategory.CLOSED_LOST)
            : (root, query, cb) -> cb.notEqual(root.get("stage").get("recordCategory"), RecordCategory.CLOSED_LOST);
    }

    public static Specification<Deal> byRecordCategory(RecordCategory recordCategory) {
        return (root, query, cb) -> cb.equal(root.get("stage").get("recordCategory"), recordCategory);
    }

    public static Specification<Deal> searchByTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        String term = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), term),
            cb.like(cb.lower(root.get("description")), term)
        );
    }

    public static Specification<Deal> expectedCloseDateBetween(LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> {
            if (fromDate != null && toDate != null) {
                return cb.between(root.get("expectedCloseDate"), fromDate, toDate);
            } else if (fromDate != null) {
                return cb.greaterThanOrEqualTo(root.get("expectedCloseDate"), fromDate);
            } else if (toDate != null) {
                return cb.lessThanOrEqualTo(root.get("expectedCloseDate"), toDate);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Deal> visibleToUser(
        String scope,
        UUID userId,
        List<UUID> teamUserIds
    ) {
        return (root, query, cb) -> switch (scope) {
            case "ALL" -> cb.conjunction();

            case "TEAM" -> cb.or(
                root.get("ownerId").in(teamUserIds),
                cb.equal(root.get("ownerId"), userId),
                cb.equal(root.get("createdBy"), userId)
            );

            case "OWN" -> cb.or(
                cb.equal(root.get("ownerId"), userId),
                cb.equal(root.get("createdBy"), userId)
            );

            default -> cb.disjunction();
        };
    }

    // Helper method to combine specifications
    public static Specification<Deal> buildSpecification(
            UUID tenantId,
            UUID stageId,
            UUID accountId,
            UUID contactId,
            UUID ownerUserId,
            String searchTerm,
            Boolean isWon,
            Boolean isLost,
            LocalDate expectedCloseDateFrom,
            LocalDate expectedCloseDateTo,
            String accessScope,
            UUID currentUserId,
            List<UUID> teamUserIds
    ) {
        Specification<Deal> spec = byTenantId(tenantId);

        spec = spec.and(visibleToUser(accessScope, currentUserId, teamUserIds));

        if (stageId != null) {
            spec = spec.and(byStageId(stageId));
        }

        if (accountId != null) {
            spec = spec.and(byAccountId(accountId));
        }

        if (contactId != null) {
            spec = spec.and(byContactId(contactId));
        }

        if (ownerUserId != null) {
            spec = spec.and(byOwnerUserId(ownerUserId));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(searchByTerm(searchTerm));
        }

        if (isWon != null) {
            spec = spec.and(byIsWon(isWon));
        }

        if (isLost != null) {
            spec = spec.and(byIsLost(isLost));
        }

        if (expectedCloseDateFrom != null || expectedCloseDateTo != null) {
            spec = spec.and(expectedCloseDateBetween(expectedCloseDateFrom, expectedCloseDateTo));
        }

        return spec;
    }
}
