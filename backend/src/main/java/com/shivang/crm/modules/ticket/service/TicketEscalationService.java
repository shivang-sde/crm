package com.shivang.crm.modules.ticket.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.ticket.entity.Ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P6: escalates a breached Ticket to its owner's manager.
 *
 * <p>One fixed rule, one hop. An agent whose SLA is missed hands the ticket to their
 * direct manager; if that manager's own SLA then missed, the ticket does not travel
 * further, because unbounded escalation up a management chain is how a single bad day
 * turns into an outage for every manager in the tenant.
 *
 * <p>The hierarchy is {@code users.manager_id}, the relationship this repository
 * already uses for TEAM record scope. No Team, Queue, policy table or configurable
 * level is involved.
 *
 * <p>The manager is resolved through the tenant-scoped, deleted-aware user lookup
 * rather than trusted from {@code manager_id} alone: that column carries no tenant
 * constraint, so a stale or cross-tenant link would otherwise assign a ticket to a
 * stranger. A missing or foreign manager yields no escalation rather than an error,
 * because the breach itself has already succeeded and must not be undone.
 *
 * <p>Runs inside the caller's transaction. The breach transition, the owner change
 * and both records roll back together, so a ticket can never end up escalated without
 * the breach that caused it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEscalationService {

    private final UserRepository userRepository;
    private final TicketService ticketService;

    /**
     * @return true when the ticket was reassigned to the owner's manager
     */
    @Transactional
    public boolean escalateToOwnerManager(Ticket ticket) {
        UUID previousOwnerId = ticket.getOwnerId();
        if (previousOwnerId == null) {
            log.debug("Ticket {} has no owner; no escalation target", ticket.getId());
            return false;
        }

        // Read at escalation time rather than from anything captured earlier, so a
        // reassignment that landed after the breach is still honoured.
        Optional<User> owner = userRepository.findByIdAndTenantIdAndDeletedFalse(
                previousOwnerId, ticket.getTenantId());
        UUID managerId = owner.map(User::getManagerId).orElse(null);
        if (managerId == null) {
            log.debug("Owner {} of ticket {} has no manager; not escalating",
                    previousOwnerId, ticket.getId());
            return false;
        }

        // Tenant-safe resolution of the target. A deleted or foreign manager resolves
        // to empty and is treated as no escalation target.
        Optional<User> manager = userRepository.findByIdAndTenantIdAndDeletedFalse(
                managerId, ticket.getTenantId());
        if (manager.isEmpty()) {
            log.warn("Manager {} of owner {} is not a live user of tenant {}; not escalating",
                    managerId, previousOwnerId, ticket.getTenantId());
            return false;
        }
        if (manager.get().getId().equals(previousOwnerId)) {
            // Self-referential manager row; escalating would be a no-op anyway.
            return false;
        }

        // Re-reads the ticket and validates the target again, so escalation cannot
        // bypass tenant ownership or write a second owner-change if one already landed.
        ticketService.escalateTicket(ticket.getId(), ticket.getTenantId(), manager.get().getId());
        log.info("Ticket {} escalated from owner {} to manager {}",
                ticket.getId(), previousOwnerId, manager.get().getId());
        return true;
    }
}
