package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalSeverity;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalType;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository;
import com.shivang.crm.modules.feedback.repository.FeedbackAnalyticsRepository.ScoreBucket;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.repository.RiskSignalQueryRepository;
import com.shivang.crm.modules.feedback.service.RiskSignalPage;
import com.shivang.crm.modules.feedback.service.RiskSignalService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.repository.TicketRepository;

/**
 * L3.4-P5: the persistence predicates behind customer-health risk signals.
 *
 * <p>These tests drive the real service against the migrated PostgreSQL schema.
 * The previous controller test mocks repositories, so it can prove rule branches
 * but not that a window, a per-account latest-row query, or a source predicate is
 * actually evaluated in SQL. Every assertion here depends on real rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RiskSignalPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private CustomerEntitlementRepository entitlementRepository;

    @Autowired
    private FeedbackAnalyticsRepository analyticsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RiskSignalQueryRepository riskSignalQueryRepository;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID userId;
    private PermissionEvaluatorService permissions;
    private RiskSignalService service;
    private final Map<String, UUID> stageIds = new HashMap<>();

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        insertTenant(tenantId, "Risk Test A");
        insertTenant(otherTenantId, "Risk Test B");
        insertUser(userId, tenantId, "risk");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));

        permissions = mock(PermissionEvaluatorService.class);
        when(permissions.getAccessScope(eq(userId), eq(tenantId), anyString(), eq("read")))
                .thenReturn("ALL");

        service = new RiskSignalService(
                feedbackRepository,
                ticketRepository,
                dealRepository,
                entitlementRepository,
                riskSignalQueryRepository,
                permissions,
                mock(RecordScopeGuard.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void feedbackSilenceIsEvaluatedPerAccountAndIgnoresInternalRows() {
        UUID silent = insertAccount(tenantId, "Silent");
        UUID recent = insertAccount(tenantId, "Recent");
        UUID internalOnly = insertAccount(tenantId, "Internal only");
        UUID mixed = insertAccount(tenantId, "Mixed");
        UUID deletedOnly = insertAccount(tenantId, "Deleted only");
        UUID never = insertAccount(tenantId, "Never gave feedback");
        UUID otherTenantAccount = insertAccount(otherTenantId, "Other tenant");

        UUID silentFeedback = insertFeedback(
                tenantId, silent, FeedbackType.CSAT, 4, FeedbackSource.WEBSITE,
                instant("2026-04-01T00:00:00Z"), false);
        UUID mixedFeedback = insertFeedback(
                tenantId, mixed, FeedbackType.CSAT, 4, FeedbackSource.WEBSITE,
                instant("2026-04-02T00:00:00Z"), false);

        insertFeedback(tenantId, recent, FeedbackType.CSAT, 4, FeedbackSource.WEBSITE,
                instant("2026-06-15T00:00:00Z"), false);
        insertFeedback(tenantId, internalOnly, FeedbackType.CSAT, 4, FeedbackSource.INTERNAL,
                instant("2026-04-01T00:00:00Z"), false);
        insertFeedback(tenantId, mixed, FeedbackType.CSAT, 5, FeedbackSource.INTERNAL,
                instant("2026-06-15T00:00:00Z"), false);
        insertFeedback(tenantId, deletedOnly, FeedbackType.CSAT, 4, FeedbackSource.WEBSITE,
                instant("2026-04-01T00:00:00Z"), true);
        insertFeedback(otherTenantId, otherTenantAccount, FeedbackType.CSAT, 4, FeedbackSource.WEBSITE,
                instant("2026-04-01T00:00:00Z"), false);

        List<RiskSignalResponse> silence = signals().stream()
                .filter(signal -> signal.getType() == RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT)
                .toList();

        assertThat(silence)
                .as("only accounts whose latest customer feedback is stale may be silent")
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactlyInAnyOrder(silentFeedback, mixedFeedback);
        assertThat(silence)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactlyInAnyOrder(silent, mixed)
                .doesNotContain(recent, internalOnly, deletedOnly, never, otherTenantAccount);
    }

    @Test
    void internalAndUnattributedFeedbackDoNotFeedCustomerRiskRules() {
        UUID external = insertAccount(tenantId, "External");
        UUID internal = insertAccount(tenantId, "Internal");
        UUID unattributed = insertAccount(tenantId, "Unattributed");

        insertFeedback(tenantId, external, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, internal, FeedbackType.NPS, 3, FeedbackSource.INTERNAL,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, unattributed, FeedbackType.NPS, 3, null,
                instant("2026-06-10T00:00:00Z"), false);

        insertFeedback(tenantId, internal, FeedbackType.COMPLAINT, null, FeedbackSource.INTERNAL,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, internal, FeedbackType.CSAT, 1, FeedbackSource.INTERNAL,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, internal, FeedbackType.CES, 7, FeedbackSource.INTERNAL,
                instant("2026-06-10T00:00:00Z"), false);

        List<RiskSignalResponse> signals = signals();

        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.NEGATIVE_NPS)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactly(external);
        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.RECENT_COMPLAINT
                        || signal.getType() == RiskSignalType.LOW_CSAT
                        || signal.getType() == RiskSignalType.HIGH_CES)
                .isEmpty();
    }

    @Test
    void feedbackRulesApplyTheirTypeScoreAndWindowOnRealRows() {
        UUID negative = insertAccount(tenantId, "Negative NPS");
        UUID negativeAtWindowStart = insertAccount(tenantId, "NPS at window start");
        UUID npsAtCutoff = insertAccount(tenantId, "NPS at excluded end");
        UUID npsBeforeWindow = insertAccount(tenantId, "NPS before window");
        UUID npsAtThreshold = insertAccount(tenantId, "NPS at threshold");
        UUID lowCsat = insertAccount(tenantId, "Low CSAT");
        UUID csatAtThreshold = insertAccount(tenantId, "CSAT at threshold");
        UUID highCes = insertAccount(tenantId, "High CES");
        UUID cesAtThreshold = insertAccount(tenantId, "CES at threshold");
        UUID complaint = insertAccount(tenantId, "Complaint");

        insertFeedback(tenantId, negative, FeedbackType.NPS, 6, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, negativeAtWindowStart, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                FROM, false);
        insertFeedback(tenantId, npsAtCutoff, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                TO, false);
        insertFeedback(tenantId, npsBeforeWindow, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                FROM.minusSeconds(1), false);
        insertFeedback(tenantId, npsAtThreshold, FeedbackType.NPS, 7, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, lowCsat, FeedbackType.CSAT, 2, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, csatAtThreshold, FeedbackType.CSAT, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, highCes, FeedbackType.CES, 4, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, cesAtThreshold, FeedbackType.CES, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, complaint, FeedbackType.COMPLAINT, null, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);

        List<RiskSignalResponse> signals = signals();

        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.NEGATIVE_NPS)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactlyInAnyOrder(negative, negativeAtWindowStart);
        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.LOW_CSAT)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactly(lowCsat);
        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.HIGH_CES)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactly(highCes);
        assertThat(signals)
                .filteredOn(signal -> signal.getType() == RiskSignalType.RECENT_COMPLAINT)
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactly(complaint);
    }

    @Test
    void analyticsIgnoreInternalAndUnattributedFeedback() {
        UUID account = insertAccount(tenantId, "Analytics");

        insertFeedback(tenantId, account, FeedbackType.CSAT, 5, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, account, FeedbackType.CSAT, 5, FeedbackSource.INTERNAL,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, account, FeedbackType.CSAT, 5, null,
                instant("2026-06-10T00:00:00Z"), false);

        List<ScoreBucket> buckets = analyticsRepository.countByTypeAndScore(
                tenantId, userId, "ALL", List.of(), FROM, TO, account);

        assertThat(buckets).containsExactly(new ScoreBucket(FeedbackType.CSAT, 5, 1));
    }

    @Test
    void dealLossWindowUsesTheActualLossDateNotCreationTime() {
        UUID lostLongAgo = insertDeal(tenantId, "Lost long ago", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), false);
        UUID lostAtWindowStart = insertDeal(tenantId, "Lost at window start", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-01"),
                instant("2025-01-01T00:00:00Z"), false);

        insertDeal(tenantId, "Created inside, lost outside", "CLOSED_LOST",
                instant("2026-06-10T00:00:00Z"), date("2026-04-01"),
                instant("2026-06-10T00:00:00Z"), false);
        insertDeal(tenantId, "Lost at excluded end", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-07-01"),
                instant("2025-01-01T00:00:00Z"), false);

        UUID fallback = insertDeal(tenantId, "Legacy fallback", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), null,
                instant("2026-06-15T12:00:00Z"), false);
        insertDeal(tenantId, "Legacy fallback outside", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), null,
                instant("2026-04-01T00:00:00Z"), false);
        insertDeal(tenantId, "No loss timestamp", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), null, null, false);

        UUID inconsistent = insertDeal(tenantId, "Closed date wins", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2026-04-01T00:00:00Z"), false);

        insertDeal(tenantId, "Deleted lost deal", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), true);
        insertDeal(otherTenantId, "Other tenant lost deal", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), false);
        insertDeal(tenantId, "Won deal", "CLOSED_WON",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), false);

        List<RiskSignalResponse> losses = signals().stream()
                .filter(signal -> signal.getType() == RiskSignalType.RECENT_DEAL_LOSS)
                .toList();

        assertThat(losses)
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactlyInAnyOrder(lostLongAgo, lostAtWindowStart, fallback, inconsistent);

        RiskSignalResponse closedDateLoss = losses.stream()
                .filter(signal -> signal.getSourceId().equals(lostLongAgo))
                .findFirst()
                .orElseThrow();
        assertThat(closedDateLoss.getDetectedAt())
                .as("closedDate is a date; its loss instant is 00:00 UTC")
                .isEqualTo(instant("2026-06-10T00:00:00Z"));
    }

    @Test
    void crossAccountPageCountsTheWholeSetBeforeApplyingTheLimit() {
        UUID account = insertAccount(tenantId, "Bulk");
        for (int i = 0; i < 505; i++) {
            insertFeedback(tenantId, account, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                    instant("2026-06-10T00:00:00Z").plusSeconds(i), false);
        }

        RiskSignalPage first = page(RiskSignalType.NEGATIVE_NPS, null, 0, 20);
        RiskSignalPage last = page(RiskSignalType.NEGATIVE_NPS, null, 25, 20);

        assertThat(first.totalElements())
                .as("the old per-source 500-row ceiling must be gone")
                .isEqualTo(505);
        assertThat(first.signals()).hasSize(20);
        assertThat(last.signals()).hasSize(5);
        assertThat(last.totalPages()).isEqualTo(26);
    }

    @Test
    void crossAccountPageUsesTheHalfOpenUtcWindowRegardlessOfJvmTimezone() {
        UUID account = insertAccount(tenantId, "Window");
        insertFeedback(tenantId, account, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                FROM.minusSeconds(1), false);
        UUID atFrom = insertFeedback(tenantId, account, FeedbackType.NPS, 3,
                FeedbackSource.WEBSITE, FROM, false);
        insertFeedback(tenantId, account, FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                TO, false);

        TimeZone original = TimeZone.getDefault();
        RiskSignalPage result;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Calcutta"));
            result = page(RiskSignalType.NEGATIVE_NPS, null, 0, 20);
        } finally {
            TimeZone.setDefault(original);
        }

        assertThat(result.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(atFrom);
    }

    @Test
    void crossAccountPageFiltersBySeverityAndOrdersNewestFirstWithinSeverity() {
        UUID account = insertAccount(tenantId, "Ordered");
        insertFeedback(tenantId, account, FeedbackType.COMPLAINT, null, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false);
        insertFeedback(tenantId, account, FeedbackType.COMPLAINT, null, FeedbackSource.WEBSITE,
                instant("2026-06-12T00:00:00Z"), false);
        insertFeedback(tenantId, account, FeedbackType.COMPLAINT, null, FeedbackSource.WEBSITE,
                instant("2026-06-11T00:00:00Z"), false);
        insertFeedback(tenantId, account, FeedbackType.CSAT, 2, FeedbackSource.WEBSITE,
                instant("2026-06-20T00:00:00Z"), false);

        RiskSignalPage highFirst = page(null, RiskSignalSeverity.HIGH, 0, 2);
        RiskSignalPage medium = page(RiskSignalType.LOW_CSAT, null, 0, 20);
        RiskSignalPage impossible = page(RiskSignalType.LOW_CSAT, RiskSignalSeverity.HIGH, 0, 20);

        assertThat(highFirst.totalElements()).isEqualTo(3);
        assertThat(highFirst.signals())
                .extracting(RiskSignalResponse::getDetectedAt)
                .containsExactly(instant("2026-06-12T00:00:00Z"),
                        instant("2026-06-11T00:00:00Z"));
        assertThat(medium.signals())
                .hasSize(1)
                .allMatch(signal -> signal.getSeverity() == RiskSignalSeverity.MEDIUM);
        assertThat(impossible.totalElements()).isZero();
        assertThat(impossible.signals()).isEmpty();
    }

    @Test
    void expiredEntitlementIsShownForACoverageGapButNotForALinkedOrContinuousRenewal() {
        UUID gapAccount = insertAccount(tenantId, "Gap");
        UUID gapOffering = UUID.randomUUID();
        UUID gapExpired = insertEntitlement(gapAccount, gapOffering, EntitlementStatus.EXPIRED,
                date("2026-01-01"), date("2026-04-01"), null, null, null);
        insertEntitlement(gapAccount, gapOffering, EntitlementStatus.PENDING,
                date("2026-07-01"), date("2027-01-01"), null, null, null);

        UUID continuousAccount = insertAccount(tenantId, "Continuous");
        UUID continuousOffering = UUID.randomUUID();
        insertEntitlement(continuousAccount, continuousOffering, EntitlementStatus.EXPIRED,
                date("2026-01-01"), date("2026-04-01"), null, null, null);
        insertEntitlement(continuousAccount, continuousOffering, EntitlementStatus.PENDING,
                date("2026-04-02"), date("2027-01-01"), null, null, null);

        UUID linkedAccount = insertAccount(tenantId, "Linked");
        UUID linkedOffering = UUID.randomUUID();
        UUID linkedExpired = insertEntitlement(linkedAccount, linkedOffering, EntitlementStatus.EXPIRED,
                date("2026-01-01"), date("2026-04-01"), null, null, null);
        insertEntitlement(linkedAccount, linkedOffering, EntitlementStatus.PENDING,
                date("2026-07-01"), date("2027-01-01"), linkedExpired, null, null);

        RiskSignalPage expired = page(RiskSignalType.ENTITLEMENT_EXPIRED, null, 0, 20);

        assertThat(expired.totalElements()).isEqualTo(1);
        assertThat(expired.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(gapExpired);
    }

    @Test
    void crossAccountPageAppliesOwnerVisibilityAndTenantIsolation() {
        UUID otherUser = UUID.randomUUID();
        insertUser(otherUser, tenantId, "other");
        UUID mine = insertFeedback(tenantId, insertAccount(tenantId, "Mine"),
                FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false, userId);
        insertFeedback(tenantId, insertAccount(tenantId, "Theirs"),
                FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false, otherUser);
        insertFeedback(otherTenantId, insertAccount(otherTenantId, "Other tenant"),
                FeedbackType.NPS, 3, FeedbackSource.WEBSITE,
                instant("2026-06-10T00:00:00Z"), false, userId);

        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("feedback"), eq("read")))
                .thenReturn("OWN");

        RiskSignalPage result = page(RiskSignalType.NEGATIVE_NPS, null, 0, 20);

        assertThat(result.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(mine);
    }

    @Test
    void accountReadScopeConstrainsSignalsEvenWhenTheSourceScopeIsAll() {
        UUID otherUser = UUID.randomUUID();
        insertUser(otherUser, tenantId, "account-other");
        UUID mine = insertAccount(tenantId, "Mine");
        UUID theirs = insertAccountOwnedBy(tenantId, "Theirs", otherUser);
        UUID mineTicket = insertTicket(tenantId, mine, userId, true);
        UUID theirTicket = insertTicket(tenantId, theirs, otherUser, true);

        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("account"), eq("read")))
                .thenReturn("OWN");
        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("ticket"), eq("read")))
                .thenReturn("ALL");

        RiskSignalPage result = page(RiskSignalType.SLA_BREACHED_TICKET, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(mineTicket)
                .doesNotContain(theirTicket);
        assertThat(result.signals())
                .extracting(RiskSignalResponse::getAccountId)
                .containsExactly(mine);
    }

    @Test
    void accountTeamScopeIncludesTeamOwnedAccountsAndExcludesOthers() {
        UUID teammate = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        insertUser(teammate, tenantId, "teammate");
        insertUser(outsider, tenantId, "outsider");
        UUID teamAccount = insertAccountOwnedBy(tenantId, "Team account", teammate);
        UUID outsideAccount = insertAccountOwnedBy(tenantId, "Outside account", outsider);
        UUID teamTicket = insertTicket(tenantId, teamAccount, teammate, true);
        insertTicket(tenantId, outsideAccount, outsider, true);

        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("account"), eq("read")))
                .thenReturn("TEAM");
        when(permissions.getTeamUserIds(eq(userId), eq(tenantId)))
                .thenReturn(List.of(teammate));
        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("ticket"), eq("read")))
                .thenReturn("ALL");

        RiskSignalPage result = page(RiskSignalType.SLA_BREACHED_TICKET, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(teamTicket);
    }

    @Test
    void unattachedDealAndEntitlementSignalsRemainSourceScopedWithNullAccount() {
        UUID otherUser = UUID.randomUUID();
        insertUser(otherUser, tenantId, "unattached-other");
        UUID outsideAccount = insertAccountOwnedBy(tenantId, "Outside account", otherUser);

        UUID unattachedDeal = insertDeal(
                tenantId, "Unattached loss", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), false);
        UUID outsideDeal = insertDeal(
                tenantId, "Outside loss", "CLOSED_LOST",
                instant("2025-01-01T00:00:00Z"), date("2026-06-10"),
                instant("2025-01-01T00:00:00Z"), false,
                outsideAccount, otherUser);

        UUID unattachedExpired = insertEntitlement(null, UUID.randomUUID(),
                EntitlementStatus.EXPIRED, date("2026-01-01"), date("2026-04-01"),
                null, null, null);
        insertEntitlement(outsideAccount, UUID.randomUUID(),
                EntitlementStatus.EXPIRED, date("2026-01-01"), date("2026-04-01"),
                null, null, null);

        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("account"), eq("read")))
                .thenReturn("OWN");
        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("deal"), eq("read")))
                .thenReturn("ALL");
        when(permissions.getAccessScope(eq(userId), eq(tenantId), eq("entitlement"), eq("read")))
                .thenReturn("ALL");

        RiskSignalPage losses = page(RiskSignalType.RECENT_DEAL_LOSS, null, 0, 20);
        assertThat(losses.totalElements()).isEqualTo(1);
        assertThat(losses.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(unattachedDeal)
                .doesNotContain(outsideDeal);
        assertThat(losses.signals().get(0).getAccountId()).isNull();

        RiskSignalPage expiries = page(RiskSignalType.ENTITLEMENT_EXPIRED, null, 0, 20);
        assertThat(expiries.totalElements()).isEqualTo(1);
        assertThat(expiries.signals())
                .extracting(RiskSignalResponse::getSourceId)
                .containsExactly(unattachedExpired);
        assertThat(expiries.signals().get(0).getAccountId()).isNull();
    }

    private RiskSignalPage page(
            RiskSignalType type, RiskSignalSeverity severity, int page, int size) {
        return service.riskSignalsPage(
                tenantId, null, FROM.toString(), TO.toString(), type, severity, page, size);
    }

    private UUID insertEntitlement(
            UUID account,
            UUID offering,
            EntitlementStatus status,
            LocalDate startDate,
            LocalDate endDate,
            UUID renewedFrom,
            UUID renewedTo,
            UUID renewalDeal) {

        CustomerEntitlement entitlement = CustomerEntitlement.builder()
                .tenantId(tenantId)
                .accountId(account)
                .offeringId(offering)
                .name("Coverage " + offering)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .renewedFromEntitlementId(renewedFrom)
                .renewedToEntitlementId(renewedTo)
                .renewalDealId(renewalDeal)
                .ownerId(userId)
                .createdBy(userId)
                .build();
        return entitlementRepository.saveAndFlush(entitlement).getId();
    }

    private List<RiskSignalResponse> signals() {
        return service.riskSignals(tenantId, null, FROM.toString(), TO.toString());
    }

    private UUID insertTenant(UUID id, String name) {
        jdbcTemplate.update(
                "insert into tenants (id, name, slug, created_at, updated_at) values (?, ?, ?, now(), now())",
                id, name, "risk-test-" + id);
        return id;
    }

    private void insertUser(UUID id, UUID tenant, String prefix) {
        jdbcTemplate.update(
                "insert into users (id, tenant_id, email, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, ?, now(), now())",
                id, tenant, prefix + "-" + id + "@example.com", "not-used");
    }

    private UUID insertAccount(UUID tenant, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into accounts (id, tenant_id, created_by, name, email, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, false, now(), now())",
                id, tenant, userId, name + " " + id, id + "@example.com");
        return id;
    }

    private UUID insertAccountOwnedBy(UUID tenant, String name, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into accounts (id, tenant_id, owner_user_id, created_by, name, email, "
                        + "deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, false, now(), now())",
                id, tenant, owner, owner, name + " " + id, id + "@example.com");
        return id;
    }

    private UUID insertTicket(UUID tenant, UUID account, UUID owner, boolean slaBreached) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tickets (id, tenant_id, owner_user_id, created_by, account_id, subject, "
                        + "status, sla_breached, deleted, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, 'OPEN', ?, false, now(), now())",
                id, tenant, owner, owner, account, "Ticket " + id, slaBreached);
        return id;
    }

    private UUID insertFeedback(
            UUID tenant,
            UUID account,
            FeedbackType type,
            Integer score,
            FeedbackSource source,
            Instant createdAt,
            boolean deleted) {
        return insertFeedback(tenant, account, type, score, source, createdAt, deleted, userId);
    }

    private UUID insertFeedback(
            UUID tenant,
            UUID account,
            FeedbackType type,
            Integer score,
            FeedbackSource source,
            Instant createdAt,
            boolean deleted,
            UUID owner) {

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into feedback (id, tenant_id, account_id, type, score, source, "
                            + "owner_user_id, created_by, created_at, updated_at, deleted) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setObject(3, account);
            ps.setString(4, type.name());
            if (score == null) {
                ps.setNull(5, Types.SMALLINT);
            } else {
                ps.setInt(5, score);
            }
            if (source == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, source.name());
            }
            ps.setObject(7, owner);
            ps.setObject(8, owner);
            ps.setTimestamp(9, Timestamp.from(createdAt), utcCalendar());
            ps.setTimestamp(10, Timestamp.from(createdAt), utcCalendar());
            ps.setBoolean(11, deleted);
            return ps;
        });
        return id;
    }

    private UUID insertDeal(
            UUID tenant,
            String name,
            String recordCategory,
            Instant createdAt,
            LocalDate closedDate,
            Instant stageEnteredAt,
            boolean deleted) {
        return insertDeal(tenant, name, recordCategory, createdAt, closedDate, stageEnteredAt,
                deleted, null, userId);
    }

    private UUID insertDeal(
            UUID tenant,
            String name,
            String recordCategory,
            Instant createdAt,
            LocalDate closedDate,
            Instant stageEnteredAt,
            boolean deleted,
            UUID accountId,
            UUID owner) {

        UUID id = UUID.randomUUID();
        UUID stageId = stageId(tenant, recordCategory);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into deals (id, tenant_id, name, stage_id, account_id, closed_date, "
                            + "stage_entered_at, created_at, updated_at, deleted, owner_user_id, created_by) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, name + " " + id);
            ps.setObject(4, stageId);
            if (accountId == null) {
                ps.setNull(5, Types.OTHER);
            } else {
                ps.setObject(5, accountId);
            }
            if (closedDate == null) {
                ps.setNull(6, Types.DATE);
            } else {
                ps.setObject(6, closedDate);
            }
            if (stageEnteredAt == null) {
                ps.setNull(7, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(7, Timestamp.from(stageEnteredAt), utcCalendar());
            }
            ps.setTimestamp(8, Timestamp.from(createdAt), utcCalendar());
            ps.setTimestamp(9, Timestamp.from(createdAt), utcCalendar());
            ps.setBoolean(10, deleted);
            if (owner == null) {
                ps.setNull(11, Types.OTHER);
                ps.setObject(12, userId);
            } else {
                ps.setObject(11, owner);
                ps.setObject(12, owner);
            }
            return ps;
        });
        return id;
    }

    private UUID stageId(UUID tenant, String recordCategory) {
        return stageIds.computeIfAbsent(tenant + "|" + recordCategory, key -> {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into deal_stages (id, tenant_id, name, record_category, is_closed, "
                            + "is_default, display_order, created_at, updated_at, deleted) "
                            + "values (?, ?, ?, ?, ?, false, 0, now(), now(), false)",
                    id, tenant, recordCategory + " " + id, recordCategory,
                    !"OPEN".equals(recordCategory));
            return id;
        });
    }

    private static java.util.Calendar utcCalendar() {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}

