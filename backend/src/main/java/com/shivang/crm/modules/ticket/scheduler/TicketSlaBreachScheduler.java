package com.shivang.crm.modules.ticket.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.ticket.config.TicketSlaProperties;
import com.shivang.crm.modules.ticket.service.TicketSlaBreachService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.1-P4: periodic SLA breach detection.
 *
 * <p>Mirrors {@code EntitlementLifecycleScheduler}: a property-gated
 * {@code @Scheduled} method delegating to a service that holds the logic. The
 * scheduler itself has no tenant, transaction or event concern.
 *
 * <p>No distributed lock is introduced. Correctness under concurrent runs comes from
 * the {@code slaBreached} column being the idempotency boundary and from the candidate
 * query only selecting unbreached rows, so a duplicate run finds no work.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketSlaBreachScheduler {

    private final TicketSlaBreachService ticketSlaBreachService;
    private final TicketSlaProperties ticketSlaProperties;

    @Scheduled(
            fixedDelayString = "${app.tickets.sla.breach-scan-delay-ms:60000}",
            initialDelayString = "${app.tickets.sla.breach-scan-delay-ms:60000}")
    public void scanForSlaBreaches() {
        if (!ticketSlaProperties.isBreachScanEnabled()) {
            return;
        }
        int breached = ticketSlaBreachService.detectBreaches();
        if (breached > 0) {
            log.debug("Ticket SLA breach scan marked {} tickets", breached);
        }
    }
}
