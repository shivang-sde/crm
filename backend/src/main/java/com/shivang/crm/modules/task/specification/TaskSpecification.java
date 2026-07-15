package com.shivang.crm.modules.task.specification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.shared.enums.OwnershipScope;

public class TaskSpecification {

    public static Specification<Task> hasTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Task> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Task> hasEntity(String entityType, UUID entityId) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("entityType"), entityType),
            cb.equal(root.get("entityId"), entityId)
        );
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasOwnershipScope(List<OwnershipScope> scopes) {
        if (scopes.contains(OwnershipScope.ALL)) {
            return (root, query, cb) -> cb.conjunction();
        }

        if (scopes.contains(OwnershipScope.TEAM)) {
            // This would need team user IDs - handled in service layer
            return (root, query, cb) -> cb.conjunction();
        }

        if (scopes.contains(OwnershipScope.OWN)) {
            // This would need current user ID - handled in service layer
            return (root, query, cb) -> cb.conjunction();
        }

        return (root, query, cb) -> cb.disjunction();
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

    public static Specification<Task> isOverdue() {
        return (root, query, cb) -> cb.and(
            cb.isFalse(root.get("isClosed")),
            cb.isNotNull(root.get("dueDate")),
            cb.lessThan(root.get("dueDate"), java.time.Instant.now())
        );
    }

    public static Specification<Task> dueBefore(java.time.Instant date) {
        return (root, query, cb) -> cb.lessThan(root.get("dueDate"), date);
    }

    public static Specification<Task> dueAfter(java.time.Instant date) {
        return (root, query, cb) -> cb.greaterThan(root.get("dueDate"), date);
    }

    public static Specification<Task> assignedTo(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("assignedTo"), userId);
    }
}
