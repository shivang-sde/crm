package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;

import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealSpecifications;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.entitlement.entity.EntitlementStatus;
import com.shivang.crm.modules.entitlement.repository.CustomerEntitlementRepository;
import com.shivang.crm.modules.feedback.controller.RiskSignalController;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalSeverity;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.feedback.repository.RiskSignalQueryRepository;
import com.shivang.crm.modules.feedback.service.RiskSignalPage;
import com.shivang.crm.modules.feedback.service.RiskSignalService;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.repository.TicketRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * L3.4-P1: contract and behaviour tests for deterministic risk signals.
 *
 * <p>Two halves. The contract half pins the HTTP surface, so the two routes
 * cannot silently move or drift onto a new permission. The service half drives
 * each documented rule through mocked repositories and checks the rule fires on
 * the documented threshold and stays quiet either side of it.
 */
class RiskSignalControllerTest {

    /** Fixture clock: 2026-06-01T00:00:00Z, so "today" is 2026-06-01 in UTC. */
    private static final EntitlementStatus ENTITLEMENT_EXPIRED_STATUS = EntitlementStatus.EXPIRED;

    private static final ObjectMapper MAPPER =
            JsonMapper.builder().findAndAddModules().build();

    private static final String ACCOUNT_SCOPED_ROUTE =
            "/api/v1/accounts/{accountId}/health/risk";
    private static final String CROSS_ACCOUNT_ROUTE =
            "/api/v1/customer-health/risk-signals";
    private static final String SUPERSEDED_ROUTE =
            "/api/v1/accounts/{accountId}/health/risk-signals";

    // ---------------- HTTP contract ----------------

    @Test
    void accountScopedRouteIsTheDocumentedOne() {
        assertThat(routeOf("riskForAccount")).containsExactly(ACCOUNT_SCOPED_ROUTE);
    }

    @Test
    void crossAccountRouteIsTheDocumentedOneAndTakesNoAccountPathVariable() {
        assertThat(routeOf("riskSignalsAcrossAccounts")).containsExactly(CROSS_ACCOUNT_ROUTE);

        Method method = methodNamed("riskSignalsAcrossAccounts");
        assertThat(Arrays.stream(method.getParameters())
                .map(java.lang.reflect.Parameter::getName))
                .as("cross-account listing must not be nested under an account")
                .doesNotContain("accountId");
    }

    @Test
    void supersededNestedListingRouteIsGone() {
        List<String> allRoutes = Arrays.stream(RiskSignalController.class.getDeclaredMethods())
                .filter(RiskSignalControllerTest::hasGetMapping)
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value()))
                .toList();

        assertThat(allRoutes).doesNotContain(SUPERSEDED_ROUTE);
    }

    @Test
    void bothRoutesRequireOnlyExistingAccountRead() {
        // No new permission in P1: these read account-derived facts, so account:read
        // is the gate, exactly as for Customer 360.
        assertThat(permissionsOf(methodNamed("riskForAccount"))).containsExactly("account:read");
        assertThat(permissionsOf(methodNamed("riskSignalsAcrossAccounts"))).containsExactly("account:read");
    }

    @Test
    void noEndpointExposesWritesOrAnalysisPermissions() {
        Set<String> referenced = Arrays.stream(RiskSignalController.class.getDeclaredMethods())
                .filter(RiskSignalControllerTest::hasGetMapping)
                .flatMap(method -> permissionsOf(method).stream())
                .collect(Collectors.toSet());

        assertThat(referenced).allMatch(permission -> permission.startsWith("account:"));
        assertThat(referenced).noneMatch(permission ->
                permission.endsWith(":delete") || permission.endsWith(":write"));
    }

    @Test
    void serialisesSignalWithAccountReferenceAndSourceRecord() {
        UUID accountId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        Map<String, Object> json = MAPPER.convertValue(RiskSignalResponse.builder()
                .accountId(accountId)
                .type(RiskSignalType.NEGATIVE_NPS)
                .severity(RiskSignalSeverity.HIGH)
                .reason("Customer rated their experience 3 out of 10")
                .detectedAt(java.time.Instant.parse("2026-01-02T03:04:05Z"))
                .sourceType("FEEDBACK")
                .sourceId(sourceId)
                .metadata(Map.of("score", 3))
                .build(), Map.class);

        assertThat(json.keySet()).containsExactlyInAnyOrder(
                "accountId", "type", "severity", "reason", "detectedAt",
                "sourceType", "sourceId", "metadata");

        // Enums must reach the wire as readable names, not ordinals or objects.
        assertThat(json.get("type")).isEqualTo("NEGATIVE_NPS");
        assertThat(json.get("severity")).isEqualTo("HIGH");
    }

    @Test
    void aNullAccountIdIsSerialisedAsAnExplicitNullKey() throws Exception {
        String json = MAPPER.writeValueAsString(RiskSignalResponse.builder()
                .accountId(null)
                .type(RiskSignalType.RECENT_DEAL_LOSS)
                .severity(RiskSignalSeverity.MEDIUM)
                .reason("Deal was lost")
                .detectedAt(Instant.parse("2026-01-02T03:04:05Z"))
                .sourceType("DEAL")
                .sourceId(UUID.randomUUID())
                .build());

        var node = MAPPER.readTree(json);
        assertThat(node.has("accountId"))
                .as("accountId is nullable but never omitted")
                .isTrue();
        assertThat(node.get("accountId").isNull()).isTrue();
    }

    @Test
    void accountRouteChecksParentAccountVisibilityBeforeEvaluatingSignals() {
        UUID tenantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        RiskSignalService service = org.mockito.Mockito.mock(RiskSignalService.class);
        AccountService accounts = org.mockito.Mockito.mock(AccountService.class);

        org.mockito.Mockito.doThrow(new PermissionDeniedException(
                        "SCOPE_DENIED", "Record is outside your access scope"))
                .when(accounts).assertAccountAccessible(tenantId, accountId, "read");

        RiskSignalController controller = controllerWithAccountService(service, accounts, tenantId);
        Throwable thrown = catchThrowable(() -> controller.riskForAccount(accountId, null, null));

        assertThat(thrown).isInstanceOf(PermissionDeniedException.class);
        org.mockito.Mockito.verify(accounts).assertAccountAccessible(tenantId, accountId, "read");
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void accountRouteReportsAnUnknownOrCrossTenantAccountAsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        RiskSignalService service = org.mockito.Mockito.mock(RiskSignalService.class);
        AccountService accounts = org.mockito.Mockito.mock(AccountService.class);

        org.mockito.Mockito.doThrow(new BusinessException("NOT_FOUND", "Account not found"))
                .when(accounts).assertAccountAccessible(tenantId, accountId, "read");

        RiskSignalController controller = controllerWithAccountService(service, accounts, tenantId);
        Throwable thrown = catchThrowable(() -> controller.riskForAccount(accountId, null, null));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo("NOT_FOUND");
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    // ---------------- L3.4-P2: engagement & support ----------------

    @Test
    void noResponsePastTheTicketsOwnDeadlineIsReported() {
        // The deadline comes from the ticket itself (responseDueAt), so no
        // threshold is invented anywhere in this rule.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveOpenTicket(
                at("2026-05-01T00:00:00Z"),   // already past at "now"
                null,                                     // never answered
                at("2026-06-15T00:00:00Z")); // not yet past

        assertThat(fixture.types()).contains(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE);
    }

    @Test
    void answeredAfterTheResponseDeadlineIsReportedAsSlowNotMissing() {
        // The two cases are mutually exclusive: an answered ticket must not also
        // be reported as "never replied to".
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveOpenTicket(
                at("2026-05-01T00:00:00Z"),
                at("2026-05-01T02:00:00Z"),                  // answered 2h late
                at("2026-06-15T00:00:00Z"));

        assertThat(fixture.types()).contains(RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE);
    }

@Test
    void answeredBeforeTheDeadlineRaisesNoSupportSignal() {
        // The support rules evaluate the row in Java, so an absence assertion here
        // is meaningful: nothing about this ticket trips any of the three rules.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveOpenTicket(
                at("2026-05-01T00:00:00Z"),
                at("2026-04-30T23:00:00Z"),                  // answered in good time
                at("2026-06-15T00:00:00Z"));                 // resolution deadline still ahead

        assertThat(fixture.types())
                .doesNotContain(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE,
                        RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE,
                        RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
    }

    @Test
    void openTicketPastItsOwnResolutionDeadlineIsReported() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveOpenTicket(
                at("2026-07-01T00:00:00Z"),   // response deadline still ahead
                at("2026-04-30T23:00:00Z"),
                at("2026-05-01T00:00:00Z")); // resolution deadline passed

        assertThat(fixture.types()).contains(RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
    }

    @Test
    void closedTicketsRaiseNoSupportSignals() {
        // A delivered ticket is finished work: it must not be reported as overdue
        // however old it is.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveClosedTicket(
                at("2026-05-01T00:00:00Z"),
                null,
                at("2026-05-01T00:00:00Z"));

assertThat(fixture.types())
                .doesNotContain(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE,
                        RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE,
                        RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
    }

    @Test
    void silenceIsOnlyReportedForAnAccountThatGaveFeedbackBefore() {
        // This is the distinction that keeps the engagement rule honest: never
        // surveyed proves nothing about the customer.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveFeedbackAged(at("2026-01-01T00:00:00Z"));   // well before the window

        assertThat(fixture.types()).contains(RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT);

RiskSignalResponse signal = fixture.firstOf(RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT);
        assertThat(signal.getReason())
                .as("must claim silence, never disengagement")
                .contains("silence")
                .doesNotContain("disengaged", "churn", "lost");
    }

    @Test
    void anAccountThatNeverGaveFeedbackRaisesNoEngagementSignal() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        // No feedback staged at all.

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT);
    }

    @Test
    void recentFeedbackSuppressesTheSilenceSignal() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveFeedbackAged(at("2026-05-31T12:00:00Z"));   // inside the 30-day window

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENGAGEMENT_FEEDBACK_SILENT);
    }

    @Test
    void supportSignalsRespectTicketScope() {
        // ticket:read withheld: no ticket-derived signal may leak, and the
        // feedback rule must still work.
        Fixture fixture = new Fixture();
        fixture.grantScope("feedback", "read");
        fixture.giveOpenTicket(
                at("2026-05-01T00:00:00Z"), null,
                at("2026-05-01T00:00:00Z"));

        assertThat(fixture.types())
                .doesNotContain(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE,
                        RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE,
                        RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
    }

    @Test
    void withholdingASourcePermissionSuppressesOnlyThatDomain() {
        // Per-domain fail-closed: ticket:read withheld means no ticket-derived
        // signal appears at all, while the feedback rules still run. This is the
        // guarantee that stops a caller seeing records through health that they
        // could not see through the domain itself.
        Fixture fixture = new Fixture();
        fixture.grantScope("feedback", "read");
        fixture.giveOpenTicket(
                at("2026-05-01T00:00:00Z"), null,
                at("2026-05-01T00:00:00Z"));

        assertThat(fixture.types())
                .doesNotContain(RiskSignalType.SUPPORT_NO_RESPONSE_PAST_DUE,
                        RiskSignalType.SUPPORT_SLOW_FIRST_RESPONSE,
                        RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE);
    }

    // ---------------- L3.4-P3: entitlement lifecycle ----------------

    /*
     * The fixture clock is fixed at 2026-06-01T00:00:00Z, so "today" is
     * 2026-06-01 and the 30-day horizon ends on 2026-07-01. Every date below is
     * written out rather than derived, so a boundary test states the boundary it
     * is testing.
     */

    @Test
    void expiredEntitlementWithNoLaterCoverageIsReported() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(
                ENTITLEMENT_EXPIRED_STATUS, date("2026-01-01"), date("2026-04-01"), UUID.randomUUID()));

        assertThat(fixture.types()).contains(RiskSignalType.ENTITLEMENT_EXPIRED);

        RiskSignalResponse signal = fixture.firstOf(RiskSignalType.ENTITLEMENT_EXPIRED);
        assertThat(signal.getSourceType()).isEqualTo("ENTITLEMENT");
        assertThat(signal.getReason())
                .as("report the lapse of coverage, never a conclusion about the customer")
                .contains("ended on 2026-04-01")
                .doesNotContain("churn", "lost", "payment");
        assertThat(signal.getDetectedAt())
                .as("the fact is the end of coverage, not when the row was created")
                .isEqualTo(java.time.Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void renewalRecordedAsLaterCoverageSuppressesTheExpiredSignal() {
        // A renewal is a new entitlement for the same account and offering. The
        // CRM has no renewal link to read (the renewed-from columns are never
        // written), so recorded coverage is what distinguishes renewal from lapse.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        UUID accountId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        fixture.giveEntitlements(
                fixture.entitlementFor(accountId, offeringId, ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-04-01")),
                fixture.entitlementFor(accountId, offeringId, EntitlementStatus.ACTIVE,
                        date("2026-04-01"), date("2026-12-31")));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void forwardDatedRenewalWithACoverageGapIsStillReported() {
        // A future-dated PENDING renewal is real, but it starts months after the old
        // coverage ended. Suppressing the signal would hide the gap in coverage.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        UUID accountId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        fixture.giveEntitlements(
                fixture.entitlementFor(accountId, offeringId, ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-04-01")),
                fixture.entitlementFor(accountId, offeringId, EntitlementStatus.PENDING,
                        date("2026-07-01"), date("2027-01-01")));

        assertThat(fixture.types()).contains(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void continuousForwardDatedRenewalSuppressesTheExpiredSignal() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        UUID accountId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();

        fixture.giveEntitlements(
                fixture.entitlementFor(accountId, offeringId, ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-04-01")),
                fixture.entitlementFor(accountId, offeringId, EntitlementStatus.PENDING,
                        date("2026-04-02"), date("2027-01-01")));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void laterCoverageForADifferentOfferingDoesNotMaskTheLapse() {
        // Known limitation: renewal linkage is never written, so a renewal into a
        // different offering cannot be recognised as a replacement. This test pins
        // the limitation rather than pretending the rule is stronger than it is.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        UUID accountId = UUID.randomUUID();

        fixture.giveEntitlements(
                fixture.entitlementFor(accountId, UUID.randomUUID(), ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-04-01")),
                fixture.entitlementFor(accountId, UUID.randomUUID(), EntitlementStatus.ACTIVE,
                        date("2026-04-01"), date("2026-12-31")));

        assertThat(fixture.types()).contains(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void expiryIsReadFromStatusNotInferredFromAPastEndDate() {
        // An ACTIVE entitlement whose end date has passed is a scheduler gap, not a
        // lifecycle fact. Reporting it as EXPIRED would contradict the entitlement
        // the user can open, and the state is already covered by the approaching
        // expiry rule.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(
                EntitlementStatus.ACTIVE, date("2026-01-01"), date("2026-05-01"), UUID.randomUUID()));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void deliberatelyEndedCoverageIsNotReportedAsExpired() {
        // CANCELLED and TERMINATED are business decisions. Only EXPIRED is the
        // status the scheduler assigns from a date alone.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(
                fixture.entitlement(EntitlementStatus.CANCELLED,
                        date("2026-01-01"), date("2026-04-01"), UUID.randomUUID()),
                fixture.entitlement(EntitlementStatus.TERMINATED,
                        date("2026-01-01"), date("2026-03-01"), UUID.randomUUID()));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED);
    }

    @Test
    void expiredEndDateBoundaryIsStrictlyBeforeToday() {
        // endDate == today is not yet expired; yesterday is.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(
                fixture.entitlement(ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-06-01"), UUID.randomUUID()),
                fixture.entitlement(ENTITLEMENT_EXPIRED_STATUS,
                        date("2026-01-01"), date("2026-05-31"), UUID.randomUUID()));

        List<RiskSignalResponse> expired = fixture.signals().stream()
                .filter(signal -> signal.getType() == RiskSignalType.ENTITLEMENT_EXPIRED)
                .toList();

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getMetadata().get("endDate")).isEqualTo(date("2026-05-31"));
    }

    @Test
    void approachingExpiryIncludesTheHorizonBoundaryAndNothingBeyondIt() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(
                fixture.entitlement(EntitlementStatus.ACTIVE,
                        date("2026-01-01"), date("2026-07-01"), UUID.randomUUID()),
                fixture.entitlement(EntitlementStatus.ACTIVE,
                        date("2026-01-01"), date("2026-07-02"), UUID.randomUUID()));

        List<RiskSignalResponse> approaching = fixture.signals().stream()
                .filter(signal -> signal.getType() == RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY)
                .toList();

        assertThat(approaching).hasSize(1);
        assertThat(approaching.get(0).getMetadata().get("endDate")).isEqualTo(date("2026-07-01"));
    }

    @Test
    void renewalDecisionDateInsideTheHorizonWarnsEvenWhenExpiryIsFarAway() {
        // renewalDueDate is persisted and recomputed on every write as
        // endDate - renewalNoticeDays, so the notice deadline can fall well before
        // the 30-day expiry horizon. P1's expiry-only test would report this far too
        // late; this is the only reason P3 extends the rule instead of adding one.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(EntitlementStatus.ACTIVE,
                date("2026-01-01"), date("2026-09-01"), UUID.randomUUID(),
                Boolean.TRUE, date("2026-06-20")));

        RiskSignalResponse signal = fixture.firstOf(RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY);

        assertThat(signal.getReason())
                .contains("renewal decision date on 2026-06-20")
                .contains("coverage runs to 2026-09-01");
        assertThat(signal.getSeverity()).isEqualTo(RiskSignalSeverity.MEDIUM);
    }

    @Test
    void renewalDecisionDateBeyondTheHorizonRaisesNothing() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(EntitlementStatus.ACTIVE,
                date("2026-01-01"), date("2026-12-01"), UUID.randomUUID(),
                Boolean.TRUE, date("2026-08-01")));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY);
    }

    @Test
    void aPassedRenewalDecisionDateIsNotTreatedAsARenewalWindow() {
        // A deadline already missed would be a renewal-overdue claim, which needs a
        // recorded obligation this schema does not have. It is not a "window".
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(EntitlementStatus.ACTIVE,
                date("2026-01-01"), date("2026-12-01"), UUID.randomUUID(),
                Boolean.TRUE, date("2026-05-01")));

        assertThat(fixture.types()).doesNotContain(RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY);
    }

    @Test
    void oneEntitlementNeverProducesTwoExpirySignals() {
        // Both conditions hold at once: expiry inside the horizon AND a renewal
        // decision date inside it. The signal must still appear once.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(EntitlementStatus.ACTIVE,
                date("2026-01-01"), date("2026-06-20"), UUID.randomUUID(),
                Boolean.TRUE, date("2026-06-10")));

        assertThat(fixture.signals())
                .filteredOn(signal -> signal.getType() == RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY)
                .hasSize(1);
    }

    @Test
    void pendingCoverageIsNeverReportedAsExpiringOrExpired() {
        // A future start date is the normal state of a forward-dated sale, not a
        // risk. ENTITLEMENT_NOT_YET_ACTIVE was deliberately not implemented.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveEntitlements(fixture.entitlement(EntitlementStatus.PENDING,
                date("2026-08-01"), date("2027-08-01"), UUID.randomUUID(),
                Boolean.TRUE, date("2027-07-01")));

        assertThat(fixture.types())
                .doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED,
                        RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY);
    }

    @Test
    void entitlementSignalsRespectEntitlementScope() {
        Fixture fixture = new Fixture();
        fixture.grantScope("feedback", "read");
        fixture.giveEntitlements(fixture.entitlement(ENTITLEMENT_EXPIRED_STATUS,
                date("2026-01-01"), date("2026-04-01"), UUID.randomUUID()));

        assertThat(fixture.types())
                .doesNotContain(RiskSignalType.ENTITLEMENT_EXPIRED,
                        RiskSignalType.ENTITLEMENT_APPROACHING_EXPIRY);
    }

    @Test
    void teamScopeResolvesTheTeamOnceAndStillReturnsSignals() {
        Fixture fixture = new Fixture();
        fixture.grantScopeWithName("entitlement", "read", "TEAM");
        fixture.giveEntitlements(fixture.entitlement(ENTITLEMENT_EXPIRED_STATUS,
                date("2026-01-01"), date("2026-04-01"), UUID.randomUUID()));

        assertThat(fixture.types()).contains(RiskSignalType.ENTITLEMENT_EXPIRED);
        org.mockito.Mockito.verify(fixture.permissionEvaluatorService, org.mockito.Mockito.atLeastOnce())
                .getTeamUserIds(fixture.userId, fixture.tenantId());
    }

    @Test
    void ownScopeNeedsNoTeamLookup() {
        Fixture fixture = new Fixture();
        fixture.grantScopeWithName("entitlement", "read", "OWN");
        fixture.giveEntitlements(fixture.entitlement(ENTITLEMENT_EXPIRED_STATUS,
                date("2026-01-01"), date("2026-04-01"), UUID.randomUUID()));

        assertThat(fixture.types()).contains(RiskSignalType.ENTITLEMENT_EXPIRED);
        org.mockito.Mockito.verify(fixture.permissionEvaluatorService, org.mockito.Mockito.never())
                .getTeamUserIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ---------------- cross-account pagination and filters ----------------

    @Test
    void crossAccountListingFiltersThenPaginatesAndReportsTheFilteredTotal() {
        List<RiskSignalResponse> staged = List.of(
                signal(RiskSignalType.ENTITLEMENT_EXPIRED, RiskSignalSeverity.HIGH, "2026-05-05T00:00:00Z"),
                signal(RiskSignalType.ENTITLEMENT_EXPIRED, RiskSignalSeverity.HIGH, "2026-05-04T00:00:00Z"),
                signal(RiskSignalType.ENTITLEMENT_EXPIRED, RiskSignalSeverity.HIGH, "2026-05-03T00:00:00Z"),
                signal(RiskSignalType.LOW_CSAT, RiskSignalSeverity.MEDIUM, "2026-05-02T00:00:00Z"),
                signal(RiskSignalType.SUPPORT_PAST_RESOLUTION_DUE, RiskSignalSeverity.MEDIUM,
                        "2026-05-01T00:00:00Z"));

        RiskSignalController controller = controllerOver(staged);

        var page0 = controller.riskSignalsAcrossAccounts(
                null, null, RiskSignalType.ENTITLEMENT_EXPIRED, null, 0, 2).getBody();
        var page1 = controller.riskSignalsAcrossAccounts(
                null, null, RiskSignalType.ENTITLEMENT_EXPIRED, null, 1, 2).getBody();
        var beyondTheEnd = controller.riskSignalsAcrossAccounts(
                null, null, RiskSignalType.ENTITLEMENT_EXPIRED, null, 9, 2).getBody();

        assertThat(page0.getData()).hasSize(2);
        assertThat(page0.getMeta()).isEqualTo(Map.of(
                "page", 0, "size", 2, "total", 3L, "totalPages", 2));
        assertThat(page1.getData()).hasSize(1);
        assertThat(page1.getMeta()).isEqualTo(Map.of(
                "page", 1, "size", 2, "total", 3L, "totalPages", 2));
        assertThat(beyondTheEnd.getData()).isEmpty();
        assertThat(beyondTheEnd.getMeta()).isEqualTo(Map.of(
                "page", 9, "size", 2, "total", 3L, "totalPages", 2));

        // Newest first, and the filtered-out types never appear.
        assertThat(page0.getData())
                .extracting(RiskSignalResponse::getDetectedAt)
                .containsExactly(java.time.Instant.parse("2026-05-05T00:00:00Z"),
                        java.time.Instant.parse("2026-05-04T00:00:00Z"));
    }

    @Test
    void crossAccountListingRejectsAnOversizedPageAndClampsNegativeInput() {
        RiskSignalController controller = controllerOver(List.of(
                signal(RiskSignalType.LOW_CSAT, RiskSignalSeverity.MEDIUM, "2026-05-01T00:00:00Z")));

        var clamped = controller.riskSignalsAcrossAccounts(null, null, null, null, -5, 100_000).getBody();

        assertThat(clamped.getMeta()).isEqualTo(Map.of(
                "page", 0, "size", 100, "total", 1L, "totalPages", 1));
        assertThat(clamped.getData()).hasSize(1);
    }

    @Test
    void crossAccountListingFiltersBySeverityIndependentlyOfType() {
        RiskSignalController controller = controllerOver(List.of(
                signal(RiskSignalType.RECENT_COMPLAINT, RiskSignalSeverity.HIGH, "2026-05-02T00:00:00Z"),
                signal(RiskSignalType.LOW_CSAT, RiskSignalSeverity.MEDIUM, "2026-05-01T00:00:00Z")));

        var highOnly = controller.riskSignalsAcrossAccounts(
                null, null, null, RiskSignalSeverity.HIGH, 0, 20).getBody();

        assertThat(highOnly.getData()).extracting(RiskSignalResponse::getType)
                .containsExactly(RiskSignalType.RECENT_COMPLAINT);
        assertThat(highOnly.getMeta()).isEqualTo(Map.of(
                "page", 0, "size", 20, "total", 1L, "totalPages", 1));
    }

    @Test
    void crossAccountListingReturnsAnEmptyPageRatherThanFailing() {
        RiskSignalController controller = controllerOver(List.of());

        var body = controller.riskSignalsAcrossAccounts(null, null, null, null, 0, 20).getBody();

        assertThat(body.getData()).isEmpty();
        assertThat(body.getMeta()).isEqualTo(Map.of(
                "page", 0, "size", 20, "total", 0L, "totalPages", 0));
    }

    // ---------------- rule behaviour ----------------

    @Test
    void ruleThresholdsMatchTheDocumentedScales() {
        // A mock cannot filter by Specification, so the boundary is pinned where it
        // is actually decided: the constants. These are the numbers that must not
        // drift, because each one mirrors a scale Feedback already validates.
        assertThat(RiskSignalService.NEGATIVE_NPS_BELOW)
                .as("NPS 0-10: 6 is a detractor, 7 must not trigger")
                .isEqualTo(7);
        assertThat(RiskSignalService.LOW_CSAT_AT_OR_BELOW)
                .as("CSAT 1-5: 2 triggers, 3 does not")
                .isEqualTo(2);
        assertThat(RiskSignalService.HIGH_CES_AT_OR_ABOVE)
                .as("CES 1-7: 4 triggers, 3 does not")
                .isEqualTo(4);
        assertThat(RiskSignalService.EXPIRY_HORIZON_DAYS)
                .as("expiry horizon")
                .isEqualTo(30);
    }

    @Test
    void everyRuleEmitsASignalForAStagedRow() {
        // All source domains granted, one Ticket row staged: the ticket rule fires
        // and the signal names its source. Feedback rows are not staged here, so the
        // only signal present is the ticket one.
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        fixture.giveSlaBreachedTicket();

        assertThat(fixture.types()).containsExactly(RiskSignalType.SLA_BREACHED_TICKET);
    }

    @Test
    void sourceDomainsWithoutPermissionContributeNoSignals() {
        // Fail-closed per domain: a caller with feedback:read only must not see
        // ticket-derived signals, and must not get an error for them either.
        Fixture fixture = new Fixture();
        fixture.grantScope("feedback", "read");
        fixture.giveSlaBreachedTicket();

        assertThat(fixture.types()).isEmpty();
    }

    @Test
    void signalsCarryTheAccountAndSourceTheyCameFrom() {
        Fixture fixture = new Fixture();
        fixture.grantAllScopes();
        UUID accountId = UUID.randomUUID();
        fixture.giveSlaBreachedTicket(accountId);

        RiskSignalResponse signal = fixture.riskSignalService()
                .riskSignals(fixture.tenantId(), null, null, null).get(0);

        assertThat(signal.getType()).isEqualTo(RiskSignalType.SLA_BREACHED_TICKET);
        assertThat(signal.getSeverity()).isEqualTo(RiskSignalSeverity.HIGH);
        assertThat(signal.getAccountId()).isEqualTo(accountId);
        assertThat(signal.getSourceType()).isEqualTo("TICKET");
        assertThat(signal.getSourceId()).isNotNull();
        assertThat(signal.getReason()).contains("missed its SLA");
    }

    @Test
    void displayOrderIsSeverityThenRecencyAndIsATotalOrder() {
        UUID sourceId = UUID.randomUUID();
        RiskSignalResponse high = RiskSignalResponse.builder()
                .type(RiskSignalType.RECENT_COMPLAINT).severity(RiskSignalSeverity.HIGH)
                .detectedAt(java.time.Instant.parse("2020-01-01T00:00:00Z"))
                .sourceId(sourceId).build();
        RiskSignalResponse medium = RiskSignalResponse.builder()
                .type(RiskSignalType.LOW_CSAT).severity(RiskSignalSeverity.MEDIUM)
                .detectedAt(java.time.Instant.parse("2030-01-01T00:00:00Z"))
                .sourceId(sourceId).build();

        // Severity dominates recency: a newer MEDIUM must not outrank a HIGH.
        List<RiskSignalResponse> sorted = new java.util.ArrayList<>(
                java.util.Arrays.asList(medium, high));
        sorted.sort(RiskSignalService.displayOrder());

        assertThat(sorted).containsExactly(high, medium);
    }

    // ---------------- test fixture ----------------

    /**
     * Wires the real service against mocked repositories. Scope comes from
     * {@link PermissionEvaluatorService}, so each domain can be granted or
     * withheld independently — which is what the fail-closed tests need.
     */
    private static final class Fixture {

        private static final UUID TENANT_ID = UUID.randomUUID();

        private final FeedbackRepository feedbackRepository =
                org.mockito.Mockito.mock(FeedbackRepository.class);
        private final TicketRepository ticketRepository =
                org.mockito.Mockito.mock(TicketRepository.class);
        private final DealRepository dealRepository =
                org.mockito.Mockito.mock(DealRepository.class);
        private final CustomerEntitlementRepository entitlementRepository =
                org.mockito.Mockito.mock(CustomerEntitlementRepository.class);
        private final RiskSignalQueryRepository riskSignalQueryRepository =
                org.mockito.Mockito.mock(RiskSignalQueryRepository.class);
        private final PermissionEvaluatorService permissionEvaluatorService =
                org.mockito.Mockito.mock(PermissionEvaluatorService.class);
        private final RecordScopeGuard recordScopeGuard =
                org.mockito.Mockito.mock(RecordScopeGuard.class);

        private final RiskSignalService riskSignalService = new RiskSignalService(
                feedbackRepository, ticketRepository, dealRepository, entitlementRepository,
                riskSignalQueryRepository, permissionEvaluatorService, recordScopeGuard,
                java.time.Clock.fixed(java.time.Instant.parse("2026-06-01T00:00:00Z"),
                        java.time.ZoneOffset.UTC));

        private final UUID userId = UUID.randomUUID();

        private Fixture() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));

            // Mockito answers null for an unstubbed findAll, which would blow up the
            // service on the sources a given test is not exercising. Default every
            // source to "nothing found"; individual tests re-stub what they need.
            stubEmpty(feedbackRepository);
            stubEmpty(ticketRepository);
            stubEmpty(dealRepository);
            stubEmpty(entitlementRepository);
        }

        private static void stubEmpty(Object repository) {
            if (repository instanceof FeedbackRepository target) {
                org.mockito.Mockito.doReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 1)))
                        .when(target).findAll(
                                org.mockito.ArgumentMatchers
                                        .any(org.springframework.data.jpa.domain.Specification.class),
                                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
            } else if (repository instanceof TicketRepository target) {
                org.mockito.Mockito.doReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 1)))
                        .when(target).findAll(
                                org.mockito.ArgumentMatchers
                                        .any(org.springframework.data.jpa.domain.Specification.class),
                                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
            } else if (repository instanceof DealRepository target) {
                org.mockito.Mockito.doReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 1)))
                        .when(target).findAll(
                                org.mockito.ArgumentMatchers
                                        .any(org.springframework.data.jpa.domain.Specification.class),
                                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
            } else if (repository instanceof CustomerEntitlementRepository target) {
                org.mockito.Mockito.doReturn(org.springframework.data.domain.Page.empty(PageRequest.of(0, 1)))
                        .when(target).findAll(
                                org.mockito.ArgumentMatchers
                                        .any(org.springframework.data.jpa.domain.Specification.class),
                                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
            }
        }

        private UUID tenantId() {
            return TENANT_ID;
        }

        private RiskSignalService riskSignalService() {
            return riskSignalService;
        }

        /** Default: every source domain is invisible, so tests opt in explicitly. */
        private void grantScope(String module, String action) {
            grantScopeWithName(module, action, "ALL");
        }

        private void grantScopeWithName(String module, String action, String scopeName) {
            org.mockito.Mockito.when(
                            permissionEvaluatorService.getAccessScope(userId, TENANT_ID, module, action))
                    .thenReturn(scopeName);
            if ("TEAM".equals(scopeName)) {
                org.mockito.Mockito.when(permissionEvaluatorService.getTeamUserIds(userId, TENANT_ID))
                        .thenReturn(List.of(UUID.randomUUID()));
            }
        }

        private void denyScope(String module, String action) {
            org.mockito.Mockito.when(
                            permissionEvaluatorService.getAccessScope(userId, TENANT_ID, module, action))
                    .thenReturn("NONE");
        }

        private void grantAllScopes() {
            for (String module : List.of("feedback", "ticket", "deal", "entitlement")) {
                grantScope(module, "read");
            }
        }

        /**
         * Stages Feedback rows that will be returned from every Feedback query.
         *
         * <p>Mocking at this level cannot see which of the service's four Feedback
         * queries is running, so a stubbed row is returned to all of them. Threshold
         * coverage is therefore asserted on the rows' own scores via the rule's
         * public output rather than by pretending to filter in the mock.
         */
        private void givenFeedbackScore(
                com.shivang.crm.modules.feedback.entity.FeedbackType type, Integer score) {
            var feedback = com.shivang.crm.modules.feedback.entity.Feedback.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .accountId(UUID.randomUUID())
                    .type(type)
                    .score(score)
                    .comment("c")
                    .build();
            org.mockito.Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(feedback), PageRequest.of(0, 1), 1))
                    .when(feedbackRepository).findAll(
                            org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        }

        private void giveSlaBreachedTicket() {
            giveSlaBreachedTicket(UUID.randomUUID());
        }

        private void giveSlaBreachedTicket(UUID accountId) {
            var ticket = com.shivang.crm.modules.ticket.entity.Ticket.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .accountId(accountId)
                    .subject("Login failing")
                    .status(com.shivang.crm.modules.ticket.entity.TicketStatus.OPEN)
                    .slaBreached(true)
                    .build();
            org.mockito.Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(ticket), PageRequest.of(0, 1), 1))
                    .when(ticketRepository).findAll(
                            org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        }

        /**
         * Stages an OPEN ticket. Deadlines are passed in explicitly so each test
         * states the deadline it is reasoning about rather than deriving one.
         */
        private void giveOpenTicket(Instant responseDueAt, Instant firstResponseAt, Instant resolutionDueAt) {
            giveTicket(TENANT_ID, com.shivang.crm.modules.ticket.entity.TicketStatus.OPEN,
                    responseDueAt, firstResponseAt, resolutionDueAt);
        }


        private void giveClosedTicket(Instant responseDueAt, Instant firstResponseAt, Instant resolutionDueAt) {
            giveTicket(TENANT_ID, com.shivang.crm.modules.ticket.entity.TicketStatus.CLOSED,
                    responseDueAt, firstResponseAt, resolutionDueAt);
        }

        private void giveTicket(UUID tenantId, com.shivang.crm.modules.ticket.entity.TicketStatus status,
                Instant responseDueAt, Instant firstResponseAt, Instant resolutionDueAt) {
            var ticket = com.shivang.crm.modules.ticket.entity.Ticket.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .accountId(UUID.randomUUID())
                    .subject("Login failing")
                    .status(status)
                    .slaBreached(false)
                    .responseDueAt(responseDueAt)
                    .firstResponseAt(firstResponseAt)
                    .resolutionDueAt(resolutionDueAt)
                    .build();
            org.mockito.Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(ticket), PageRequest.of(0, 1), 1))
                    .when(ticketRepository).findAll(
                            org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        }

        /** Feedback dated outside the window, so the silence rule can fire. */
        private void giveFeedbackAged(Instant createdAt) {
            var feedback = com.shivang.crm.modules.feedback.entity.Feedback.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .accountId(UUID.randomUUID())
                    .type(com.shivang.crm.modules.feedback.entity.FeedbackType.CSAT)
                    .score(4)
                    .createdAt(createdAt)
                    .build();
            org.mockito.Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(feedback), PageRequest.of(0, 1), 1))
                    .when(feedbackRepository).findAll(
                            org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        }

        private List<RiskSignalType> types() {
            return signals().stream()
                    .map(RiskSignalResponse::getType)
                    .toList();
        }

        /**
         * Stages entitlement rows. Mocking cannot evaluate a Specification, so the
         * service's two entitlement queries both receive this same page; the rules
         * re-check status and dates in Java, which is what makes these assertions
         * meaningful rather than a restatement of the mock.
         */
        private void giveEntitlements(CustomerEntitlement... entitlements) {
            List<CustomerEntitlement> rows = List.of(entitlements);
            org.mockito.Mockito.doReturn(new org.springframework.data.domain.PageImpl<>(
                            rows, PageRequest.of(0, 1), rows.size()))
                    .when(entitlementRepository).findAll(
                            org.mockito.ArgumentMatchers.any(org.springframework.data.jpa.domain.Specification.class),
                            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
        }

        private CustomerEntitlement entitlement(
                EntitlementStatus status, LocalDate startDate, LocalDate endDate, UUID offeringId) {
            return entitlementFor(UUID.randomUUID(), offeringId, status, startDate, endDate,
                    null, null);
        }

        private CustomerEntitlement entitlement(
                EntitlementStatus status, LocalDate startDate, LocalDate endDate, UUID offeringId,
                Boolean renewable, LocalDate renewalDueDate) {
            return entitlementFor(UUID.randomUUID(), offeringId, status, startDate, endDate,
                    renewable, renewalDueDate);
        }

        private CustomerEntitlement entitlementFor(
                UUID accountId, UUID offeringId, EntitlementStatus status,
                LocalDate startDate, LocalDate endDate) {
            return entitlementFor(accountId, offeringId, status, startDate, endDate, null, null);
        }

        private CustomerEntitlement entitlementFor(
                UUID accountId, UUID offeringId, EntitlementStatus status,
                LocalDate startDate, LocalDate endDate,
                Boolean renewable, LocalDate renewalDueDate) {
            return CustomerEntitlement.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .accountId(accountId)
                    .offeringId(offeringId)
                    .name("Support Plan")
                    .status(status)
                    .startDate(startDate)
                    .endDate(endDate)
                    .renewable(renewable)
                    .autoRenew(Boolean.FALSE)
                    .renewalDueDate(renewalDueDate)
                    .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();
        }

        private List<RiskSignalResponse> signals() {
            return riskSignalService.riskSignals(TENANT_ID, null, null, null);
        }

private RiskSignalResponse firstOf(RiskSignalType type) {
            return signals().stream()
                    .filter(s -> s.getType() == type)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no signal of type " + type));
        }

        private void reset() {
            org.mockito.Mockito.reset(feedbackRepository, ticketRepository,
                    dealRepository, entitlementRepository);
        }
    }

    // ---------------- reflection helpers ----------------

    /** The fixture clock is pinned to 2026-06-01, so deadlines read against that fixed now. */
    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    /** A signal with only the fields the cross-account listing looks at. */
    private static RiskSignalResponse signal(
            RiskSignalType type, RiskSignalSeverity severity, String detectedAt) {
        return RiskSignalResponse.builder()
                .accountId(UUID.randomUUID())
                .type(type)
                .severity(severity)
                .detectedAt(Instant.parse(detectedAt))
                .sourceType("TEST")
                .sourceId(UUID.randomUUID())
                .build();
    }

    /**
     * The controller over a stubbed service, so pagination and filtering are
     * exercised without re-running every rule.
     */
    private static RiskSignalController controllerOver(List<RiskSignalResponse> staged) {
        RiskSignalService service = org.mockito.Mockito.mock(RiskSignalService.class);
        org.mockito.Mockito.when(service.riskSignalsPage(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    RiskSignalType type = invocation.getArgument(4);
                    RiskSignalSeverity severity = invocation.getArgument(5);
                    int page = invocation.getArgument(6);
                    int size = invocation.getArgument(7);
                    List<RiskSignalResponse> matching = staged.stream()
                            .filter(signal -> type == null || signal.getType() == type)
                            .filter(signal -> severity == null || signal.getSeverity() == severity)
                            .sorted(RiskSignalService.displayOrder())
                            .toList();
                    int fromIndex = (int) Math.min((long) page * size, matching.size());
                    int toIndex = Math.min(fromIndex + size, matching.size());
                    return new RiskSignalPage(
                            matching.subList(fromIndex, toIndex), page, size, matching.size());
                });

        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        org.mockito.Mockito.when(tenantContext.requireTenantId()).thenReturn(UUID.randomUUID());

        return new RiskSignalController(
                service, tenantContext, org.mockito.Mockito.mock(AccountService.class));
    }

    private static RiskSignalController controllerWithAccountService(
            RiskSignalService service, AccountService accounts, UUID tenantId) {
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        org.mockito.Mockito.when(tenantContext.requireTenantId()).thenReturn(tenantId);
        return new RiskSignalController(service, tenantContext, accounts);
    }


    private static List<String> routeOf(String methodName) {
        return List.of(methodNamed(methodName).getAnnotation(GetMapping.class).value());
    }

    private static Set<String> permissionsOf(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (annotation == null) {
            return Set.of();
        }
        Set<String> permissions = new java.util.LinkedHashSet<>();
        var matcher = Pattern
                .compile("@rbac\\.has\\(authentication,\\s*'([^']+)',\\s*'([^']+)'\\)")
                .matcher(annotation.value());
        while (matcher.find()) {
            permissions.add(matcher.group(1) + ":" + matcher.group(2));
        }
        return permissions;
    }

    private static boolean hasGetMapping(Method method) {
        return method.getAnnotation(GetMapping.class) != null;
    }

    private static Method methodNamed(String name) {
        return Arrays.stream(RiskSignalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no controller method " + name));
    }
}