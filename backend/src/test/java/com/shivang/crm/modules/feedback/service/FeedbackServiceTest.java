package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.mapper.FeedbackMapper;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.service.FeedbackIdentityResolver.ResolvedCustomer;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

/**
 * L3.3-P1: Feedback submission rules — score validation, identity ownership,
 * external-reference idempotency, ticket correlation, and canonical event emission.
 */
class FeedbackServiceTest {

    private FeedbackRepository feedbackRepository;
    private FeedbackIdentityResolver identityResolver;
    private AccountRepository accountRepository;
    private FeedbackMapper feedbackMapper;
    private CustomerRelationshipValidator relationshipValidator;
    private RecordScopeGuard recordScopeGuard;
    private CanonicalCrmEventPublisher canonicalEventPublisher;
    private com.shivang.crm.modules.activity.service.ActivityService activityService;
    private FeedbackService service;

    private UUID tenantId;
    private UUID callerId;
    private UUID accountId;

    private final ResolvedCustomer customer;

    FeedbackServiceTest() {
        accountId = UUID.randomUUID();
        customer = new ResolvedCustomer(accountId, null);
    }

    @BeforeEach
    void setup() throws Exception {
        feedbackRepository = mock(FeedbackRepository.class);
        identityResolver = mock(FeedbackIdentityResolver.class);
        accountRepository = mock(AccountRepository.class);
        feedbackMapper = mock(FeedbackMapper.class);
        relationshipValidator = mock(CustomerRelationshipValidator.class);
        recordScopeGuard = mock(RecordScopeGuard.class);
        canonicalEventPublisher = mock(CanonicalCrmEventPublisher.class);
        activityService = mock(com.shivang.crm.modules.activity.service.ActivityService.class);
        service = new FeedbackService(
                feedbackRepository, identityResolver, accountRepository, feedbackMapper,
                relationshipValidator, recordScopeGuard, canonicalEventPublisher, activityService);

        tenantId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        when(identityResolver.resolve(eq(tenantId), anyString())).thenReturn(customer);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(eq(accountId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(accountOwner()));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(Instant.now());
            return f;
        });
        when(feedbackMapper.toResponse(any(Feedback.class))).thenAnswer(inv -> {
            Feedback f = inv.getArgument(0);
            return FeedbackResponse.builder()
                    .id(f.getId()).accountId(f.getAccountId()).contactId(f.getContactId())
                    .ticketId(f.getTicketId()).type(f.getType()).score(f.getScore())
                    .comment(f.getComment()).source(f.getSource())
                    .externalReference(f.getExternalReference()).ownerUserId(f.getOwnerId())
                    .createdAt(f.getCreatedAt())
                    .build();
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    private Account accountOwner() {
        Account account = new Account();
        account.setId(accountId);
        account.setOwnerId(UUID.randomUUID());
        return account;
    }

    private FeedbackCreateRequest request(FeedbackType type, Integer score) {
        return FeedbackCreateRequest.builder()
                .email("customer@example.com")
                .type(type)
                .score(score)
                .source(FeedbackSource.INTERNAL)
                .build();
    }

    private String codeOf(Throwable thrown) {
        return ((BusinessException) thrown).getErrorCode();
    }

    // ================= Score validation =================

    @Test
    void npsAcceptsTheFullZeroToTenRange() {
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.NPS, 0))).isNotNull();
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.NPS, 10))).isNotNull();
    }

    @Test
    void npsRejectsOutOfRangeAndMissing() {
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.NPS, -1)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.NPS, 11)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.NPS, null)))))
                .isEqualTo("FEEDBACK_SCORE_REQUIRED");
    }

    @Test
    void csatRequiresOneToFiveWithoutClamping() {
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.CSAT, 1))).isNotNull();
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.CSAT, 5))).isNotNull();
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.CSAT, 0)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.CSAT, 6)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.CSAT, null)))))
                .isEqualTo("FEEDBACK_SCORE_REQUIRED");
    }

    @Test
    void cesRequiresOneToSevenWithoutClamping() {
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.CES, 1))).isNotNull();
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.CES, 7))).isNotNull();
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.CES, 0)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, request(FeedbackType.CES, 8)))))
                .isEqualTo("FEEDBACK_SCORE_OUT_OF_RANGE");
    }

    @Test
    void generalFeedbackAndComplaintAllowANullScore() {
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.GENERAL_FEEDBACK, null))).isNotNull();
        assertThat(service.submit(tenantId, callerId, request(FeedbackType.COMPLAINT, null))).isNotNull();
    }

    @Test
    void anInvalidScoreLeadsToNoEventAndNoPersistence() {
        catchThrowable(() -> service.submit(tenantId, callerId, request(FeedbackType.NPS, 11)));

        verifyNoInteractions(feedbackRepository, canonicalEventPublisher, activityService);
    }

    // ================= Ownership =================

    @Test
    void ownershipIsInheritedFromTheAccount() {
        UUID accountOwner = UUID.randomUUID();
        Account rich = accountOwner();
        rich.setOwnerId(accountOwner);
        when(accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId))
                .thenReturn(java.util.Optional.of(rich));

        service.submit(tenantId, callerId, request(FeedbackType.NPS, 9));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(accountOwner);
    }

    // ================= Provenance and staff account context =================

    @Test
    void staffSubmissionWithoutASourceIsRecordedAsInternal() {
        FeedbackCreateRequest r = request(FeedbackType.CSAT, 4);
        r.setSource(null);

        service.submit(tenantId, callerId, r);

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(FeedbackSource.INTERNAL);
    }

    @Test
    void staffCannotClaimCustomerProvenance() {
        FeedbackCreateRequest r = request(FeedbackType.CSAT, 4);
        r.setSource(FeedbackSource.WEBSITE);

        assertThat(codeOf(catchThrowable(() -> service.submit(tenantId, callerId, r))))
                .isEqualTo("FEEDBACK_SOURCE_NOT_PERMITTED");
        verifyNoInteractions(feedbackRepository, canonicalEventPublisher, activityService);
    }

    @Test
    void trustedIntegrationMayPreserveItsDeclaredSource() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        callerId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION"))));

        FeedbackCreateRequest r = request(FeedbackType.CSAT, 4);
        r.setSource(FeedbackSource.WEBSITE);

        service.submit(tenantId, callerId, r);

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(FeedbackSource.WEBSITE);
    }

    @Test
    void aMismatchedExpectedAccountIsRejectedBeforePersistence() {
        FeedbackCreateRequest r = request(FeedbackType.CSAT, 4);
        r.setExpectedAccountId(UUID.randomUUID());

        assertThat(codeOf(catchThrowable(() -> service.submit(tenantId, callerId, r))))
                .isEqualTo("FEEDBACK_ACCOUNT_MISMATCH");
        verifyNoInteractions(feedbackRepository, canonicalEventPublisher, activityService);
    }

    @Test
    void aMatchingExpectedAccountIsAccepted() {
        FeedbackCreateRequest r = request(FeedbackType.CSAT, 4);
        r.setExpectedAccountId(accountId);

        assertThat(service.submit(tenantId, callerId, r)).isNotNull();
    }

    // ================= Ticket correlation =================

    @Test
    void aTicketOutsideTheResolvedAccountIsRejected() {
        FeedbackCreateRequest ticketRequest = request(FeedbackType.CSAT, 4);
        UUID foreignTicket = UUID.randomUUID();
        ticketRequest.setTicketId(foreignTicket);

        org.mockito.Mockito.doThrow(new BusinessException("TICKET_ACCOUNT_MISMATCH", "mismatch"))
                .when(relationshipValidator)
                .requireTicketBelongsToAccount(foreignTicket, accountId, tenantId);

        assertThat(codeOf(catchThrowable(() ->
                service.submit(tenantId, callerId, ticketRequest))))
                .isEqualTo("TICKET_ACCOUNT_MISMATCH");
        verifyNoInteractions(feedbackRepository, canonicalEventPublisher, activityService);
    }

    @Test
    void aFeedbackWithoutATicketIsAllowed() {
        service.submit(tenantId, callerId, request(FeedbackType.GENERAL_FEEDBACK, null));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getTicketId()).isNull();
    }

    // ================= External-reference idempotency =================

    @Test
    void aDuplicateExternalReferenceReturnsTheExistingRecord() {
        Feedback existing = new Feedback();
        existing.setId(UUID.randomUUID());
        existing.setAccountId(accountId);
        when(feedbackRepository.findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
                eq(tenantId), eq(FeedbackSource.INTERNAL), eq("resp-1")))
                .thenReturn(java.util.Optional.of(existing));

        FeedbackCreateRequest r = request(FeedbackType.CSAT, 5);
        r.setExternalReference("resp-1");

        service.submit(tenantId, callerId, r);

        // Not a new row, no event: the client already has the accepted record.
        verify(feedbackRepository, org.mockito.Mockito.never()).save(any(Feedback.class));
        verifyNoInteractions(canonicalEventPublisher, activityService);
    }

    @Test
    void aDuplicateReferenceCannotReturnAnotherCustomersFeedback() {
        Feedback existing = new Feedback();
        existing.setId(UUID.randomUUID());
        existing.setAccountId(UUID.randomUUID());
        when(feedbackRepository.findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
                eq(tenantId), eq(FeedbackSource.INTERNAL), eq("resp-1")))
                .thenReturn(java.util.Optional.of(existing));

        FeedbackCreateRequest r = request(FeedbackType.CSAT, 5);
        r.setExternalReference("resp-1");

        assertThat(codeOf(catchThrowable(() -> service.submit(tenantId, callerId, r))))
                .isEqualTo("FEEDBACK_EXTERNAL_REFERENCE_CONFLICT");
        verify(feedbackRepository, org.mockito.Mockito.never()).save(any(Feedback.class));
    }

    @Test
    void aDifferentSourceOrReferenceKeepsDistinctSubmissions() {
        FeedbackCreateRequest r = request(FeedbackType.CSAT, 5);
        r.setExternalReference("resp-2");
        when(feedbackRepository.findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
                eq(tenantId), eq(FeedbackSource.INTERNAL), eq("resp-2")))
                .thenReturn(java.util.Optional.empty());

        service.submit(tenantId, callerId, r);
        verify(feedbackRepository, org.mockito.Mockito.times(1)).save(any(Feedback.class));
    }

    // ================= Canonical event =================

    @Test
    void exactlyOneReceivedEventGoesThroughTheOutbox() {
        service.submit(tenantId, callerId, request(FeedbackType.CSAT, 4));

        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(canonicalEventPublisher).publish(
                eq(tenantId), entityType.capture(), eventType.capture(), any(UUID.class), anyMap());
        assertThat(entityType.getValue()).isEqualTo("FEEDBACK");
        assertThat(eventType.getValue()).isEqualTo("RECEIVED");
    }

    @Test
    void eventMetadataCarriesTheScoredShape() {
        service.submit(tenantId, callerId, request(FeedbackType.CSAT, 3));

        ArgumentCaptor<java.util.Map> meta = ArgumentCaptor.forClass(java.util.Map.class);
        verify(canonicalEventPublisher).publish(
                any(), any(), any(), any(), meta.capture());
        assertThat(meta.getValue())
                .containsKey("type")
                .containsKey("score")
                .containsKey("accountId");
    }

    // ================= Timeline activity =================

    @Test
    void submissionWritesAFeedbackReceivedActivity() {
        // L3.3-P3: the unified Customer 360 timeline reads the activities table, so
        // without this row a submitted feedback would never appear in it.
        service.submit(tenantId, callerId, request(FeedbackType.CSAT, 4));

        ArgumentCaptor<String> entityType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> activityType = ArgumentCaptor.forClass(String.class);
        verify(activityService).logActivity(
                eq(tenantId), any(UUID.class), entityType.capture(), activityType.capture(),
                any(String.class), eq(callerId), anyMap());
        assertThat(entityType.getValue()).isEqualTo("FEEDBACK");
        assertThat(activityType.getValue()).isEqualTo("FEEDBACK_RECEIVED");
    }

    @Test
    void theActivityCarriesTheFeedbackIdentity() {
        service.submit(tenantId, callerId, request(FeedbackType.NPS, 9));

        ArgumentCaptor<java.util.Map> meta = ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityService).logActivity(
                any(), any(), any(), any(), any(), any(), meta.capture());
        assertThat(meta.getValue())
                .containsKey("type")
                .containsKey("score")
                .containsKey("accountId");
    }

    @Test
    void aDeduplicatedResubmissionWritesNoSecondActivity() {
        Feedback existing = new Feedback();
        existing.setId(UUID.randomUUID());
        existing.setAccountId(accountId);
        when(feedbackRepository.findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
                eq(tenantId), eq(FeedbackSource.INTERNAL), eq("resp-1")))
                .thenReturn(java.util.Optional.of(existing));

        FeedbackCreateRequest r = request(FeedbackType.CSAT, 5);
        r.setExternalReference("resp-1");

        service.submit(tenantId, callerId, r);

        // The accepted record already has its activity; a resubmission must not
        // duplicate it, or the timeline would show the same feedback twice.
        verifyNoInteractions(activityService);
    }

    @Test
    void aDomainFailurePublishesNoEvent() {
        when(identityResolver.resolve(eq(tenantId), anyString()))
                .thenThrow(new BusinessException("FEEDBACK_CUSTOMER_NOT_FOUND", "no such customer"));

        catchThrowable(() -> service.submit(tenantId, callerId, request(FeedbackType.CSAT, 4)));

        verifyNoInteractions(feedbackRepository, canonicalEventPublisher, activityService);
    }

    // ================= RBAC record scope (read path) =================

    @Test
    void listScopeDefaultsToTheCallerService() {
        var params = com.shivang.crm.modules.feedback.dto.FeedbackListParams.builder().build();
        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenReturn("ALL");
        org.springframework.data.domain.Page<Feedback> empty =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(feedbackRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Feedback>>any(),
                org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any()))
                .thenReturn(empty);

        service.list(tenantId, params);

        verify(recordScopeGuard).requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read"));
    }

    @Test
    void getByIdReportsNotFoundForAForeignOrScopelessRow() {
        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenReturn("OWN");
        when(feedbackRepository.findOne(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Feedback>>any()))
                .thenReturn(java.util.Optional.empty());

        assertThat(codeOf(catchThrowable(() -> service.getById(tenantId, UUID.randomUUID()))))
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void aTEAMScopePassesTheCallersTeamAsIds() {
        var params = com.shivang.crm.modules.feedback.dto.FeedbackListParams.builder().build();
        when(recordScopeGuard.requireScope(eq(tenantId), any(UUID.class), eq("feedback"), eq("read")))
                .thenReturn("TEAM");
        when(recordScopeGuard.teamMemberIds(eq(tenantId), any(UUID.class))).thenReturn(List.of());
        org.springframework.data.domain.Page<Feedback> empty =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(feedbackRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Feedback>>any(),
                org.mockito.ArgumentMatchers.<org.springframework.data.domain.Pageable>any()))
                .thenReturn(empty);

        service.list(tenantId, params);

        verify(recordScopeGuard).teamMemberIds(eq(tenantId), any(UUID.class));
    }
}
