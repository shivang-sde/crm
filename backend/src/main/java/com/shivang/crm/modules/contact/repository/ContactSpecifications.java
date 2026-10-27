package com.shivang.crm.modules.contact.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.contact.entity.Contact;

public class ContactSpecifications {

    public static Specification<Contact> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    /**
     * L3.0-P2: soft-delete is conventional here (no @SQLDelete / @Where), so
     * live reads must exclude deleted rows explicitly.
     */
    public static Specification<Contact> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Contact> visibleToUser(String scope, UUID userId, List<UUID> teamUserIds) {
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

    public static Specification<Contact> byOwnerId(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Contact> byOwnerIds(List<UUID> ownerIds) {
        return (root, query, cb) -> root.get("ownerId").in(ownerIds);
    }

    public static Specification<Contact> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Contact> searchByTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        String term = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("firstName")), term),
            cb.like(cb.lower(root.get("lastName")), term),
            cb.like(cb.lower(root.get("email")), term),
            cb.like(cb.lower(root.get("phone")), term),
            cb.like(cb.lower(root.get("jobTitle")), term)
        );
    }

    public static Specification<Contact> buildSpecification(
            UUID tenantId,
            UUID accountId,
            UUID ownerId,
            String searchTerm,
            String accessScope,
            UUID currentUserId,
            List<UUID> teamUserIds) {
        Specification<Contact> spec = byTenantId(tenantId);
        spec = spec.and(notDeleted());
        spec = spec.and(visibleToUser(accessScope, currentUserId, teamUserIds));

        if (accountId != null) {
            spec = spec.and(byAccountId(accountId));
        }

        if (ownerId != null) {
            spec = spec.and(byOwnerId(ownerId));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(searchByTerm(searchTerm));
        }

        return spec;
    }
}
