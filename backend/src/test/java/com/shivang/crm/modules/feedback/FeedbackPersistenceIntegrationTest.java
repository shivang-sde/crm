package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackSpecifications;
import com.shivang.crm.modules.feedback.service.FeedbackIdentityResolver;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.3-P1: feedback persistence behaviour that mocks cannot prove.
 *
 * <p>The repository is driven against the migrated PostgreSQL schema: the ticket
 * filter (including the partial ticket index shape), tenant isolation, soft-delete
 * exclusion, and the email resolver's contact-first / ambiguity rules.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackPersistenceIntegrationTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackIdentityResolver identityResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;
    private UUID accountA;
    private UUID accountB;
    private UUID ticketA;
    private UUID ticketB;
    private UUID contactA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        accountA = UUID.randomUUID();
        accountB = UUID.randomUUID();
        ticketA = UUID.randomUUID();
        ticketB = UUID.randomUUID();
        contactA = UUID.randomUUID();

        insertTenant(tenantA, "Feedback Test A");
        insertTenant(tenantB, "Feedback Test B");
        insertUser(userA, tenantA, "feedback-a");
        insertUser(userB, tenantB, "feedback-b");
        insertAccount(accountA, tenantA, userA, "Account A", "shared@example.com");
        insertAccount(accountB, tenantA, userA, "Account B", "other@example.com");
        insertContact(contactA, tenantA, userA, accountA, "customer@example.com");
        insertTicket(ticketA, tenantA, userA, accountA, "Ticket A");
        insertTicket(ticketB, tenantA, userA, accountA, "Ticket B");
    }

    @Test
    void ticketFilterReturnsOnlyThatTicketsFeedbackInTheTenant() {
        saveFeedback(tenantA, accountA, ticketA, false);
        saveFeedback(tenantA, accountA, ticketA, false);
        saveFeedback(tenantA, accountA, ticketB, false);
        saveFeedback(tenantB, accountB, ticketA, false);

        List<Feedback> result = feedbackRepository.findAll(
                FeedbackSpecifications.byTenantId(tenantA)
                        .and(FeedbackSpecifications.notDeleted())
                        .and(FeedbackSpecifications.byTicketId(ticketA)));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(feedback -> feedback.getTicketId().equals(ticketA));
        assertThat(result).allMatch(feedback -> feedback.getTenantId().equals(tenantA));
    }

    @Test
    void softDeletedFeedbackIsExcludedFromTheTicketFilter() {
        saveFeedback(tenantA, accountA, ticketA, false);
        saveFeedback(tenantA, accountA, ticketA, true);

        List<Feedback> result = feedbackRepository.findAll(
                FeedbackSpecifications.byTenantId(tenantA)
                        .and(FeedbackSpecifications.notDeleted())
                        .and(FeedbackSpecifications.byTicketId(ticketA)));

        assertThat(result).hasSize(1);
    }

    @Test
    void aContactEmailResolvesToItsAccountAndContact() {
        var resolved = identityResolver.resolve(tenantA, "CUSTOMER@example.com");

        assertThat(resolved.accountId()).isEqualTo(accountA);
        assertThat(resolved.contactId()).isEqualTo(contactA);
    }

    @Test
    void anAccountOnlyEmailIsAcceptedWhenItIsUnambiguous() {
        var resolved = identityResolver.resolve(tenantA, "shared@example.com");

        assertThat(resolved.accountId()).isEqualTo(accountA);
        assertThat(resolved.contactId()).isNull();
    }

    @Test
    void severalAccountsSharingAnEmailAreRejectedAsAmbiguous() {
        insertAccount(UUID.randomUUID(), tenantA, userA, "Shared Account", "shared@example.com");

        Throwable thrown = catchThrowable(() -> identityResolver.resolve(tenantA, "shared@example.com"));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("FEEDBACK_CUSTOMER_AMBIGUOUS");
    }

    @Test
    void anUnknownEmailIsRejected() {
        Throwable thrown = catchThrowable(() -> identityResolver.resolve(tenantA, "missing@example.com"));

        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo("FEEDBACK_CUSTOMER_NOT_FOUND");
    }

    @Test
    void thePartialUniqueIndexRejectsAnActiveDuplicateReference() {
        Feedback first = new Feedback();
        first.setTenantId(tenantA);
        first.setOwnerId(userA);
        first.setCreatedBy(userA);
        first.setAccountId(accountA);
        first.setType(FeedbackType.CSAT);
        first.setScore(4);
        first.setSource(FeedbackSource.INTERNAL);
        first.setExternalReference("dup-ref");
        feedbackRepository.saveAndFlush(first);

        Feedback second = new Feedback();
        second.setTenantId(tenantA);
        second.setOwnerId(userA);
        second.setCreatedBy(userA);
        second.setAccountId(accountA);
        second.setType(FeedbackType.NPS);
        second.setScore(9);
        second.setSource(FeedbackSource.INTERNAL);
        second.setExternalReference("dup-ref");

        assertThatThrownBy(() -> feedbackRepository.saveAndFlush(second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private void saveFeedback(UUID tenantId, UUID accountId, UUID ticketId, boolean deleted) {
        Feedback feedback = new Feedback();
        feedback.setTenantId(tenantId);
        feedback.setOwnerId(userA);
        feedback.setCreatedBy(userA);
        feedback.setAccountId(accountId);
        feedback.setTicketId(ticketId);
        feedback.setType(FeedbackType.CSAT);
        feedback.setScore(4);
        feedback.setSource(FeedbackSource.INTERNAL);
        feedback.setDeleted(deleted);
        feedbackRepository.save(feedback);
    }

    private void insertTenant(UUID id, String name) {
        jdbcTemplate.update(
                "insert into tenants (id, name, slug, created_at, updated_at) values (?, ?, ?, now(), now())",
                id, name, "feedback-test-" + id);
    }

    private void insertUser(UUID id, UUID tenantId, String prefix) {
        jdbcTemplate.update(
                "insert into users (id, tenant_id, email, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, ?, now(), now())",
                id, tenantId, prefix + "-" + id + "@example.com", "not-used");
    }

    private void insertAccount(UUID id, UUID tenantId, UUID createdBy, String name, String email) {
        jdbcTemplate.update(
                "insert into accounts (id, tenant_id, created_by, name, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                id, tenantId, createdBy, name + " " + id, email);
    }

    private void insertContact(UUID id, UUID tenantId, UUID createdBy, UUID accountId, String email) {
        jdbcTemplate.update(
                "insert into contacts (id, tenant_id, created_by, account_id, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                id, tenantId, createdBy, accountId, email);
    }

    private void insertTicket(UUID id, UUID tenantId, UUID createdBy, UUID accountId, String subject) {
        jdbcTemplate.update(
                "insert into tickets (id, tenant_id, created_by, account_id, subject, status, deleted, "
                        + "created_at, updated_at) values (?, ?, ?, ?, ?, 'OPEN', false, now(), now())",
                id, tenantId, createdBy, accountId, subject);
    }
}


