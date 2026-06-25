package com.shivang.crm.modules.task.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;

public class TaskSpecifications {

    public static Specification<Task> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Task> byEntityType(String entityType) {
        return (root, query, cb) -> entityType != null 
            ? cb.equal(root.get("entityType"), entityType) 
            : cb.isNull(root.get("entityType"));
    }

    public static Specification<Task> byEntityId(UUID entityId) {
        return (root, query, cb) -> entityId != null 
            ? cb.equal(root.get("entityId"), entityId) 
            : cb.isNull(root.get("entityId"));
    }

    public static Specification<Task> byStatus(TaskStatus status) {
        return (root, query, cb) -> status != null 
            ? cb.equal(root.get("status"), status) 
            : cb.isNull(root.get("status"));
    }

    public static Specification<Task> byOwnerUserId(UUID ownerUserId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerUserId);
    }

    public static Specification<Task> byIsClosed(Boolean isClosed) {
        return (root, query, cb) -> isClosed != null 
            ? cb.equal(root.get("isClosed"), isClosed) 
            : cb.isNull(root.get("isClosed"));
    }

    public static Specification<Task> searchByTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        String term = "%" + searchTerm.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("subject")), term),
            cb.like(cb.lower(root.get("description")), term)
        );
    }

    public static Specification<Task> dueDateBefore(Instant dueDate) {
        return (root, query, cb) -> dueDate != null 
            ? cb.lessThan(root.get("dueDate"), dueDate) 
            : cb.conjunction();
    }

    public static Specification<Task> dueDateAfter(Instant dueDate) {
        return (root, query, cb) -> dueDate != null 
            ? cb.greaterThan(root.get("dueDate"), dueDate) 
            : cb.conjunction();
    }

    public static Specification<Task> visibleToUser(
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
    public static Specification<Task> buildSpecification(
            UUID tenantId,
            String entityType,
            UUID entityId,
            TaskStatus status,
            UUID ownerUserId,
            String searchTerm,
            Boolean isClosed,
            String accessScope,
            UUID currentUserId,
            List<UUID> teamUserIds
    ) {
        Specification<Task> spec = byTenantId(tenantId);

        spec = spec.and(visibleToUser(accessScope, currentUserId, teamUserIds));

        if (entityType != null) {
            spec = spec.and(byEntityType(entityType));
        }

        if (entityId != null) {
            spec = spec.and(byEntityId(entityId));
        }

        if (status != null) {
            spec = spec.and(byStatus(status));
        }

        if (ownerUserId != null) {
            spec = spec.and(byOwnerUserId(ownerUserId));
        }

        if (isClosed != null) {
            spec = spec.and(byIsClosed(isClosed));
        }

        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and(searchByTerm(searchTerm));
        }

        return spec;
    }
}
