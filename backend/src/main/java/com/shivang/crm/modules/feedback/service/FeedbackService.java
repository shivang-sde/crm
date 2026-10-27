package com.shivang.crm.modules.feedback.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackListParams;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.mapper.FeedbackMapper;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackSpecifications;
import com.shivang.crm.modules.feedback.service.FeedbackIdentityResolver.ResolvedCustomer;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P1: Feedback submission and read.
 *
 * <h2>Identity</h2>
 * <p>Callers submit an email, never CRM ids. {@link FeedbackIdentityResolver} turns
 * it into an Account (and optionally a Contact) inside the authenticated tenant, so a
 * submission can never attach feedback to an arbitrary customer and cannot create
 * master data as a side effect.
 *
 * <h2>Immutability</h2>
 * <p>There is no update path. A submitted customer score is a statement of fact;
 * silently editing one would corrupt every average derived from it, and the score
 * is the input to retention decisions. Correction, if it is ever needed, should be
 * modelled explicitly as a new submission or a review action rather than bolted onto
 * this row. There is likewise no delete endpoint, so the soft-delete columns are
 * only ever set by the shared base class.
 *
 * <h2>Ordering of concerns on submit</h2>
 * <ol>
 *   <li>Resolve identity — an unresolvable or ambiguous email is rejected before
 *       anything else happens.</li>
 *   <li>Validate the score against its type, rejecting rather than clamping.</li>
 *   <li>Return an existing row for a repeated external reference (idempotency).</li>
 *   <li>Validate the optional ticket correlation against the resolved account.</li>
 *   <li>Persist, then publish {@code FEEDBACK.RECEIVED} through the transactional
 *       outbox, in the same transaction as the insert.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    /** Matches the repository's existing list-endpoint bounds. */
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackIdentityResolver identityResolver;
    private final AccountRepository accountRepository;
    private final FeedbackMapper feedbackMapper;
    private final CustomerRelationshipValidator relationshipValidator;
    private final RecordScopeGuard recordScopeGuard;
    private final CanonicalCrmEventPublisher canonicalEventPublisher;
    private final ActivityService activityService;

    /**
     * Submits one piece of customer feedback.
     *
     * @return the created response
     * @throws BusinessException {@code FEEDBACK_CUSTOMER_NOT_FOUND},
     *         {@code FEEDBACK_CUSTOMER_AMBIGUOUS}, {@code FEEDBACK_SCORE_REQUIRED}
     *         or {@code FEEDBACK_SCORE_OUT_OF_RANGE}
     */
    @Transactional
    public FeedbackResponse submit(UUID tenantId, UUID userId, FeedbackCreateRequest request) {
        FeedbackSource source = resolveSource(request.getSource());
        String externalReference = normalize(request.getExternalReference());

        requireValidScore(request.getType(), request.getScore());

        ResolvedCustomer customer = identityResolver.resolve(tenantId, request.getEmail());

        // Staff capture is anchored on the Account open in the UI. The email is still
        // resolved server-side, and this optional guard prevents a typo or a crafted
        // submission from attaching the feedback to a different customer. It is not an
        // identity input: the resolved email remains the authority.
        if (request.getExpectedAccountId() != null
                && !request.getExpectedAccountId().equals(customer.accountId())) {
            throw new BusinessException("FEEDBACK_ACCOUNT_MISMATCH",
                    "The submitted email does not belong to the account this feedback is being recorded against");
        }

        return persist(
                tenantId,
                userId,
                customer.accountId(),
                customer.contactId(),
                request.getTicketId(),
                request.getType(),
                request.getScore(),
                request.getComment(),
                source,
                externalReference);
    }

    /**
     * L3.5: submits feedback for a trusted caller that already resolved the
     * customer ids. Only in-process paths (the token public form) use this; the
     * authenticated email-based entry point remains {@link #submit}.
     *
     * <p>This exists so the public form cannot supply a customer identity. The
     * account/contact/ticket come from the persisted request row, not the request
     * body. Validation, deduplication, canonical event and timeline activity are
     * shared with {@link #submit} through {@link #persist}.
     */
    @Transactional
    public FeedbackResponse submitResolved(
            UUID tenantId, UUID userId, TrustedFeedbackSubmission submission) {
        if (submission.source() == null) {
            throw new BusinessException("FEEDBACK_SOURCE_REQUIRED",
                    "Feedback source is required for a resolved submission");
        }
        relationshipValidator.requireLiveAccount(submission.accountId(), tenantId);
        return persist(
                tenantId,
                userId,
                submission.accountId(),
                submission.contactId(),
                submission.ticketId(),
                submission.type(),
                submission.score(),
                submission.comment(),
                submission.source(),
                normalize(submission.externalReference()));
    }

    /** The one persistence path shared by every feedback entry point. */
    private FeedbackResponse persist(
            UUID tenantId,
            UUID userId,
            UUID accountId,
            UUID contactId,
            UUID ticketId,
            FeedbackType type,
            Integer score,
            String comment,
            FeedbackSource source,
            String externalReference) {

        requireValidScore(type, score);

        // Idempotency before the ticket check: a resubmission of an already-accepted
        // response must return the accepted response, not fail a correlation that was
        // only relevant on first receipt.
        if (source != null && externalReference != null) {
            var existing = feedbackRepository
                    .findByTenantIdAndSourceAndExternalReferenceAndDeletedFalse(
                            tenantId, source, externalReference);
            if (existing.isPresent()) {
                if (!existing.get().getAccountId().equals(accountId)) {
                    throw new BusinessException("FEEDBACK_EXTERNAL_REFERENCE_CONFLICT",
                            "This external reference is already used for a different customer");
                }
                log.info("Feedback submission {} from source {} already accepted; returning it unchanged",
                        externalReference, source);
                return feedbackMapper.toResponse(existing.get());
            }
        }

        // Ticket correlation is optional, and must belong to the resolved account.
        relationshipValidator.requireTicketBelongsToAccount(ticketId, accountId, tenantId);
        // The resolver only returns contacts it read inside this tenant, so this is a
        // re-assertion of an invariant rather than new validation; it also documents
        // that contact and account must agree.
        relationshipValidator.requireContactBelongsToAccount(contactId, accountId, tenantId);

        UUID ownerUserId = resolveOwner(accountId, tenantId);

        // Constructor + setters rather than the SuperBuilder, matching
        // TicketService.createTicket: TenantOwnedEntity declares the field as ownerId
        // and the subclass only remaps its column, so a builder call would read
        // ownerId(...) while the response DTO reads ownerUserId.
        Feedback feedback = new Feedback();
        feedback.setTenantId(tenantId);
        feedback.setOwnerId(ownerUserId);
        feedback.setCreatedBy(userId);
        feedback.setUpdatedBy(userId);
        feedback.setAccountId(accountId);
        feedback.setContactId(contactId);
        feedback.setTicketId(ticketId);
        feedback.setType(type);
        feedback.setScore(score);
        feedback.setComment(comment);
        feedback.setSource(source);
        feedback.setExternalReference(externalReference);
        feedback.setDeleted(false);

        feedback = feedbackRepository.save(feedback);

        canonicalEventPublisher.publish(
                tenantId,
                CanonicalCrmEvent.FEEDBACK_ENTITY_TYPE,
                CanonicalCrmEvent.RECEIVED_EVENT_TYPE,
                feedback.getId(),
                eventMetadata(feedback));

        // L3.3-P3: the timeline enabler. The unified Customer 360 timeline reads the
        // activities table, so without this row a submitted feedback would never
        // appear in it. This mirrors TicketService.createTicket writing
        // TICKET_CREATED: the activity is the audit fact "this feedback was
        // received", owned by the feedback's own visibility, not a second event
        // system.
        //
        // Deliberately the same vocabulary as the canonical event (RECEIVED, not
        // CREATED), so the activity row and the outbox row describe one fact. The
        // actor is the submitting user; the public form passes the staff requester
        // as that actor because BaseEntity.createdBy is a non-null FK to users.
        logFeedbackActivity(feedback, userId);

        log.info("Recorded {} feedback for account {} (contact {}, ticket {})",
                type, accountId, contactId, feedback.getTicketId());

        return feedbackMapper.toResponse(feedback);
    }

    /** L3.5: resolved customer identity for an in-process trusted submission. */
    public record TrustedFeedbackSubmission(
            UUID accountId,
            UUID contactId,
            UUID ticketId,
            FeedbackType type,
            Integer score,
            String comment,
            FeedbackSource source,
            String externalReference) {
    }

    /**
     * Newest-first page of feedback visible to the caller.
     *
     * <p>Three layers, as the repository intends: {@code RbacFilter} derives
     * {@code feedback:read} from the URL and fails closed; the controller restates
     * it in {@code @PreAuthorize}; this method applies {@code RecordScopeGuard} for
     * the record boundary, which is the authoritative one. Having
     * {@code feedback:read} does not reveal another owner's feedback.
     */
    @Transactional(readOnly = true)
    public Page<FeedbackResponse> list(UUID tenantId, FeedbackListParams params) {
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId(), "feedback", "read");
        List<UUID> teamUserIds = "TEAM".equals(scope)
                ? recordScopeGuard.teamMemberIds(tenantId, currentUserId())
                : List.of();

        var spec = FeedbackSpecifications.byTenantId(tenantId)
                .and(FeedbackSpecifications.notDeleted())
                .and(FeedbackSpecifications.visibleToUser(scope, currentUserId(), teamUserIds));

        if (params.getAccountId() != null) {
            spec = spec.and(FeedbackSpecifications.byAccountId(params.getAccountId()));
        }
        if (params.getTicketId() != null) {
            spec = spec.and(FeedbackSpecifications.byTicketId(params.getTicketId()));
        }
        if (params.getType() != null) {
            spec = spec.and(FeedbackSpecifications.byType(params.getType()));
        }

        PageRequest pageable = PageRequest.of(
                Math.max(0, params.getPage()),
                boundedSize(params.getSize()),
                FeedbackSpecifications.newestFirst());

        return feedbackRepository.findAll(spec, pageable).map(feedbackMapper::toResponse);
    }

    /** One feedback record, or NOT_FOUND. Tenant isolation and scope both apply. */
    @Transactional(readOnly = true)
    public FeedbackResponse getById(UUID tenantId, UUID id) {
        UUID userId = currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, userId, "feedback", "read");
        List<UUID> teamUserIds = "TEAM".equals(scope)
                ? recordScopeGuard.teamMemberIds(tenantId, userId)
                : List.of();

        var spec = FeedbackSpecifications.byTenantId(tenantId)
                .and(FeedbackSpecifications.notDeleted())
                .and(FeedbackSpecifications.visibleToUser(scope, userId, teamUserIds));

        return feedbackRepository.findOne(spec)
                .map(feedbackMapper::toResponse)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Feedback not found"));
    }

    /**
     * Staff capture must not be able to claim customer provenance.
     *
     * <p>{@code source} is caller-supplied metadata, so without this a staff member
     * could record an internal note as {@code WEBSITE} and make the Voice-of-Customer
     * reporting lie. The existing API-key security filter marks trusted integrations
     * with {@code ROLE_INTEGRATION}; those callers keep their declared source. Every
     * other caller is normalized to {@code INTERNAL}, and a non-internal source is
     * rejected rather than silently rewritten.
     *
     * <p>Known limitation: no feedback ingestion route currently passes through the
     * commercial API-key filter. A dedicated trusted feedback credential remains a
     * later phase; until then external callers use the normal tenant credential and
     * are recorded as {@code INTERNAL} if they omit the source.
     */
    private FeedbackSource resolveSource(FeedbackSource requested) {
        if (hasIntegrationAuthority()) {
            return requested;
        }
        if (requested == null || requested == FeedbackSource.INTERNAL) {
            return FeedbackSource.INTERNAL;
        }
        throw new BusinessException("FEEDBACK_SOURCE_NOT_PERMITTED",
                "Only a trusted integration may submit a non-internal feedback source");
    }

    private static boolean hasIntegrationAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_INTEGRATION".equals(a.getAuthority()));
    }

    /**
     * Rejects an out-of-range or missing score. Never clamps: silently turning an 11
     * into a 10 would hide a broken integration and quietly move an average.
     */
    private void requireValidScore(FeedbackType type, Integer score) {
        if (type.isScoreRequired() && score == null) {
            throw new BusinessException("FEEDBACK_SCORE_REQUIRED",
                    "A score is required for " + type + " (scale " + type.describeScale() + ")");
        }
        if (!type.acceptsScore(score)) {
            throw new BusinessException("FEEDBACK_SCORE_OUT_OF_RANGE",
                    "Score " + score + " is not valid for " + type
                            + " (scale " + type.describeScale() + ")");
        }
    }

    /**
     * Ownership inherits from the Account.
     *
     * <p>External feedback frequently has no natural submitting CRM user, and the
     * shared OWN/TEAM/ALL specifications filter on {@code ownerId}. Inheriting the
     * Account's owner makes Feedback respond to the same scope rules as every other
     * customer-domain record. Falls back to the submitting user when the Account has
     * no owner, so the row is never left scope-less.
     */
    private UUID resolveOwner(UUID accountId, UUID tenantId) {
        return accountRepository.findByIdAndTenantIdAndDeletedFalse(accountId, tenantId)
                .map(Account::getOwnerId)
                .orElse(null);
    }

    /**
     * Canonical event metadata.
     *
     * <p>{@code CanonicalCrmEvent.immutableMetadata} drops null keys and values, so
     * the optional contact/ticket slots are omitted rather than sent as nulls — no
     * extra null handling needed here.
     */
    private Map<String, Object> eventMetadata(Feedback feedback) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", feedback.getType().name());
        metadata.put("score", feedback.getScore());
        metadata.put("accountId", feedback.getAccountId());
        metadata.put("contactId", feedback.getContactId());
        metadata.put("ticketId", feedback.getTicketId());
        metadata.put("source", feedback.getSource() == null ? null : feedback.getSource().name());
        return metadata;
    }

    /**
     * Writes the audit row the Customer 360 timeline reads.
     *
     * <p>Same shape as every other domain's submission activity (see
     * {@code TicketService.logTicketActivity}): the account/contact/ticket the
     * feedback belongs to travel in metadata so the timeline entry is intelligible
     * without a second lookup. Optional values are omitted rather than stored as
     * nulls.
     */
    private void logFeedbackActivity(Feedback feedback, UUID userId) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "accountId", feedback.getAccountId());
        putIfPresent(metadata, "contactId", feedback.getContactId());
        putIfPresent(metadata, "ticketId", feedback.getTicketId());
        metadata.put("type", feedback.getType().name());
        putIfPresent(metadata, "score", feedback.getScore());
        putIfPresent(metadata, "source",
                feedback.getSource() == null ? null : feedback.getSource().name());

        String description = "Feedback received: " + feedback.getType().name()
                + (feedback.getScore() != null ? " " + feedback.getScore() : "");

        activityService.logActivity(
                feedback.getTenantId(),
                feedback.getId(),
                "FEEDBACK",
                "FEEDBACK_RECEIVED",
                description,
                userId,
                metadata);
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // Blank collapses to absent so the dedup index's btrim guard and this agree.
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int boundedSize(int size) {
        return Math.min(MAX_PAGE_SIZE, size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    private static UUID currentUserId() {
        return com.shivang.crm.util.UserUtil.currentUserId();
    }
}
