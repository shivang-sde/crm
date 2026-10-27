package com.shivang.crm.modules.ticket.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.modules.ticket.repository.TicketSpecifications;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P4: detects missed SLA deadlines.
 *
 * <p>Follows {@code EntitlementExpiryService}: a plain {@code @Service} holding the
 * logic, driven by a thin {@code @Component} scheduler. No Quartz, no lock manager,
 * no new worker infrastructure — this repository runs {@code @Scheduled} methods and
 * P4 does not redesign that.
 *
 * <p>Idempotency is the {@code slaBreached} column, not an in-memory flag, and the
 * false → true transition is a single conditional UPDATE whose affected-row count
 * decides who may publish. Sequential reruns find nothing to do; concurrent runners
 * cannot both win.
 *
 * <p>The scan crosses tenants deliberately, as the entitlement expiry does. Every
 * write and every event uses the Ticket's own tenant; no run-level tenant context is
 * involved, because there is no authenticated caller to carry one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlaBreachService {

    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";
    private static final String ACTOR_SOURCE_SCHEDULER = "SCHEDULER";

    private static final String BREACH_TYPE_RESPONSE = "RESPONSE";
    private static final String BREACH_TYPE_RESOLUTION = "RESOLUTION";

    private static final String ACTIVITY_TYPE_SLA_BREACHED = "TICKET_SLA_BREACHED";
    private static final String ACTOR_SOURCE_SLA_LIFECYCLE = "TICKET_SLA_LIFECYCLE";

    /** Bounds one scan so a large backlog cannot hold a single transaction open. */
    private static final int SCAN_BATCH_SIZE = 500;

    private final TicketRepository ticketRepository;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;
    private final ActivityService activityService;
    private final TicketEscalationService escalationService;
    private final java.time.Clock clock;

    @Transactional
    public int detectBreaches() {
        Instant now = clock.instant();

        List<Ticket> candidates = ticketRepository.findAll(
                        TicketSpecifications.slaBreachCandidates(now),
                        PageRequest.of(0, SCAN_BATCH_SIZE,
                                Sort.by(Sort.Direction.ASC, "resolutionDueAt")))
                .getContent();

        int breached = 0;
        for (Ticket ticket : candidates) {
            try {
                if (markBreached(ticket, now)) {
                    breached++;
                }
            } catch (Exception ex) {
                // One ticket must not abort the batch: the rest are independent, and
                // a failure here is retried on the next scan because the row is still
                // unbreached.
                log.warn("Failed to evaluate SLA for ticket {}", ticket.getId(), ex);
            }
        }
        if (breached > 0) {
            log.info("Marked {} tickets as SLA breached as of {}", breached, now);
        }
        return breached;
    }

    /**
     * Applies the breach if this ticket has one, and is the only place
     * {@code TICKET.SLA_BREACHED} is published.
     *
     * <p>The state transition is a conditional UPDATE whose affected-row count
     * arbitrates. A count of 1 means this call is the only one that could have
     * observed {@code sla_breached = false} and flipped it, so it publishes. A count
     * of 0 means another worker already won the transition, and it publishes nothing —
     * checking the flag in memory before the update would leave two callers free to
     * both read false and both publish.
     *
     * @return true when this call won the transition and published the breach
     */
    boolean markBreached(Ticket ticket, Instant now) {
        if (ticket.isSlaBreached()) {
            return false;
        }

        String breachType = breachTypeFor(ticket, now);
        if (breachType == null) {
            return false;
        }

        int rowsUpdated = ticketRepository.markSlaBreachedIfNotBreached(ticket.getId(), ticket.getTenantId());
        if (rowsUpdated == 0) {
            // Lost the race, or the ticket was deleted/breached since the candidate
            // query. No save, no event.
            return false;
        }

        // The conditional UPDATE bypasses the persistence context, which still holds
        // this candidate as managed with slaBreached = false. Syncing the field keeps
        // a later flush of this entity from writing the stale false back over the
        // committed transition. No save() is issued for the SLA change itself.
        ticket.setSlaBreached(true);

        publishBreach(ticket, breachType);
        logBreachActivity(ticket, breachType);
        // Synchronous and in-transaction: the outbox row above is only PENDING until
        // commit, so an async consumer would be a second, independently committed
        // path to the same breach.
        escalationService.escalateToOwnerManager(ticket);
        return true;
    }

    /**
     * Records the breach on the ticket timeline as a SYSTEM activity.
     *
     * <p>activities.performed_by is nullable and actor_type distinguishes SYSTEM, so
     * this is attributed to the scheduler rather than to an invented user. Written
     * only by the winner of the conditional update, which is what keeps repeated
     * scheduler runs from duplicating timeline entries.
     */
    private void logBreachActivity(Ticket ticket, String breachType) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("breachType", breachType);

        activityService.logSystemActivity(
                ticket.getTenantId(), ticket.getId(), "TICKET",
                ACTIVITY_TYPE_SLA_BREACHED,
                "Ticket SLA breached (" + breachType + ")",
                ACTOR_SOURCE_SLA_LIFECYCLE,
                metadata);
    }

    /**
     * Which deadline was missed. Resolution wins when both are outstanding so the
     * value is deterministic; the ticket still emits exactly one event either way.
     */
    private String breachTypeFor(Ticket ticket, Instant now) {
        if (ticket.hasOutstandingResolutionSla(now)) {
            return BREACH_TYPE_RESOLUTION;
        }
        if (ticket.hasOutstandingResponseSla(now)) {
            return BREACH_TYPE_RESPONSE;
        }
        return null;
    }

    private void publishBreach(Ticket ticket, String breachType) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("ticketId", ticket.getId().toString());
        metadata.put("breachType", breachType);
        metadata.put("actorType", ACTOR_TYPE_SYSTEM);
        metadata.put("source", ACTOR_SOURCE_SCHEDULER);

        // No actorId: the breach was caused by a clock, not a person, and inventing a
        // user id would misattribute it. Identifiers only — no subject, description or
        // message content belongs on the bus.
        canonicalCrmEventPublisher.publish(
                ticket.getTenantId(),
                CanonicalCrmEvent.TICKET_ENTITY_TYPE,
                CanonicalCrmEvent.SLA_BREACHED_EVENT_TYPE,
                ticket.getId(),
                metadata);
    }
}
