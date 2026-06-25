package com.shivang.crm.modules.lead.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.lead.entity.Lead;

public class LeadSpecifications {

    public static Specification<Lead> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Lead> visibleToUser(
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

    public static Specification<Lead> byStatusId(UUID statusId) {
        return (root, query, cb) -> cb.equal(root.get("status").get("id"), statusId);
    }

    public static Specification<Lead> bySourceId(UUID sourceId) {
        return (root, query, cb) -> cb.equal(root.get("source").get("id"), sourceId);
    }

    public static Specification<Lead> byOwnerUserId(UUID ownerUserId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerUserId);
    }

    public static Specification<Lead> byOwnerUserIds(List<UUID> ownerUserIds) {
        return (root, query, cb) -> root.get("ownerId").in(ownerUserIds);
    }

    public static Specification<Lead> byIsConverted(Boolean isConverted) {
        return (root, query, cb) -> cb.equal(root.get("isConverted"), isConverted);
    }

    public static Specification<Lead> searchByTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        String term = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("firstName")), term),
            cb.like(cb.lower(root.get("lastName")), term),
            cb.like(cb.lower(root.get("email")), term),
            cb.like(cb.lower(root.get("phone")), term),
            cb.like(cb.lower(root.get("company")), term)
        );
    }

    public static Specification<Lead> byScore(Integer minScore, Integer maxScore) {
        return (root, query, cb) -> {
            if (minScore != null && maxScore != null) {
                return cb.between(root.get("score"), minScore, maxScore);
            } else if (minScore != null) {
                return cb.greaterThanOrEqualTo(root.get("score"), minScore);
            } else if (maxScore != null) {
                return cb.lessThanOrEqualTo(root.get("score"), maxScore);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Lead> byNotConverted() {
        return (root, query, cb) -> cb.equal(root.get("isConverted"), false);
    }

    // Helper method to combine specifications
    public static Specification<Lead> buildSpecification(UUID tenantId, UUID statusId, UUID sourceId,
            UUID ownerUserId, String searchTerm, Boolean isConverted, String accessScope, UUID currentUserId, List<UUID> teamUserIds) {
        Specification<Lead> spec = byTenantId(tenantId);

        spec = spec.and(
            visibleToUser(accessScope, currentUserId, teamUserIds)
        );

        if (statusId != null) {
            spec = spec.and(byStatusId(statusId));
        }

        if (sourceId != null) {
            spec = spec.and(bySourceId(sourceId));
        }

        if (ownerUserId != null) {
            spec = spec.and(byOwnerUserId(ownerUserId));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(searchByTerm(searchTerm));
        }

        if (isConverted != null) {
            spec = spec.and(byIsConverted(isConverted));
        }

        return spec;
    }
}
