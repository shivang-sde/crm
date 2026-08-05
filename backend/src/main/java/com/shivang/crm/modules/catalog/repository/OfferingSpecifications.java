package com.shivang.crm.modules.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.shivang.crm.modules.catalog.entity.Offering;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;

import jakarta.persistence.criteria.Predicate;

public final class OfferingSpecifications {

    private OfferingSpecifications() {
    }

    public static Specification<Offering> byTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Offering> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Offering> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate name = cb.like(cb.lower(root.get("name")), pattern);
            Predicate code = cb.like(cb.lower(root.get("code")), pattern);
            Predicate description = cb.like(cb.lower(root.get("description")), pattern);
            return cb.or(name, code, description);
        };
    }

    public static Specification<Offering> byOfferingType(OfferingType offeringType) {
        if (offeringType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("offeringType"), offeringType);
    }

    public static Specification<Offering> byBillingType(BillingType billingType) {
        if (billingType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("billingType"), billingType);
    }

    public static Specification<Offering> byActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Offering> byOwnerUserId(UUID ownerUserId) {
        if (ownerUserId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerUserId);
    }

    public static Specification<Offering> buildSpecification(UUID tenantId, String search, OfferingType offeringType,
            BillingType billingType, Boolean active, UUID ownerUserId) {
        Specification<Offering> spec = Specification.where(byTenantId(tenantId)).and(notDeleted());
        Specification<Offering> searchSpec = search(search);
        Specification<Offering> typeSpec = byOfferingType(offeringType);
        Specification<Offering> billingSpec = byBillingType(billingType);
        Specification<Offering> activeSpec = byActive(active);
        Specification<Offering> ownerSpec = byOwnerUserId(ownerUserId);

        if (searchSpec != null) {
            spec = spec.and(searchSpec);
        }
        if (typeSpec != null) {
            spec = spec.and(typeSpec);
        }
        if (billingSpec != null) {
            spec = spec.and(billingSpec);
        }
        if (activeSpec != null) {
            spec = spec.and(activeSpec);
        }
        if (ownerSpec != null) {
            spec = spec.and(ownerSpec);
        }
        return spec;
    }
}
