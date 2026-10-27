package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitRequest;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitResponse;
import com.shivang.crm.modules.feedback.entity.FeedbackRequest;
import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackRequestRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackSpecifications;
import com.shivang.crm.modules.feedback.service.FeedbackRequestService;
import com.shivang.crm.modules.feedback.service.FeedbackTokenService;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.5: request persistence and public-submission behaviour against the migrated
 * PostgreSQL schema. These are the facts mocks cannot prove: token uniqueness,
 * soft-delete/tenant filtering, the live-pending predicate, row locking, and
 * that the public path writes through the canonical feedback service.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackRequestPersistenceIntegrationTest {

    @Autowired
    private FeedbackRequestRepository requestRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private FeedbackRequestService feedbackRequestService;

    @Autowired
    private FeedbackTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID userId;
    private UUID accountId;
    private UUID contactId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        insertTenant(tenantId);
        insertUser(userId, tenantId);
        insertAccount(accountId, tenantId, userId);
        insertContact(contactId, tenantId, userId, accountId);
        insertTicket(ticketId, tenantId, userId, accountId);
    }

    @Test
    void tokenHashIsGloballyUnique() {
        String tokenHash = "a".repeat(64);
        saveRequest(tokenHash, Instant.now().plusSeconds(3600), null, null, false);

        assertThatThrownBy(() -> saveRequest(
                tokenHash, Instant.now().plusSeconds(3600), null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void softDeletedRequestsAreNotResolvedByToken() {
        FeedbackRequest live = saveRequest(
                "live-token", Instant.now().plusSeconds(3600), null, null, false);
        saveRequest("deleted-token", Instant.now().plusSeconds(3600), null, null, true);

        assertThat(requestRepository.findByTokenHashAndDeletedFalse("live-token"))
                .contains(live);
        assertThat(requestRepository.findByTokenHashAndDeletedFalse("deleted-token")).isEmpty();
    }

    @Test
    void onlyAnUnexpiredUnrevokedUnsubmittedRequestIsLivePending() {
        Instant now = Instant.now();
        FeedbackRequest pending = saveRequest(
                "pending-token", now.plusSeconds(3600), null, null, false);
        saveRequest("expired-token", now.minusSeconds(60), null, null, false);
        saveRequest("revoked-token", now.plusSeconds(3600), null, now.minusSeconds(60), false);
        saveRequest("submitted-token", now.plusSeconds(3600), now.minusSeconds(30), null, false);
        saveRequest("deleted-pending-token", now.plusSeconds(3600), null, null, true);

        List<FeedbackRequest> live = requestRepository.findLivePending(tenantId, ticketId, now);

        assertThat(live).extracting(FeedbackRequest::getId).containsExactly(pending.getId());
    }

    @Test
    void lockedLookupsAreScopedToTheTenantAndTicket() {
        FeedbackRequest request = saveRequest(
                "locked-token", Instant.now().plusSeconds(3600), null, null, false);

        assertThat(requestRepository.findForUpdate(request.getId(), tenantId, ticketId))
                .contains(request);
        assertThat(requestRepository.findForUpdate(request.getId(), UUID.randomUUID(), ticketId))
                .isEmpty();
        assertThat(requestRepository.findForUpdate(request.getId(), tenantId, UUID.randomUUID()))
                .isEmpty();
        assertThat(requestRepository.findByTokenHashAndDeletedFalseForUpdate("locked-token"))
                .contains(request);
    }

    @Test
    void theTicketSnapshotAndDerivedStatusArePersisted() {
        FeedbackRequest request = saveRequest(
                "snapshot-token", Instant.now().plusSeconds(3600), null, null, false);
        requestRepository.flush();

        FeedbackRequest loaded = requestRepository.findById(request.getId()).orElseThrow();

        assertThat(loaded.getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.getTicketId()).isEqualTo(ticketId);
        assertThat(loaded.getAccountId()).isEqualTo(accountId);
        assertThat(loaded.getContactId()).isEqualTo(contactId);
        assertThat(loaded.getType()).isEqualTo(FeedbackType.CSAT);
        assertThat(loaded.statusAt(Instant.now())).isEqualTo(FeedbackRequestStatus.PENDING);
    }

    @Test
    void aPublicSubmissionCreatesOneWebsiteFeedbackVisibleToTheTicketFilter() {
        String rawToken = "public-token";
        FeedbackRequest request = saveRequest(
                tokenService.hash(rawToken),
                Instant.now().plusSeconds(3600), null, null, false);

        PublicFeedbackSubmitResponse response = feedbackRequestService.submit(
                rawToken,
                PublicFeedbackSubmitRequest.builder()
                        .score(2)
                        .comment("The response was slow")
                        .build());

        assertThat(response.getStatus()).isEqualTo(FeedbackRequestStatus.SUBMITTED);

        var feedback = feedbackRepository.findAll(
                FeedbackSpecifications.byTenantId(tenantId)
                        .and(FeedbackSpecifications.notDeleted())
                        .and(FeedbackSpecifications.byTicketId(ticketId)));
        assertThat(feedback).hasSize(1);
        assertThat(feedback.get(0).getAccountId()).isEqualTo(accountId);
        assertThat(feedback.get(0).getContactId()).isEqualTo(contactId);
        assertThat(feedback.get(0).getSource()).isEqualTo(FeedbackSource.WEBSITE);
        assertThat(feedback.get(0).getExternalReference())
                .isEqualTo("feedback-request:" + request.getId());
        assertThat(FeedbackSource.WEBSITE).isNotEqualTo(FeedbackSource.INTERNAL);

        FeedbackRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSubmittedAt()).isNotNull();
        assertThat(reloaded.getFeedbackId()).isEqualTo(feedback.get(0).getId());
    }

    @Test
    void aRepeatedPublicSubmissionIsRejectedAndCreatesNoSecondFeedback() {
        String rawToken = "single-use-token";
        FeedbackRequest request = saveRequest(
                tokenService.hash(rawToken),
                Instant.now().plusSeconds(3600), null, null, false);

        feedbackRequestService.submit(
                rawToken, PublicFeedbackSubmitRequest.builder().score(2).build());

        BusinessException thrown = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> feedbackRequestService.submit(
                        rawToken, PublicFeedbackSubmitRequest.builder().score(2).build()),
                BusinessException.class);

        assertThat(thrown.getErrorCode()).isEqualTo("FEEDBACK_REQUEST_ALREADY_SUBMITTED");
        assertThat(feedbackRepository.findAll(
                FeedbackSpecifications.byTenantId(tenantId)
                        .and(FeedbackSpecifications.byTicketId(ticketId))))
                .hasSize(1);
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getSubmittedAt())
                .isNotNull();
    }

    private FeedbackRequest saveRequest(
            String tokenHash, Instant expiresAt, Instant submittedAt, Instant revokedAt, boolean deleted) {
        FeedbackRequest request = new FeedbackRequest();
        request.setTenantId(tenantId);
        request.setOwnerId(userId);
        request.setCreatedBy(userId);
        request.setUpdatedBy(userId);
        request.setTicketId(ticketId);
        request.setAccountId(accountId);
        request.setContactId(contactId);
        request.setType(FeedbackType.CSAT);
        request.setTokenHash(tokenHash);
        request.setExpiresAt(expiresAt);
        request.setSubmittedAt(submittedAt);
        request.setRevokedAt(revokedAt);
        request.setDeleted(deleted);
        return requestRepository.saveAndFlush(request);
    }

    private void insertTenant(UUID id) {
        jdbcTemplate.update(
                "insert into tenants (id, name, slug, created_at, updated_at) values (?, ?, ?, now(), now())",
                id, "Feedback request test " + id, "feedback-request-test-" + id);
    }

    private void insertUser(UUID id, UUID tenant) {
        jdbcTemplate.update(
                "insert into users (id, tenant_id, email, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, ?, now(), now())",
                id, tenant, "feedback-request-" + id + "@example.com", "not-used");
    }

    private void insertAccount(UUID id, UUID tenant, UUID createdBy) {
        jdbcTemplate.update(
                "insert into accounts (id, tenant_id, created_by, name, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                id, tenant, createdBy, "Feedback request account " + id, "account-" + id + "@example.com");
    }

    private void insertContact(UUID id, UUID tenant, UUID createdBy, UUID account) {
        jdbcTemplate.update(
                "insert into contacts (id, tenant_id, created_by, account_id, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                id, tenant, createdBy, account, "contact-" + id + "@example.com");
    }

    private void insertTicket(UUID id, UUID tenant, UUID createdBy, UUID account) {
        jdbcTemplate.update(
                "insert into tickets (id, tenant_id, created_by, account_id, subject, status, deleted, "
                        + "created_at, updated_at) values (?, ?, ?, ?, ?, 'OPEN', false, now(), now())",
                id, tenant, createdBy, account, "Feedback request ticket " + id);
    }
}
