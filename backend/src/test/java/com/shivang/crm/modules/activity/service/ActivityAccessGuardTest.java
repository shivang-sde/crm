package com.shivang.crm.modules.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.deal.service.DealService;
import com.shivang.crm.modules.lead.service.LeadService;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.1-P8: the guard delegates to the parent entity's own scope assertion.
 *
 * <p>The point is reuse rather than a parallel authorization model: each parent
 * already enforces RBAC-7 for its own module, and the guard's job is only to route to
 * the right one. Unsupported types fail closed.
 */
class ActivityAccessGuardTest {

    private AccountService accountService;
    private ContactService contactService;
    private DealService dealService;
    private LeadService leadService;
    private TicketService ticketService;
    private ActivityAccessGuard guard;

    private UUID tenantId;
    private UUID entityId;
    private UUID userId;

    @BeforeEach
    void setup() {
        accountService = mock(AccountService.class);
        contactService = mock(ContactService.class);
        dealService = mock(DealService.class);
        leadService = mock(LeadService.class);
        ticketService = mock(TicketService.class);
        guard = new ActivityAccessGuard(accountService, contactService, dealService,
                leadService, ticketService);

        tenantId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        userId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, java.util.List.of()));
    }

    private String codeOf(Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    @Test
    void accountRoutesToTheAccountAssertion() {
        guard.requireEntityReadable(tenantId, "ACCOUNT", entityId, "read");

        verify(accountService).assertAccountAccessible(tenantId, entityId, "read");
        verifyNoInteractions(contactService, dealService, leadService, ticketService);
    }

    @Test
    void contactRoutesToTheContactAssertion() {
        guard.requireEntityReadable(tenantId, "CONTACT", entityId, "read");

        verify(contactService).assertContactAccessible(tenantId, entityId, "read");
    }

    @Test
    void dealRoutesToTheDealAssertion() {
        guard.requireEntityReadable(tenantId, "DEAL", entityId, "read");

        verify(dealService).assertDealAccessible(tenantId, entityId, "read");
    }

    @Test
    void leadRoutesToTheLeadAssertion() {
        guard.requireEntityReadable(tenantId, "LEAD", entityId, "read");

        verify(leadService).assertLeadAccessible(tenantId, entityId, "read");
    }

    @Test
    void ticketRoutesToTheTicketTimelineAssertion() {
        guard.requireEntityReadable(tenantId, "TICKET", entityId, "read");

        // The same entry point GET /tickets/{id}/activities uses, so the generic and
        // Ticket-specific paths cannot disagree about Ticket access.
        verify(ticketService).requireReadable(entityId, tenantId, userId);
    }

    @Test
    void entityTypeIsMatchedCaseInsensitively() {
        guard.requireEntityReadable(tenantId, "deal", entityId, "read");

        verify(dealService).assertDealAccessible(tenantId, entityId, "read");
    }

    @Test
    void surroundingWhitespaceIsTolerated() {
        guard.requireEntityReadable(tenantId, " DEAL ", entityId, "read");

        verify(dealService).assertDealAccessible(tenantId, entityId, "read");
    }

    @Test
    void anUnsupportedEntityTypeFailsClosed() {
        Throwable thrown = catchThrowable(
                () -> guard.requireEntityReadable(tenantId, "CALL", entityId, "read"));

        assertThat(codeOf(thrown)).isEqualTo("ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED");
        // Nothing was authorized, so no activity may be served for it.
        verifyNoInteractions(accountService, contactService, dealService, leadService, ticketService);
    }

    @Test
    void aNullEntityTypeFailsClosed() {
        Throwable thrown = catchThrowable(
                () -> guard.requireEntityReadable(tenantId, null, entityId, "read"));

        assertThat(codeOf(thrown)).isEqualTo("ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED");
    }

    @Test
    void aTenantBlanketEntityTypeIsNotTreatedAsAuthorized() {
        // The vulnerability this closes: an entity type with no parent scope check
        // must not fall back to "the tenantId matched".
        for (String type : java.util.List.of("CALL", "TASK", "MEETING", "OFFERING", "ENTITLEMENT", "RECORD")) {
            Throwable thrown = catchThrowable(
                    () -> guard.requireEntityReadable(tenantId, type, entityId, "read"));
            assertThat(codeOf(thrown))
                    .as("%s must not be authorized generically", type)
                    .isEqualTo("ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED");
        }
    }

    @Test
    void theActionIsForwardedVerbatim() {
        guard.requireEntityReadable(tenantId, "ACCOUNT", entityId, "write");

        verify(accountService).assertAccountAccessible(tenantId, entityId, "write");
    }

    @Test
    void exactlyFiveEntityTypesAreSupported() {
        // A new type is a deliberate decision, not something that starts working
        // because it happened to appear in a query.
        assertThat(java.util.Arrays.stream(ActivityAccessGuard.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder("requireEntityReadable", "currentUserId");

        for (String type : java.util.List.of("ACCOUNT", "CONTACT", "DEAL", "LEAD", "TICKET")) {
            guard.requireEntityReadable(tenantId, type, entityId, "read");
        }
    }

    private static final class List<T> extends java.util.ArrayList<T> {
    }
}
