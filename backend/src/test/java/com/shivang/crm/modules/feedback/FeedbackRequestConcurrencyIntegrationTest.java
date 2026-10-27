package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitRequest;
import com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitResponse;
import com.shivang.crm.modules.feedback.service.FeedbackRequestService;
import com.shivang.crm.modules.feedback.service.FeedbackTokenService;
import com.shivang.crm.shared.exception.BusinessException;

/**
 * L3.5: two simultaneous public submissions for one token must produce one
 * feedback row. The setup is committed deliberately - a test-managed transaction
 * would hide the request row from the competing threads.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeedbackRequestConcurrencyIntegrationTest {

    @Autowired
    private FeedbackRequestService feedbackRequestService;

    @Autowired
    private FeedbackTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID userId;
    private UUID accountId;
    private UUID contactId;
    private UUID ticketId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        ticketId = UUID.randomUUID();
        requestId = UUID.randomUUID();

        String rawToken = "concurrent-public-token";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            insertTenant();
            insertUser();
            insertAccount();
            insertContact();
            insertTicket();
            jdbcTemplate.update(
                    "insert into feedback_requests "
                            + "(id, tenant_id, ticket_id, account_id, contact_id, type, token_hash, "
                            + "expires_at, owner_user_id, created_by, created_at, updated_at, deleted) "
                            + "values (?, ?, ?, ?, ?, 'CSAT', ?, now() + interval '1 hour', ?, ?, now(), now(), false)",
                    requestId, tenantId, ticketId, accountId, contactId,
                    tokenService.hash(rawToken), userId, userId);
        });
    }

    @AfterEach
    void tearDown() {
        if (tenantId == null) {
            return;
        }
        jdbcTemplate.update("delete from crm_event_outbox where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from activities where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from feedback_requests where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from feedback where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from tickets where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from contacts where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from accounts where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from users where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from tenants where id = ?", tenantId);
    }

    @Test
    void simultaneousSubmissionsCreateExactlyOneFeedbackAndOneWinner() throws Exception {
        String rawToken = "concurrent-public-token";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Object> submission = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                return feedbackRequestService.submit(
                        rawToken, PublicFeedbackSubmitRequest.builder().score(5).build());
            } catch (BusinessException exception) {
                return exception;
            }
        };

        try {
            Future<Object> first = pool.submit(submission);
            Future<Object> second = pool.submit(submission);
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("both submissions should be ready before the race starts")
                    .isTrue();
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            outcomes.add(first.get(30, TimeUnit.SECONDS));
            outcomes.add(second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes)
                    .filteredOn(PublicFeedbackSubmitResponse.class::isInstance)
                    .as("exactly one submission wins the request lock")
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(BusinessException.class::isInstance)
                    .extracting(outcome -> ((BusinessException) outcome).getErrorCode())
                    .as("the loser observes an immutable submitted request")
                    .containsExactly("FEEDBACK_REQUEST_ALREADY_SUBMITTED");

            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from feedback where tenant_id = ?", Integer.class, tenantId))
                    .as("the request lock prevents a duplicate feedback row")
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select submitted_at is not null from feedback_requests where id = ?",
                    Boolean.class, requestId))
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    private void insertTenant() {
        jdbcTemplate.update(
                "insert into tenants (id, name, slug, created_at, updated_at) values (?, ?, ?, now(), now())",
                tenantId, "Feedback concurrency test " + tenantId, "feedback-concurrency-" + tenantId);
    }

    private void insertUser() {
        jdbcTemplate.update(
                "insert into users (id, tenant_id, email, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, ?, now(), now())",
                userId, tenantId, "feedback-concurrency-" + userId + "@example.com", "not-used");
    }

    private void insertAccount() {
        jdbcTemplate.update(
                "insert into accounts (id, tenant_id, created_by, name, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                accountId, tenantId, userId, "Concurrent account " + accountId,
                "concurrent-account-" + accountId + "@example.com");
    }

    private void insertContact() {
        jdbcTemplate.update(
                "insert into contacts (id, tenant_id, created_by, account_id, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                contactId, tenantId, userId, accountId,
                "concurrent-contact-" + contactId + "@example.com");
    }

    private void insertTicket() {
        jdbcTemplate.update(
                "insert into tickets (id, tenant_id, created_by, account_id, subject, status, deleted, "
                        + "created_at, updated_at) values (?, ?, ?, ?, ?, 'OPEN', false, now(), now())",
                ticketId, tenantId, userId, accountId, "Concurrent ticket " + ticketId);
    }
}
