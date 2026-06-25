package com.shivang.crm.modules.meeting.repository;

import com.shivang.crm.modules.meeting.entity.Meeting;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class MeetingSpecifications {

    public static Specification<Meeting> hasTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Meeting> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Meeting> hasEntity(String entityType, UUID entityId) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("entityType"), entityType),
            cb.equal(root.get("entityId"), entityId)
        );
    }

    public static Specification<Meeting> hasStatus(Meeting.MeetingStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Meeting> hasOwnerOrAssignedTo(UUID userId) {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("createdBy"), userId),
            cb.equal(root.get("assignedTo"), userId)
        );
    }
}
