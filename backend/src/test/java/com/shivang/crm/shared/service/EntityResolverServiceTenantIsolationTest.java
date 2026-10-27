package com.shivang.crm.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.0-P2: EntityResolverService must never surface another tenant's customer
 * record, nor a soft-deleted one, as a live polymorphic link target.
 */
class EntityResolverServiceTenantIsolationTest {

    private LeadRepository leadRepository;
    private ContactRepository contactRepository;
    private AccountRepository accountRepository;
    private DealRepository dealRepository;
    private CustomerEntitlementRepository entitlementRepository;
    private TicketRepository ticketRepository;
    private UserRepository userRepository;
    private EntityResolverService resolver;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setup() {
        leadRepository = mock(LeadRepository.class);
        contactRepository = mock(ContactRepository.class);
        accountRepository = mock(AccountRepository.class);
        dealRepository = mock(DealRepository.class);
        entitlementRepository = mock(CustomerEntitlementRepository.class);
        ticketRepository = mock(TicketRepository.class);
        userRepository = mock(UserRepository.class);
        resolver = new EntityResolverService(
                userRepository, leadRepository, contactRepository,
                accountRepository, dealRepository, entitlementRepository, ticketRepository);

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
    }

    // --- validateEntityExists ---

    private void assertCode(String expectedCode, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expectedCode);
    }

    @Test
    void sameTenantActiveEntityValidatesForEveryBranch() {
        for (String type : new String[] {"LEAD", "CONTACT", "ACCOUNT", "DEAL", "ENTITLEMENT"}) {
            UUID id = UUID.randomUUID();
            registerExists(type, id, tenantA);
            assertThatNoException()
                    .as(type)
                    .isThrownBy(() -> resolver.validateEntityExists(type, id, tenantA));
        }
    }

    @Test
    void crossTenantEntityFailsValidationForEveryBranch() {
        for (String type : new String[] {"LEAD", "CONTACT", "ACCOUNT", "DEAL", "ENTITLEMENT"}) {
            UUID id = UUID.randomUUID();
            // Only resolvable under tenantB; tenantA sees nothing.
            registerExists(type, id, tenantB);

            assertCode("ENTITY_NOT_FOUND", () -> resolver.validateEntityExists(type, id, tenantA));
        }
    }

    @Test
    void deletedEntityFailsValidationForEveryBranch() {
        for (String type : new String[] {"LEAD", "CONTACT", "ACCOUNT", "DEAL", "ENTITLEMENT"}) {
            // Unstubbed mocks return false for the deletedFalse existence probe,
            // which is exactly what a soft-deleted row yields.
            assertCode("ENTITY_NOT_FOUND", () -> resolver.validateEntityExists(type, UUID.randomUUID(), tenantA));
        }
    }

    @Test
    void nullEntityReferenceIsAllowed() {
        assertThat(catchThrowable(() -> resolver.validateEntityExists("ACCOUNT", null, tenantA))).isNull();
    }

/**
     * L3.1-P1 changed this expectation deliberately. TICKET used to be asserted
     * unsupported; it is now a registered customer-domain entity type, so the
     * unknown-type case must use a type that genuinely is not registered.
     */
    @Test
    void unknownEntityTypeRejected() {
        assertCode("INVALID_ENTITY_TYPE",
                () -> resolver.validateEntityExists("WIDGET", UUID.randomUUID(), tenantA));
    }

    // --- L3.1-P1: Ticket is a registered entity type, and inherits the same
    //     tenant-scoped, deleted-aware resolution guarantees as every other type ---

    @Test
    void ticketIsResolvedOnlyWithinItsOwnTenant() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsByIdAndTenantIdAndDeletedFalse(ticketId, tenantA)).thenReturn(true);

        assertDoesNotThrow(() -> resolver.validateEntityExists("TICKET", ticketId, tenantA));
        // Tenant B sees nothing: the lookup is tenant-scoped, not existence-scoped.
        assertCode("ENTITY_NOT_FOUND",
                () -> resolver.validateEntityExists("TICKET", ticketId, tenantB));
    }

    @Test
    void softDeletedTicketIsNotAValidLinkTarget() {
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.existsByIdAndTenantIdAndDeletedFalse(ticketId, tenantA)).thenReturn(false);

        assertCode("ENTITY_NOT_FOUND",
                () -> resolver.validateEntityExists("TICKET", ticketId, tenantA));
    }

    @Test
    void ticketResolvesToItsSubjectWithinItsOwnTenant() {
        UUID ticketId = UUID.randomUUID();
        var ticket = new com.shivang.crm.modules.ticket.entity.Ticket();
        ticket.setId(ticketId);
        ticket.setTenantId(tenantA);
        ticket.setSubject("Customer cannot access dashboard");
        when(ticketRepository.findByIdAndTenantIdAndDeletedFalse(ticketId, tenantA))
                .thenReturn(Optional.of(ticket));

        assertThat(resolver.resolveEntityName("TICKET", ticketId, tenantA))
                .isEqualTo("Customer cannot access dashboard");
        assertThat(resolver.resolveEntityName("TICKET", ticketId, tenantB)).isNull();
    }

    // --- resolveEntityName ---

    @Test
    void resolvesSameTenantActiveNames() {
        UUID leadId = liveLead("Alice", "Smith", tenantA);
        UUID contactId = liveContact("Bob", "Jones", tenantA);
        UUID accountId = liveAccount("Acme", tenantA);
        UUID dealId = liveDeal("Big Deal", tenantA);
        UUID entitlementId = liveEntitlement("Support Plan", tenantA);

        assertThat(resolver.resolveEntityName("LEAD", leadId, tenantA)).isEqualTo("Alice Smith");
        assertThat(resolver.resolveEntityName("CONTACT", contactId, tenantA)).isEqualTo("Bob Jones");
        assertThat(resolver.resolveEntityName("ACCOUNT", accountId, tenantA)).isEqualTo("Acme");
        assertThat(resolver.resolveEntityName("DEAL", dealId, tenantA)).isEqualTo("Big Deal");
        assertThat(resolver.resolveEntityName("ENTITLEMENT", entitlementId, tenantA)).isEqualTo("Support Plan");
    }

    @Test
    void doesNotResolveCrossTenantNames() {
        UUID leadId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();

        // Records exist, but only for tenantB.
        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setTenantId(tenantB);
        lead.setFirstName("Alice");
        lead.setLastName("Smith");
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(leadId, tenantB)).thenReturn(Optional.of(lead));

        Contact contact = new Contact();
        contact.setId(contactId);
        contact.setTenantId(tenantB);
        contact.setFirstName("Bob");
        contact.setEmail("bob@other.example");
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(contactId, tenantB)).thenReturn(Optional.of(contact));

        Account account = new Account();
        account.setId(accountId);
        account.setTenantId(tenantB);
        account.setName("OtherTenantAcme");
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantB))
                .thenReturn(Optional.of(account));

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setTenantId(tenantB);
        deal.setName("OtherTenantDeal");
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(dealId, tenantB)).thenReturn(Optional.of(deal));

        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(entitlementId);
        entitlement.setTenantId(tenantB);
        entitlement.setName("OtherTenantPlan");
        when(entitlementRepository.findByIdAndTenantIdAndDeletedFalse(entitlementId, tenantB))
                .thenReturn(Optional.of(entitlement));

        assertThat(resolver.resolveEntityName("LEAD", leadId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("CONTACT", contactId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("ACCOUNT", accountId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("DEAL", dealId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("ENTITLEMENT", entitlementId, tenantA)).isNull();
    }

    @Test
    void doesNotResolveDeletedNames() {
        UUID leadId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();

        // All deleted: the tenant-scoped + deletedFalse lookups return nothing.
        assertThat(resolver.resolveEntityName("LEAD", leadId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("CONTACT", contactId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("ACCOUNT", accountId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("DEAL", dealId, tenantA)).isNull();
        assertThat(resolver.resolveEntityName("ENTITLEMENT", entitlementId, tenantA)).isNull();
    }

/**
     * L3.1-P1 changed this expectation deliberately. TICKET is registered now, so
     * an unknown type must use a type that is genuinely unregistered.
     */
    @Test
    void unknownEntityTypeResolvesToNull() {
        assertThat(resolver.resolveEntityName("WIDGET", UUID.randomUUID(), tenantA)).isNull();
    }

    @Test
    void nullReferenceResolvesToNull() {
        assertThat(resolver.resolveEntityName(null, UUID.randomUUID(), tenantA)).isNull();
        assertThat(resolver.resolveEntityName("ACCOUNT", null, tenantA)).isNull();
    }

    // --- helpers ---

    private void registerExists(String type, UUID id, UUID tenant) {
        final java.util.function.BiPredicate<UUID, UUID> predicate =
                (requestedId, requestedTenant) -> requestedTenant.equals(tenant) && requestedId.equals(id);

        switch (type) {
            case "LEAD" ->
                when(leadRepository.existsByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                    .thenAnswer(inv -> predicate.test(inv.getArgument(0), inv.getArgument(1)));
            case "CONTACT" ->
                when(contactRepository.existsByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                    .thenAnswer(inv -> predicate.test(inv.getArgument(0), inv.getArgument(1)));
            case "ACCOUNT" ->
                when(accountRepository.existsByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                    .thenAnswer(inv -> predicate.test(inv.getArgument(0), inv.getArgument(1)));
            case "DEAL" ->
                when(dealRepository.existsByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                    .thenAnswer(inv -> predicate.test(inv.getArgument(0), inv.getArgument(1)));
            case "ENTITLEMENT" ->
                when(entitlementRepository.existsByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                    .thenAnswer(inv -> predicate.test(inv.getArgument(0), inv.getArgument(1)));
            default -> throw new IllegalArgumentException(type);
        }
    }

    private UUID liveLead(String first, String last, UUID tenant) {
        UUID id = UUID.randomUUID();
        Lead lead = new Lead();
        lead.setId(id);
        lead.setTenantId(tenant);
        lead.setFirstName(first);
        lead.setLastName(last);
        when(leadRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(lead));
        return id;
    }

    private UUID liveContact(String first, String last, UUID tenant) {
        UUID id = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(id);
        contact.setTenantId(tenant);
        contact.setFirstName(first);
        contact.setLastName(last);
        when(contactRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(contact));
        return id;
    }

    private UUID liveAccount(String name, UUID tenant) {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id);
        account.setTenantId(tenant);
        account.setName(name);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(account));
        return id;
    }

    private UUID liveDeal(String name, UUID tenant) {
        UUID id = UUID.randomUUID();
        Deal deal = new Deal();
        deal.setId(id);
        deal.setTenantId(tenant);
        deal.setName(name);
        when(dealRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant)).thenReturn(Optional.of(deal));
        return id;
    }

    private UUID liveEntitlement(String name, UUID tenant) {
        UUID id = UUID.randomUUID();
        CustomerEntitlement entitlement = new CustomerEntitlement();
        entitlement.setId(id);
        entitlement.setTenantId(tenant);
        entitlement.setName(name);
        when(entitlementRepository.findByIdAndTenantIdAndDeletedFalse(id, tenant))
                .thenReturn(Optional.of(entitlement));
        return id;
    }
}