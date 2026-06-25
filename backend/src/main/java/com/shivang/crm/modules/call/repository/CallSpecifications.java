package com.shivang.crm.modules.call.repository;

import com.shivang.crm.modules.call.entity.Call;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class CallSpecifications {

    public static Specification<Call> hasTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Call> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Call> hasEntity(String entityType, UUID entityId) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("entityType"), entityType),
            cb.equal(root.get("entityId"), entityId)
        );
    }

    public static Specification<Call> hasStatus(Call.CallStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Call> hasCallType(Call.CallType callType) {
        return (root, query, cb) -> cb.equal(root.get("callType"), callType);
    }

    public static Specification<Call> hasOwnerOrAssignedTo(UUID userId) {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("createdBy"), userId),
            cb.equal(root.get("assignedTo"), userId)
        );
    }
}
