package com.shivang.crm.modules.feedback.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.analytics.AnalyticsDateRange;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealSpecifications;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementSpecifications;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalSeverity;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalType;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackSpecifications;
import com.shivang.crm.modules.feedback.repository.RiskSignalQueryRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.util.UserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.4-P1: deterministic customer risk signals.
 *
 * <p>Not a health score. Each rule fires on a fact already recorded
 * elsewhere in the CRM, and every returned signal names the row it came from, so
 * a user can open that row and check the claim. Nothing here infers intent:
 * an entitlement expiring is reported as an entitlement expiring, never as churn
 * probability, and a lost deal is never read as a lost customer.
 *
 * <p>Visibility has two layers. A signal attached to an account requires the
 * caller to hold {@code account:read} over that account in addition to the
 * source domain's read scope. The account-specific route enforces the parent
 * account rule before calling this service; the cross-account query applies the
 * account scope in SQL. Each source domain is still filtered by its own
 * {@code visibleToUser} specification, with the scope resolved from that
 * domain's own permission. A caller without {@code ticket:read} gets no ticket
 * signals — not an error, and not other users' tickets either.
 *
 * <p>A deal or entitlement with no linked account has no account whose scope
 * could authorize it; it remains governed by its source-domain read scope and
 * is returned with a null {@code accountId}.
 *
 * <p>Feedback-derived rules read Feedback rows directly rather than the
 * {@code GROUP BY} buckets the VoC analytics endpoint uses, because a bucket has
 * no single source record to cite and the API promises one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskSignalService {

    /** Score thresholds. These mirror the scales Feedback already validates against. */
    public static final int NEGATIVE_NPS_BELOW = 7;
    public static final int LOW_CSAT_AT_OR_BELOW = 2;
    public static final int HIGH_CES_AT_OR_ABOVE = 4;

    /** How close to expiry still counts as "approaching". */
    public static final int EXPIRY_HORIZON_DAYS = 30;

    private final com.shivang.crm.modules.feedback.repository.FeedbackRepository feedbackRepository;
    private final com.shivang.crm.modules.ticket.repository.TicketRepository ticketRepository;
    private final com.shivang.crm.modules.deal.repository.DealRepository dealRepository;
    private final com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository entitlementRepository;
    private final RiskSignalQueryRepository riskSignalQueryRepository;

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final RecordScopeGuard recordScopeGuard;
    private final Clock clock;

    /**
     * Risk signals for one account, or for every visible account when
     * {@code accountId} is null (the cross-account listing).
     *
     * <p>{@code from}/{@code to} are ISO-8601 and half-open {@code [from, to)},
     * reusing {@link AnalyticsDateRange}'s parsing and defaults so this endpoint
     * and {@code GET /feedback/analytics} cannot disagree about what a window
     * means.
     */
    @Transactional(readOnly = true)
    public List<RiskSignalResponse> riskSignals(
            UUID tenantId, UUID accountId, String rawFrom, String rawTo) {

        UUID userId = UserUtil.currentUserId();
        Instant now = clock.instant();
        // The window derives from the injected clock, so "the last 30 days" is one
        // value shared by every rule here and by the tests, instead of each call
        // re-reading the system clock.
        AnalyticsDateRange range = AnalyticsDateRange.resolve(rawFrom, rawTo, now);
        Instant from = range.from();
        Instant to = range.to();

        Scope feedback = scope(userId, tenantId, "feedback", "read");
        Scope ticket = scope(userId, tenantId, "ticket", "read");
        Scope deal = scope(userId, tenantId, "deal", "read");
        Scope entitlement = scope(userId, tenantId, "entitlement", "read");

        List<RiskSignalResponse> signals = new ArrayList<>();

        if (feedback.visible()) {
            signals.addAll(negativeNps(feedback, tenantId, accountId, from, to));
            signals.addAll(lowCsat(feedback, tenantId, accountId, from, to));
            signals.addAll(highCes(feedback, tenantId, accountId, from, to));
            signals.addAll(recentComplaints(feedback, tenantId, accountId, from, to));
        }
        if (feedback.visible()) {
            // P2: the silence case needs the feedback stream, so it is gated by
            // feedback:read exactly like the four rules above.
            signals.addAll(feedbackSilence(feedback, tenantId, accountId, from));
        }
        if (ticket.visible()) {
            signals.addAll(slaBreachedTickets(ticket, tenantId, accountId));
        }
        if (ticket.visible()) {
            signals.addAll(supportHealth(ticket, tenantId, accountId, now));
        }
        if (entitlement.visible()) {
            signals.addAll(entitlementLifecycle(entitlement, tenantId, accountId, now));
        }
        if (deal.visible()) {
            signals.addAll(recentLostDeals(deal, tenantId, accountId, from, to));
        }

        log.debug("Risk signals for tenant {} account {}: {} signal(s) in [{}, {})",
                tenantId, accountId, signals.size(), from, to);
        return signals;
    }

    /**
     * Filtered, globally ordered and paged signals for the cross-account listing.
     *
     * <p>The database evaluates the rules, type/severity filters, caller scopes,
     * ordering and count over the whole set, then returns only this page's source
     * coordinates. The old in-memory path returned at most 500 rows per source and
     * could not report how many signals it had dropped.
     */
    @Transactional(readOnly = true)
    public RiskSignalPage riskSignalsPage(
            UUID tenantId,
            UUID accountId,
            String rawFrom,
            String rawTo,
            RiskSignalType type,
            RiskSignalSeverity severity,
            int page,
            int size) {

        UUID userId = UserUtil.currentUserId();
        Instant now = clock.instant();
        AnalyticsDateRange range = AnalyticsDateRange.resolve(rawFrom, rawTo, now);

        Scope accountScope = scope(userId, tenantId, "account", "read");
        Map<String, RiskSignalQueryRepository.Scope> queryScopes = new HashMap<>();
        for (String domain : List.of("feedback", "ticket", "deal", "entitlement")) {
            Scope resolved = scope(userId, tenantId, domain, "read");
            queryScopes.put(domain, new RiskSignalQueryRepository.Scope(
                    resolved.name(), resolved.userId(), resolved.teamUserIds()));
        }

        List<RiskSignalType> types = Arrays.stream(RiskSignalType.values())
                .filter(candidate -> type == null || candidate == type)
                .filter(candidate -> severity == null || severityOf(candidate) == severity)
                .toList();

        RiskSignalQueryRepository.Query query = new RiskSignalQueryRepository.Query(
                tenantId,
                accountId,
                userId,
                range.from(),
                range.to(),
                now,
                now.atZone(ZoneOffset.UTC).toLocalDate(),
                types,
                new RiskSignalQueryRepository.Scope(
                        accountScope.name(), accountScope.userId(), accountScope.teamUserIds()),
                queryScopes);

        long offset = (long) page * size;
        RiskSignalQueryRepository.Result result = riskSignalQueryRepository.search(query, offset, size);

        return new RiskSignalPage(
                toSignals(result.rows(), range.from(), now), page, size, result.total());
    }

    private List<RiskSignalResponse> toSignals(
            List<RiskSignalQueryRepository.SignalRow> rows, Instant from, Instant now) {

        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> feedbackIds = sourceIds(rows, SourceTypes.FEEDBACK);
        Set<UUID> ticketIds = sourceIds(rows, SourceTypes.TICKET);
        Set<UUID> entitlementIds = sourceIds(rows, SourceTypes.ENTITLEMENT);
        Set<UUID> dealIds = sourceIds(rows, SourceTypes.DEAL);

        Map<UUID, com.shivang.crm.modules.feedback.entity.Feedback> feedbackById = new HashMap<>();
        if (!feedbackIds.isEmpty()) {
            feedbackRepository.findAllById(feedbackIds)
                    .forEach(feedback -> feedbackById.put(feedback.getId(), feedback));
        }
        Map<UUID, com.shivang.crm.modules.ticket.entity.Ticket> ticketById = new HashMap<>();
        if (!ticketIds.isEmpty()) {
            ticketRepository.findAllById(ticketIds)
                    .forEach(ticket -> ticketById.put(ticket.getId(), ticket));
        }
        Map<UUID, com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> entitlementById =
                new HashMap<>();
        if (!entitlementIds.isEmpty()) {
            entitlementRepository.findAllById(entitlementIds)
                    .forEach(entitlement -> entitlementById.put(entitlement.getId(), entitlement));
        }
        Map<UUID, com.shivang.crm.modules.deal.entity.Deal> dealById = new HashMap<>();
        if (!dealIds.isEmpty()) {
            dealRepository.findAllById(dealIds)
                    .forEach(deal -> dealById.put(deal.getId(), deal));
        }

        List<RiskSignalResponse> signals = new ArrayList<>(rows.size());
        for (RiskSignalQueryRepository.SignalRow row : rows) {
            RiskSignalResponse signal = switch (row.type()) {
                case NEGATIVE_NPS, LOW_CSAT, HIGH_CES, RECENT_COMPLAINT -> {
                    var feedback = feedbackById.get(row.sourceId());
                    yield feedback == null ? null : feedbackSignal(feedback, row.type());
                }
                case ENGAGEMENT_FEEDBACK_SILENT -> {
                    var feedback = feedbackById.get(row.sourceId());
                    yield feedback == null ? null : silenceSignal(feedback, from);
                }
                case SLA_BREACHED_TICKET -> {
                    var ticket = ticketById.get(row.sourceId());
                    yield ticket == null ? null : slaBreachedSignal(ticket);
                }
                case SUPPORT_NO_RESPONSE_PAST_DUE -> {
                    var ticket = ticketById.get(row.sourceId());
                    yield ticket == null ? null : noResponseSignal(ticket);
                }
                case SUPPORT_SLOW_FIRST_RESPONSE -> {
                    var ticket = ticketById.get(row.sourceId());
                    yield ticket == null ? null : slowResponseSignal(ticket);
                }
                case SUPPORT_PAST_RESOLUTION_DUE -> {
                    var ticket = ticketById.get(row.sourceId());
                    yield ticket == null ? null : pastResolutionSignal(ticket, now);
                }
                case ENTITLEMENT_APPROACHING_EXPIRY -> {
                    var entitlement = entitlementById.get(row.sourceId());
                    yield entitlement == null ? null : approachingExpiry(entitlement);
                }
                case ENTITLEMENT_EXPIRED -> {
                    var entitlement = entitlementById.get(row.sourceId());
                    yield entitlement == null ? null : expiredSignal(entitlement);
                }
                case RECENT_DEAL_LOSS -> {
                    var deal = dealById.get(row.sourceId());
                    yield deal == null ? null : dealLossSignal(deal);
                }
            };
            if (signal != null) {
                signals.add(signal);
            }
        }
        return signals;
    }

    private static Set<UUID> sourceIds(
            List<RiskSignalQueryRepository.SignalRow> rows, Set<RiskSignalType> types) {
        return rows.stream()
                .filter(row -> types.contains(row.type()))
                .map(RiskSignalQueryRepository.SignalRow::sourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static final class SourceTypes {
        private static final Set<RiskSignalType> FEEDBACK = Set.of(
                RiskSignalType.NEGATIVE_NPS,
                RiskSignalType.LOW_CSAT,
                RiskSignalType.HIGH_CES,
                RiskSignalType.RECENT_COMPLAINT,
                RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT);
        private static final Set<RiskSignalType> TICKET = Set.of(
                RiskSignalType.SLA_BREACHED_TICKET,
                RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE,
                RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE,
                RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
        private static final Set<RiskSignalType> ENTITLEMENT = Set.of(
                RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY,
                RiskSignalType.ENTITLEMENT_EXPIRED);
        private static final Set<RiskSignalType> DEAL = Set.of(RiskSignalType.RECENT_DEAL_LOSS);

        private SourceTypes() {
        }
    }

    private static RiskSignalSeverity severityOf(RiskSignalType type) {
        return switch (type) {
            case NEGATIVE_NPS, RECENT_COMPLAINT, SLA_BREACHED_TICKET,
                    SUPPORT_NO_RESPONSE_PAST_DUE, ENTITLEMENT_EXPIRED -> RiskSignalSeverity.HIGH;
            case LOW_CSAT, HIGH_CES, ENGAGEMENT_FEEDBACK_SILENT,
                    SUPPORT_SLOW_FIRST_RESPONSE, SUPPORT_PAST_RESOLUTION_DUE,
                    ENTITLEMENT_APPROACHING_EXPIRY, RECENT_DEAL_LOSS -> RiskSignalSeverity.MEDIUM;
        };
    }

    // ---------- RISK-001..004: Feedback-derived ----------

    private List<RiskSignalResponse> negativeNps(
            Scope scope, UUID tenantId, UUID accountId, Instant from, Instant to) {
        return feedbackSignals(scope, tenantId, accountId, from, to,
                FeedbackType.NPS, 0, NEGATIVE_NPS_BELOW - 1,
                RiskSignalType.NEGATIVE_NPS);
    }

    private List<RiskSignalResponse> lowCsat(
            Scope scope, UUID tenantId, UUID accountId, Instant from, Instant to) {
        return feedbackSignals(scope, tenantId, accountId, from, to,
                FeedbackType.CSAT, 1, LOW_CSAT_AT_OR_BELOW,
                RiskSignalType.LOW_CSAT);
    }

    private List<RiskSignalResponse> highCes(
            Scope scope, UUID tenantId, UUID accountId, Instant from, Instant to) {
        return feedbackSignals(scope, tenantId, accountId, from, to,
                FeedbackType.CES, HIGH_CES_AT_OR_ABOVE, 7,
                RiskSignalType.HIGH_CES);
    }

    private List<RiskSignalResponse> recentComplaints(
            Scope scope, UUID tenantId, UUID accountId, Instant from, Instant to) {
        return feedbackSignals(scope, tenantId, accountId, from, to,
                FeedbackType.COMPLAINT, null, null,
                RiskSignalType.RECENT_COMPLAINT);
    }

    /**
     * One Feedback query per rule, each returning the individual rows so every
     * signal can cite a source. Complaints carry no score, hence the null range.
     */
    private List<RiskSignalResponse> feedbackSignals(
            Scope scope,
            UUID tenantId,
            UUID accountId,
            Instant from,
            Instant to,
            FeedbackType type,
            Integer scoreFrom,
            Integer scoreTo,
            RiskSignalType signalType) {

        Specification<com.shivang.crm.modules.feedback.entity.Feedback> spec =
                Specification.allOf(
                        FeedbackSpecifications.byTenantId(tenantId),
                        FeedbackSpecifications.notDeleted(),
                        FeedbackSpecifications.customerOriginated(),
                        FeedbackSpecifications.byType(type),
                        FeedbackSpecifications.visibleToUser(scope.name(), scope.userId(), scope.teamUserIds()),
                        (root, query, cb) -> {
                            if (from == null && to == null) {
                                return cb.conjunction();
                            }
                            if (from != null && to != null) {
                                return cb.and(
                                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                                        cb.lessThan(root.get("createdAt"), to));
                            }
                            return from != null
                                    ? cb.greaterThanOrEqualTo(root.get("createdAt"), from)
                                    : cb.lessThan(root.get("createdAt"), to);
                        },
                        (root, query, cb) -> scoreRange(cb, root, scoreFrom, scoreTo),
                        accountId == null
                                ? (root, query, cb) -> cb.conjunction()
                                : FeedbackSpecifications.byAccountId(accountId));

        return feedbackRepository.findAll(spec,
                        Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(feedback -> feedbackSignal(feedback, signalType))
                .toList();
    }

    private RiskSignalResponse feedbackSignal(
            com.shivang.crm.modules.feedback.entity.Feedback feedback, RiskSignalType type) {
        int score = feedback.getScore() == null ? 0 : feedback.getScore();
        String reason = switch (type) {
            case NEGATIVE_NPS -> "Customer rated their experience " + score + " out of 10, below the "
                    + NEGATIVE_NPS_BELOW + " needed to count as anything but negative";
            case LOW_CSAT -> "Customer rated satisfaction " + score + " out of 5";
            case HIGH_CES -> "Customer reported an effort score of " + score + " out of 7, "
                    + "where higher means they had to work harder";
            case RECENT_COMPLAINT -> "Customer submitted a complaint";
            default -> throw new IllegalArgumentException("Not a feedback signal: " + type);
        };
        return RiskSignalResponse.builder()
                .accountId(feedback.getAccountId())
                .type(type)
                .severity(severityOf(type))
                .reason(reason)
                .detectedAt(feedback.getCreatedAt())
                .sourceType("FEEDBACK")
                .sourceId(feedback.getId())
                .metadata(metadata(feedback))
                .build();
    }

    private jakarta.persistence.criteria.Predicate scoreRange(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<com.shivang.crm.modules.feedback.entity.Feedback> root,
            Integer from,
            Integer to) {
        if (from == null || to == null) {
            return cb.conjunction();
        }
        return cb.between(root.get("score"), from, to);
    }

    private Map<String, Object> metadata(com.shivang.crm.modules.feedback.entity.Feedback feedback) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("feedbackType", feedback.getType().name());
        if (feedback.getScore() != null) {
            metadata.put("score", feedback.getScore());
        }
        if (feedback.getComment() != null && !feedback.getComment().isBlank()) {
            metadata.put("comment", feedback.getComment());
        }
        if (feedback.getTicketId() != null) {
            metadata.put("ticketId", feedback.getTicketId());
        }
        return metadata;
    }

    // ---------- L3.4-P2: Engagement ----------

    /**
     * The one engagement statement the CRM can actually support.
     *
     * <p>Reads each account's most recent customer-originated Feedback row ever.
     * The signal fires only when that row exists and predates the window — that
     * is, an account that has demonstrably given feedback before, and has given
     * none since. Requiring the prior row is what separates "went quiet" from
     * "was never surveyed": an account with no feedback at all yields nothing,
     * because silence there is a fact about the survey programme, not about the
     * customer.
     *
     * <p><b>What this does not claim.</b> It does not claim the customer is
     * disengaged, and it is not a stand-in for an engagement score. There is no
     * inbound email, portal, web-analytics or product-usage trail in this
     * repository, and {@code CALL} activities record that a rep pressed dial
     * (see {@code DefaultClickToCallService}, which logs subType
     * {@code CALL_INITIATED}), not that anyone spoke. Feedback is the only
     * customer-initiated artefact on record.
     */
    private List<RiskSignalResponse> feedbackSilence(
            Scope scope, UUID tenantId, UUID accountId, Instant from) {

        if (from == null) {
            // No window means no claim about silence.
            return List.of();
        }

        Specification<com.shivang.crm.modules.feedback.entity.Feedback> spec =
                Specification.allOf(
                        FeedbackSpecifications.byTenantId(tenantId),
                        FeedbackSpecifications.notDeleted(),
                        FeedbackSpecifications.customerOriginated(),
                        FeedbackSpecifications.visibleToUser(
                                scope.name(), scope.userId(), scope.teamUserIds()),
                        FeedbackSpecifications.latestCustomerFeedbackPerAccount(
                                tenantId, scope.name(), scope.userId(), scope.teamUserIds()),
                        (root, query, cb) -> cb.lessThan(root.get("createdAt"), from),
                        accountId == null
                                ? (root, query, cb) -> cb.conjunction()
                                : FeedbackSpecifications.byAccountId(accountId));

        // One row per account, latest first. Equal timestamps can yield both rows;
        // de-duplicating here keeps one silence signal per account, not per tie.
        Map<UUID, com.shivang.crm.modules.feedback.entity.Feedback> latestByAccount =
                new java.util.LinkedHashMap<>();
        for (com.shivang.crm.modules.feedback.entity.Feedback candidate : feedbackRepository.findAll(
                spec, Pageable.unpaged(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.DESC, "id")))).getContent()) {
            latestByAccount.putIfAbsent(candidate.getAccountId(), candidate);
        }

        if (latestByAccount.isEmpty()) {
            // Never gave feedback (or only internal/null-source rows): silence
            // proves nothing about this customer.
            return List.of();
        }

        List<RiskSignalResponse> signals = new ArrayList<>(latestByAccount.size());
        for (com.shivang.crm.modules.feedback.entity.Feedback latest : latestByAccount.values()) {
            if (latest.getCreatedAt() == null || !latest.getCreatedAt().isBefore(from)) {
                continue;
            }

            signals.add(silenceSignal(latest, from));
        }
        return signals;
    }

    private RiskSignalResponse silenceSignal(
            com.shivang.crm.modules.feedback.entity.Feedback latest, Instant from) {
        long days = java.time.Duration.between(latest.getCreatedAt(), from).toDays();
        return RiskSignalResponse.builder()
                .accountId(latest.getAccountId())
                .type(RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason("Customer last submitted feedback " + days + " day(s) ago, before the "
                        + "reporting window opened. This records silence, not disengagement.")
                .detectedAt(latest.getCreatedAt())
                .sourceType("FEEDBACK")
                .sourceId(latest.getId())
                .metadata(Map.of(
                        "lastFeedbackAt", latest.getCreatedAt(),
                        "lastFeedbackType", latest.getType().name(),
                        "windowStart", from,
                        "daysSinceLastFeedback", days))
                .build();
    }

    // ---------- L3.4-P2: Support ----------

    /**
     * Support health from fields the Ticket already persists.
     *
     * <p>Every rule below is anchored on a deadline that was snapshotted onto the
     * ticket when it was raised ({@code responseDueAt}, {@code resolutionDueAt}),
     * so none of them needs a threshold invented here. "Late" always means "later
     * than the deadline this ticket was already given".
     *
     * <p>Deliberately absent: open-ticket counts, repeat-ticket counts and any
     * "3 tickets in 14 days" escalation. Those need a volume policy this
     * repository does not define, and a plausible-looking threshold would be
     * invented business logic wearing the costume of a rule.
     */
    private List<RiskSignalResponse> supportHealth(
            Scope scope, UUID tenantId, UUID accountId, Instant now) {

        Specification<com.shivang.crm.modules.ticket.entity.Ticket> spec =
                Specification.allOf(
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.byTenantId(tenantId),
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.notDeleted(),
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.visibleToUser(
                                scope.name(), scope.userId(), scope.teamUserIds()),
                        (root, query, cb) -> root.get("status").in(
                                com.shivang.crm.modules.ticket.entity.TicketStatus.OPEN,
                                com.shivang.crm.modules.ticket.entity.TicketStatus.IN_PROGRESS),
                        accountId == null
                                ? (root, query, cb) -> cb.conjunction()
                                : com.shivang.crm.modules.ticket.repository.TicketSpecifications
                                        .byAccountId(accountId));

        List<RiskSignalResponse> signals = new ArrayList<>();

        for (com.shivang.crm.modules.ticket.entity.Ticket ticket : ticketRepository.findAll(
                        spec, Pageable.unpaged(Sort.by(Sort.Direction.ASC, "createdAt")))) {

            // Re-checked here rather than trusted to the Specification alone. A
            // RESOLVED or CLOSED ticket is finished work: missing a deadline after
            // that is not a service failure, and Ticket.isSlaEligible() draws
            // exactly this line.
            com.shivang.crm.modules.ticket.entity.TicketStatus status = ticket.getStatus();
            if (status != com.shivang.crm.modules.ticket.entity.TicketStatus.OPEN
                    && status != com.shivang.crm.modules.ticket.entity.TicketStatus.IN_PROGRESS) {
                continue;
            }

            if (ticket.getFirstResponseAt() == null
                    && ticket.getResponseDueAt() != null
                    && !now.isBefore(ticket.getResponseDueAt())) {

                signals.add(noResponseSignal(ticket));

            } else if (ticket.getFirstResponseAt() != null
                    && ticket.getResponseDueAt() != null
                    && ticket.getFirstResponseAt().isAfter(ticket.getResponseDueAt())) {

                signals.add(slowResponseSignal(ticket));
            }

            if (ticket.getResolutionDueAt() != null
                    && !now.isBefore(ticket.getResolutionDueAt())) {

                signals.add(pastResolutionSignal(ticket, now));
            }
        }

        return signals;
    }

    private RiskSignalResponse noResponseSignal(
            com.shivang.crm.modules.ticket.entity.Ticket ticket) {
        return RiskSignalResponse.builder()
                .accountId(ticket.getAccountId())
                .type(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE)
                .severity(RiskSignalSeverity.HIGH)
                .reason("Ticket \"" + ticket.getSubject() + "\" has had no agent reply and is past its "
                        + "response deadline")
                .detectedAt(ticket.getCreatedAt())
                .sourceType("TICKET")
                .sourceId(ticket.getId())
                .metadata(metadata(ticket))
                .build();
    }

    private RiskSignalResponse slowResponseSignal(
            com.shivang.crm.modules.ticket.entity.Ticket ticket) {
        long lateMinutes = java.time.Duration
                .between(ticket.getResponseDueAt(), ticket.getFirstResponseAt()).toMinutes();
        return RiskSignalResponse.builder()
                .accountId(ticket.getAccountId())
                .type(RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason("Ticket \"" + ticket.getSubject() + "\" was first answered " + lateMinutes
                        + " minute(s) after its response deadline")
                .detectedAt(ticket.getFirstResponseAt())
                .sourceType("TICKET")
                .sourceId(ticket.getId())
                .metadata(metadata(ticket))
                .build();
    }

    private RiskSignalResponse pastResolutionSignal(
            com.shivang.crm.modules.ticket.entity.Ticket ticket, Instant now) {
        long overdueDays = java.time.Duration
                .between(ticket.getResolutionDueAt(), now).toDays();
        return RiskSignalResponse.builder()
                .accountId(ticket.getAccountId())
                .type(RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason("Ticket \"" + ticket.getSubject() + "\" is still open, " + overdueDays
                        + " day(s) past its resolution deadline")
                .detectedAt(ticket.getCreatedAt())
                .sourceType("TICKET")
                .sourceId(ticket.getId())
                .metadata(metadata(ticket))
                .build();
    }

    private RiskSignalResponse slaBreachedSignal(
            com.shivang.crm.modules.ticket.entity.Ticket ticket) {
        return RiskSignalResponse.builder()
                .accountId(ticket.getAccountId())
                .type(RiskSignalType.SLA_BREACHED_TICKET)
                .severity(RiskSignalSeverity.HIGH)
                .reason("Support ticket \"" + ticket.getSubject() + "\" missed its SLA deadline")
                .detectedAt(ticket.getCreatedAt())
                .sourceType("TICKET")
                .sourceId(ticket.getId())
                .metadata(metadata(ticket))
                .build();
    }

    // ---------- L3.4-P1: Ticket (SLA breach) ----------

    /**
     * Reads the persisted {@code sla_breached} flag rather than re-deriving it
     * from the due dates. The flag is set once by the SLA scheduler and is
     * deliberately historical, so recomputing here would disagree with the ticket
     * the user can already open.
     */
    private List<RiskSignalResponse> slaBreachedTickets(
            Scope scope, UUID tenantId, UUID accountId) {
        Specification<com.shivang.crm.modules.ticket.entity.Ticket> spec =
                Specification.allOf(
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.byTenantId(tenantId),
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.notDeleted(),
                        com.shivang.crm.modules.ticket.repository.TicketSpecifications.visibleToUser(
                                scope.name(), scope.userId(), scope.teamUserIds()),
                        (root, query, cb) -> cb.isTrue(root.get("slaBreached")),
                        accountId == null
                                ? (root, query, cb) -> cb.conjunction()
                                : com.shivang.crm.modules.ticket.repository.TicketSpecifications
                                        .byAccountId(accountId));

        return ticketRepository.findAll(spec,
                        Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::slaBreachedSignal)
                .toList();
    }

    /**
     * {@link Map#of} rejects nulls, and every SLA timestamp here is legitimately
     * null for a ticket raised before the SLA policy existed or before a response
     * arrived — a breached ticket with no recorded deadline is exactly the case
     * this endpoint must survive.
     */
    private Map<String, Object> metadata(com.shivang.crm.modules.ticket.entity.Ticket ticket) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("subject", ticket.getSubject());
        metadata.put("status", ticket.getStatus() == null ? null : ticket.getStatus().name());
        metadata.put("responseDueAt", ticket.getResponseDueAt());
        metadata.put("resolutionDueAt", ticket.getResolutionDueAt());
        metadata.put("firstResponseAt", ticket.getFirstResponseAt());
        metadata.put("resolvedAt", ticket.getResolvedAt());
        metadata.put("closedAt", ticket.getClosedAt());
        return metadata;
    }

    // ---------- RISK-006: Entitlement ----------

    /**
     * L3.4-P1 + L3.4-P3: entitlement lifecycle signals.
     *
     * <p>Two facts are reported, and neither claims anything about intent:
     * <ul>
     *   <li>coverage is about to end or its renewal decision date is near
     *       ({@code ENTITLEMENT_APPROACHING_EXPIRY});</li>
     *   <li>coverage ended and nothing replaced it
     *       ({@code ENTITLEMENT_EXPIRED}).</li>
     * </ul>
     *
     * <p><b>Why the expired rule needs a replacement check.</b> A renewal in this
     * schema is a NEW entitlement provisioned from a NEW line item (the unique
     * {@code (tenant_id, deal_line_item_id)} constraint permits exactly one
     * entitlement per line item), and renewal linkage is often not written. Every
     * renewal can therefore leave the old row sitting in {@code EXPIRED}.
     * Reporting it unconditionally would label a successful renewal as an
     * unresolved risk. A live row suppresses the signal when it is explicitly
     * linked to the expired row, or when it covers the same account/offering and
     * starts on or before {@code expired.endDate + 1 day} (a null start means
     * unknown, not future). A future-dated PENDING row with a real coverage gap
     * does not suppress the signal.
     *
     * <p>Deliberately not reported: {@code CANCELLED} and {@code TERMINATED}
     * entitlements. Ending coverage on purpose is a business decision, not a
     * lapse, and {@code EXPIRED} is the only status the scheduler assigns from a
     * date alone. Nor is an ACTIVE row past its end date reported: the status is
     * the lifecycle contract, and inferring expiry from a date would disagree
     * with the entitlement a user can open.
     */
    private List<RiskSignalResponse> entitlementLifecycle(
            Scope scope, UUID tenantId, UUID accountId, Instant now) {

        LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate horizon = today.plusDays(EXPIRY_HORIZON_DAYS);

        List<RiskSignalResponse> signals = new ArrayList<>();

        // One query for everything still covering the customer: ACTIVE rows drive
        // the expiry/renewal warning, ACTIVE or PENDING rows suppress a false
        // "coverage lapsed" claim.
        List<com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> live = entitlementRepository.findAll(
                        liveCoverageSpecification(scope, tenantId, accountId, today),
                        Pageable.unpaged(Sort.by(Sort.Direction.ASC, "endDate")))
                .stream()
                .toList();

        List<com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> liveCoverage =
                new ArrayList<>();
        for (com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement : live) {
            // Re-checked here, not trusted to the query alone: this is what keeps
            // the rule correct on its own rather than only by way of the SQL.
            if (!isLiveCoverage(entitlement, today)) {
                continue;
            }
            liveCoverage.add(entitlement);

            if (entitlement.getStatus() != EntitlementStatus.ACTIVE) {
                continue;
            }
            boolean expiresSoon = !entitlement.getEndDate().isAfter(horizon);
            boolean renewalDueSoon = Boolean.TRUE.equals(entitlement.getRenewable())
                    && entitlement.getRenewalDueDate() != null
                    && !entitlement.getRenewalDueDate().isBefore(today)
                    && !entitlement.getRenewalDueDate().isAfter(horizon);
            if (!expiresSoon && !renewalDueSoon) {
                continue;
            }
            signals.add(approachingExpiry(entitlement));
        }

        for (com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement : expiredCoverage(
                scope, tenantId, accountId, today)) {

            if (entitlement.getStatus() != EntitlementStatus.EXPIRED
                    || entitlement.getEndDate() == null
                    || !entitlement.getEndDate().isBefore(today)) {
                continue;
            }
            final com.shivang.crm.modules.entitlement.entity.CustomerEntitlement expired = entitlement;
            boolean replaced = liveCoverage.stream().anyMatch(later -> replacesExpired(later, expired));
            if (replaced) {
                // A later coverage period is on record: this is a renewal, not a lapse.
                continue;
            }
            signals.add(expiredSignal(entitlement));
        }

        return signals;
    }

    private RiskSignalResponse expiredSignal(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement) {
        return RiskSignalResponse.builder()
                .accountId(entitlement.getAccountId())
                .type(RiskSignalType.ENTITLEMENT_EXPIRED)
                .severity(RiskSignalSeverity.HIGH)
                .reason("Entitlement \"" + label(entitlement) + "\" ended on "
                        + entitlement.getEndDate() + " and no later coverage for it is recorded")
                // The fact is the end of the coverage period, not when the row
                // happened to be created.
                .detectedAt(entitlement.getEndDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                .sourceType("ENTITLEMENT")
                .sourceId(entitlement.getId())
                .metadata(metadata(entitlement))
                .build();
    }

    private static boolean replacesExpired(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement later,
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement expired) {
        if (isExplicitlyLinked(later, expired)) {
            return true;
        }
        if (!sameCoverage(later, expired)) {
            return false;
        }
        LocalDate start = later.getStartDate();
        return start == null || !start.isAfter(expired.getEndDate().plusDays(1));
    }

    private static boolean isExplicitlyLinked(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement later,
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement expired) {
        if (expired.getId() != null && expired.getId().equals(later.getRenewedFromEntitlementId())) {
            return true;
        }
        if (later.getId() != null && later.getId().equals(expired.getRenewedToEntitlementId())) {
            return true;
        }
        return expired.getRenewalDealId() != null
                && expired.getRenewalDealId().equals(later.getRenewalDealId());
    }

    private static boolean sameCoverage(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement a,
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement b) {
        return a.getAccountId() != null
                && a.getAccountId().equals(b.getAccountId())
                && a.getOfferingId() != null
                && a.getOfferingId().equals(b.getOfferingId());
    }

    /**
     * Coverage that is still current as of {@code today}: ACTIVE or PENDING, and
     * not already past its end date.
     *
     * <p>PENDING counts because a forward-dated renewal is provisioned as PENDING
     * and no scheduler ever flips it to ACTIVE, so excluding it would report a
     * recorded renewal as a lapse. SUSPENDED does not count: it is paused
     * coverage, not continuing coverage.
     */
    private static boolean isLiveCoverage(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement, LocalDate today) {
        EntitlementStatus status = entitlement.getStatus();
        if (status != EntitlementStatus.ACTIVE && status != EntitlementStatus.PENDING) {
            return false;
        }
        return entitlement.getEndDate() != null && !entitlement.getEndDate().isBefore(today);
    }

    private Specification<com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> liveCoverageSpecification(
            Scope scope, UUID tenantId, UUID accountId, LocalDate today) {
        return Specification.allOf(
                CustomerEntitlementSpecifications.buildSpecification(
                        tenantId, accountId, null, null, null, null, null, today, null, null),
                (root, query, cb) -> root.get("status").in(
                        EntitlementStatus.ACTIVE, EntitlementStatus.PENDING),
                CustomerEntitlementSpecifications.visibleToUser(
                        scope.name(), scope.userId(), scope.teamUserIds()));
    }

    /**
     * Expired coverage, newest end date first. {@code <= today - 1} is the
     * {@link LocalDate} equivalent of "strictly before today".
     */
    private List<com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> expiredCoverage(
            Scope scope, UUID tenantId, UUID accountId, LocalDate today) {
        Specification<com.shivang.crm.modules.entitlement.entity.CustomerEntitlement> spec =
                Specification.allOf(
                        CustomerEntitlementSpecifications.buildSpecification(
                                tenantId, accountId, null, null, EntitlementStatus.EXPIRED, null, null,
                                null, today.minusDays(1), null),
                        CustomerEntitlementSpecifications.visibleToUser(
                                scope.name(), scope.userId(), scope.teamUserIds()));

        return entitlementRepository.findAll(spec,
                        Pageable.unpaged(Sort.by(Sort.Direction.DESC, "endDate")))
                .stream()
                .toList();
    }

    /**
     * Reports only that coverage is about to lapse or that its renewal decision
     * date is near. It says nothing about whether a renewal will happen, be
     * wanted, or be paid for — the CRM has no renewal obligation or payment model
     * to support such a claim.
     */
    private RiskSignalResponse approachingExpiry(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement) {

        LocalDate endDate = entitlement.getEndDate();
        LocalDate renewalDueDate = entitlement.getRenewalDueDate();
        boolean renewalDecidesFirst = Boolean.TRUE.equals(entitlement.getRenewable())
                && renewalDueDate != null
                && renewalDueDate.isBefore(endDate);

        String reason = renewalDecidesFirst
                ? "Entitlement \"" + label(entitlement) + "\" reaches its renewal decision date on "
                        + renewalDueDate + "; coverage runs to " + endDate
                : "Entitlement \"" + label(entitlement) + "\" expires on " + endDate;

        return RiskSignalResponse.builder()
                .accountId(entitlement.getAccountId())
                .type(RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason(reason)
                .detectedAt(entitlement.getCreatedAt())
                .sourceType("ENTITLEMENT")
                .sourceId(entitlement.getId())
                .metadata(metadata(entitlement))
                .build();
    }

    private String label(com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement) {
        if (entitlement.getName() != null && !entitlement.getName().isBlank()) {
            return entitlement.getName();
        }
        return entitlement.getCode() != null ? entitlement.getCode() : entitlement.getId().toString();
    }

    private Map<String, Object> metadata(
            com.shivang.crm.modules.entitlement.entity.CustomerEntitlement entitlement) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("status", entitlement.getStatus() == null ? null : entitlement.getStatus().name());
        metadata.put("startDate", entitlement.getStartDate());
        metadata.put("endDate", entitlement.getEndDate());
        metadata.put("renewalDueDate", entitlement.getRenewalDueDate());
        metadata.put("autoRenew", entitlement.getAutoRenew());
        metadata.put("renewable", entitlement.getRenewable());
        metadata.put("offeringId", entitlement.getOfferingId());
        return metadata;
    }

    // ---------- RISK-007: Deal ----------

    /**
     * Reports the lost deal as a lost deal. It is not evidence of contraction or
     * of churn: the CRM cannot tell a lost upsell from a lost new logo.
     */
    private List<RiskSignalResponse> recentLostDeals(
            Scope scope, UUID tenantId, UUID accountId, Instant from, Instant to) {

        Specification<com.shivang.crm.modules.deal.entity.Deal> spec =
                Specification.allOf(
                        DealSpecifications.byTenantId(tenantId),
                        DealSpecifications.notDeleted(),
                        DealSpecifications.visibleToUser(
                                scope.name(), scope.userId(), scope.teamUserIds()),
                        DealSpecifications.byRecordCategory(RecordCategory.CLOSED_LOST),
                        (root, query, cb) -> lossWindow(cb, root, from, to),
                        accountId == null
                                ? (root, query, cb) -> cb.conjunction()
                                : DealSpecifications.byAccountId(accountId));

        return dealRepository.findAll(spec,
                        Pageable.unpaged(
                                Sort.by(Sort.Order.desc("closedDate").nullsLast(),
                                        Sort.Order.desc("stageEnteredAt"))))
                .stream()
                .filter(deal -> inWindow(lossAt(deal), from, to))
                .map(this::dealLossSignal)
                .toList();
    }

    private RiskSignalResponse dealLossSignal(com.shivang.crm.modules.deal.entity.Deal deal) {
        return RiskSignalResponse.builder()
                .accountId(deal.getAccountId())
                .type(RiskSignalType.RECENT_DEAL_LOSS)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason("Deal \"" + deal.getName() + "\" was lost"
                        + (deal.getLostReason() == null || deal.getLostReason().isBlank()
                                ? ""
                                : ": " + deal.getLostReason()))
                .detectedAt(lossAt(deal))
                .sourceType("DEAL")
                .sourceId(deal.getId())
                .metadata(metadata(deal))
                .build();
    }

    /**
     * The date the deal actually closed is the loss event, not the date the row
     * was created. {@code closedDate} is the business close date and is written
     * for every closed deal, including a backdated one; it is date-only, so its
     * loss instant is that date at 00:00 UTC.
     *
     * <p>Legacy/inconsistent rows without {@code closedDate} fall back to
     * {@code stageEnteredAt}, the exact instant the deal entered its current
     * stage. When both are present, {@code closedDate} wins: it is the recorded
     * business fact, while {@code stageEnteredAt} can reflect delayed CRM entry.
     * Rows with neither timestamp are not turned into a claim about "recent".
     */
    private static Instant lossAt(com.shivang.crm.modules.deal.entity.Deal deal) {
        if (deal.getClosedDate() != null) {
            return deal.getClosedDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
        return deal.getStageEnteredAt();
    }

    private static boolean inWindow(Instant value, Instant from, Instant to) {
        return value != null
                && (from == null || !value.isBefore(from))
                && (to == null || value.isBefore(to));
    }

    /**
     * SQL-side equivalent of {@link #lossAt}: closedDate first, then
     * stageEnteredAt only when closedDate is absent. The closedDate half uses
     * date arithmetic because the column is date-only, and the stage half keeps
     * the normal half-open {@code [from, to)} instant comparison.
     */
    private static jakarta.persistence.criteria.Predicate lossWindow(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<com.shivang.crm.modules.deal.entity.Deal> root,
            Instant from,
            Instant to) {

        var closedDate = root.<LocalDate>get("closedDate");
        var stageEnteredAt = root.<Instant>get("stageEnteredAt");

        jakarta.persistence.criteria.Predicate closedDateWindow;
        if (from == null && to == null) {
            closedDateWindow = cb.isNotNull(closedDate);
        } else {
            LocalDate lower = from == null ? null : firstClosedDateAtOrAfter(from);
            LocalDate upper = to == null ? null : lastClosedDateBefore(to);
            if (lower != null && upper != null && lower.isAfter(upper)) {
                closedDateWindow = cb.disjunction();
            } else if (lower != null && upper != null) {
                closedDateWindow = cb.between(closedDate, lower, upper);
            } else if (lower != null) {
                closedDateWindow = cb.greaterThanOrEqualTo(closedDate, lower);
            } else {
                closedDateWindow = cb.lessThanOrEqualTo(closedDate, upper);
            }
        }

        jakarta.persistence.criteria.Predicate stageFallback = cb.and(
                cb.isNull(closedDate),
                from == null
                        ? cb.conjunction()
                        : cb.greaterThanOrEqualTo(stageEnteredAt, from),
                to == null
                        ? cb.conjunction()
                        : cb.lessThan(stageEnteredAt, to));

        return cb.or(closedDateWindow, stageFallback);
    }

    /** First UTC calendar date whose 00:00 is at or after {@code from}. */
    private static LocalDate firstClosedDateAtOrAfter(Instant from) {
        LocalDate date = from.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return from.equals(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                ? date
                : date.plusDays(1);
    }

    /** Last UTC calendar date whose 00:00 is strictly before {@code to}. */
    private static LocalDate lastClosedDateBefore(Instant to) {
        LocalDate date = to.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return to.equals(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
                ? date.minusDays(1)
                : date;
    }

    private Map<String, Object> metadata(com.shivang.crm.modules.deal.entity.Deal deal) {
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("dealName", deal.getName());
        metadata.put("stage", deal.getStage() == null ? null : deal.getStage().getName());
        metadata.put("closedDate", deal.getClosedDate());
        metadata.put("stageEnteredAt", deal.getStageEnteredAt());
        if (deal.getLostReason() != null && !deal.getLostReason().isBlank()) {
            metadata.put("lostReason", deal.getLostReason());
        }
        return metadata;
    }

    // ---------- scope + ordering ----------

    /**
     * Resolves one domain's read scope. Returns an invisible {@link Scope} when the
     * caller lacks the permission, so that domain contributes no signals instead of
     * failing the whole request.
     */
    private Scope scope(UUID userId, UUID tenantId, String module, String action) {
        String name = permissionEvaluatorService.getAccessScope(userId, tenantId, module, action);
        boolean visible = "ALL".equals(name) || "TEAM".equals(name) || "OWN".equals(name);
        List<UUID> team = "TEAM".equals(name)
                ? permissionEvaluatorService.getTeamUserIds(userId, tenantId)
                : List.of();
        return new Scope(visible, name, userId, team);
    }

    /**
     * Severity first, then newest first. Total order with source id and type as
     * tiebreakers so pagination cannot repeat or skip a signal when timestamps
     * collide. This mirrors the SQL ordering exactly.
     */
    public static Comparator<RiskSignalResponse> displayOrder() {
        return Comparator
                .comparingInt((RiskSignalResponse signal) -> signal.getSeverity().rank())
                .thenComparing(RiskSignalResponse::getDetectedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RiskSignalResponse::getSourceId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(signal -> signal.getType().name());
    }

    private record Scope(boolean visible, String name, UUID userId, List<UUID> teamUserIds) {
    }
}