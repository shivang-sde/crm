package com.shivang.crm.modules.activity.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.deal.service.DealService;
import com.shivang.crm.modules.lead.service.LeadService;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P8: authorization boundary for the generic activity endpoints.
 *
 * <p>An activity row has no owner of its own, so it cannot be authorized directly.
 * It is authorized through the entity it belongs to: the entity-scoped activity
 * endpoints already do exactly this via {@code assert*Accessible}, and the generic
 * {@code /api/v1/activities} endpoints are routed through the same checks here rather
 * than reimplementing them.
 *
 * <p>Entity types with no parent-scope assertion are rejected rather than served on
 * tenant ownership alone. An activity is only as private as its parent, so treating a
 * matching {@code tenantId} as sufficient would expose the audit trail of records the
 * caller cannot see under OWN or TEAM scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityAccessGuard {

    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;
    private final LeadService leadService;
    private final TicketService ticketService;

    /**
     * Requires the caller to be able to read the referenced entity.
     *
     * @param entityType as stored in {@code activities.entity_type}
     * @param action    the parent's read/write action, matching the guard's vocabulary
     * @throws BusinessException when the type is unsupported or the parent is out of
     *         scope; the caller must not fall back to tenant-only filtering
     */
    public void requireEntityReadable(UUID tenantId, String entityType, UUID entityId, String action) {
        String type = entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);

        switch (type) {
            case "ACCOUNT" -> accountService.assertAccountAccessible(tenantId, entityId, action);
            case "CONTACT" -> contactService.assertContactAccessible(tenantId, entityId, action);
            case "DEAL" -> dealService.assertDealAccessible(tenantId, entityId, action);
            case "LEAD" -> leadService.assertLeadAccessible(tenantId, entityId, action);
            case "TICKET" -> ticketService.requireReadable(entityId, tenantId, currentUserId());
            default -> throw new BusinessException(
                    "ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED",
                    "Activities cannot be authorized for entity type: " + entityType);
        }
    }

    private UUID currentUserId() {
        return com.shivang.crm.util.UserUtil.currentUserId();
    }
}
