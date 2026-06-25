package com.shivang.crm.modules.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.account.entity.Account;

public class AccountSpecifications {

    public static Specification<Account> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Account> visibleToUser(String scope, UUID userId, List<UUID> teamUserIds) {
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

    public static Specification<Account> byOwnerId(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Account> byOwnerIds(List<UUID> ownerIds) {
        return (root, query, cb) -> root.get("ownerId").in(ownerIds);
    }

    public static Specification<Account> searchByTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        String term = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), term),
            cb.like(cb.lower(root.get("industry")), term),
            cb.like(cb.lower(root.get("email")), term),
            cb.like(cb.lower(root.get("phone")), term),
            cb.like(cb.lower(root.get("website")), term)
        );
    }

    public static Specification<Account> buildSpecification(
            UUID tenantId,
            UUID ownerId,
            String searchTerm,
            String accessScope,
            UUID currentUserId,
            List<UUID> teamUserIds) {
        Specification<Account> spec = byTenantId(tenantId);
        spec = spec.and(visibleToUser(accessScope, currentUserId, teamUserIds));

        if (ownerId != null) {
            spec = spec.and(byOwnerId(ownerId));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(searchByTerm(searchTerm));
        }

        return spec;
    }
}
