package com.shivang.crm.modules.analytics.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.analytics.AnalyticsContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.analytics.dto.AnalyticsSummaryResponse;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.shared.base.TenantOwnedEntity;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds scoped aggregate summaries with database-side COUNT queries
 * (Criteria API, the same mechanism JPA Specifications use elsewhere in this
 * project). No entity hydration, no per-record loops.
 *
 * Scoping predicates:
 *   PLATFORM - no tenant filter (SUPERADMIN only)
 *   RESELLER - entity.tenant_id IN (SELECT id FROM tenants WHERE reseller_id = :resellerId)
 *   TENANT   - entity.tenant_id = :tenantId
 *   USER     - TENANT filter AND (owner_user_id = :userId OR created_by = :userId),
 *              matching the project's established OWN semantics in RecordScopeGuard
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    @PersistenceContext
    private EntityManager em;

    public AnalyticsSummaryResponse getSummary(AnalyticsContext context, AnalyticsDateRange range) {
        return AnalyticsSummaryResponse.builder()
                .scope(context.scope())
                .from(range.from())
                .to(range.to())
                .leads(count(Lead.class, context, range.from(), range.to()))
                .contacts(count(Contact.class, context, range.from(), range.to()))
                .deals(count(Deal.class, context, range.from(), range.to()))
                .tasks(count(Task.class, context, range.from(), range.to()))
                .calls(count(Call.class, context, range.from(), range.to()))
                .meetings(count(Meeting.class, context, range.from(), range.to()))
                .build();
    }

    /*
     * Attribute names below ("deleted", "createdAt", "tenantId", "ownerId",
     * "createdBy") are the shared TenantOwnedEntity/BaseEntity attributes and
     * are identical on every audited entity type.
     */
    private <T extends TenantOwnedEntity> long count(
            Class<T> entityType, AnalyticsContext ctx, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entityType);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get("deleted")));
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        predicates.add(cb.lessThan(root.get("createdAt"), to));

        switch (ctx.scope()) {
            case TENANT -> predicates.add(
                    cb.equal(root.<UUID>get("tenantId"), ctx.tenantId()));
            case USER -> {
                predicates.add(cb.equal(root.<UUID>get("tenantId"), ctx.tenantId()));
                predicates.add(cb.or(
                        cb.equal(root.<UUID>get("ownerId"), ctx.userId()),
                        cb.equal(root.<UUID>get("createdBy"), ctx.userId())));
            }
            case RESELLER -> predicates.add(root.<UUID>get("tenantId").in(
                    resellerTenantIds(query, root, cb, ctx.resellerId())));
            case PLATFORM -> {
                // SUPERADMIN: all non-deleted records in range.
            }
        }

        query.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));
        TypedQuery<Long> typedQuery = em.createQuery(query);
        Long result = typedQuery.getSingleResult();
        log.debug("Analytics count {} [{} {} {}] = {}", entityType.getSimpleName(),
                ctx.scope(), from, to, result);
        return result != null ? result : 0L;
    }

    private Subquery<UUID> resellerTenantIds(
            CriteriaQuery<Long> query, Root<?> root, CriteriaBuilder cb, UUID resellerId) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<Tenant> tenant = subquery.from(Tenant.class);
        subquery.select(tenant.get("id"))
                .where(cb.equal(tenant.<UUID>get("resellerId"), resellerId));
        return subquery;
    }
}
