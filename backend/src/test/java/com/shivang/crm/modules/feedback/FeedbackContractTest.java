package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import com.shivang.crm.modules.feedback.controller.FeedbackController;
import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;

/**
 * L3.3-P1/P2 contract guard for the Feedback HTTP surface.
 *
 * <p>Pins three facts P1/P2 rely on:
 * <ul>
 *   <li>every endpoint checks a permission that carries no delete/assign/review/export,
 *       and every one is scoped to the feedback module (a copy-paste that left another
 *       module's permission silently widens access);</li>
 *   <li>the submission payload identifies the customer by email, not by an
 *       accountId/contactId/tenantId identity field; expectedAccountId is only an
 *       optional staff-UI consistency guard;</li>
 *   <li>the complaint→ticket conversion additionally requires ticket:write, so Ticket
 *       RBAC is never bypassed by the Feedback path.</li>
 * </ul>
 */
class FeedbackContractTest {

    private static final ObjectMapper MAPPER =
            JsonMapper.builder().findAndAddModules().build();

    @Test
    void everyEndpointRequiresOnlyReadAndWrite() {
        // P2: the conversion endpoint additionally requires ticket:write, so Ticket
        // RBAC is never bypassed through the Feedback path.
        Set<String> referenced = Arrays.stream(FeedbackController.class.getDeclaredMethods())
                .filter(FeedbackContractTest::hasHttpMapping)
                .flatMap(m -> permissionsOf(m).stream())
                .collect(Collectors.toSet());

        assertThat(referenced).containsExactlyInAnyOrder(
                "feedback:read", "feedback:write", "ticket:write");
        assertThat(referenced).noneMatch(p -> p.startsWith("feedback:delete"));
        assertThat(referenced).noneMatch(p -> p.equals("feedback:assign"));
        assertThat(referenced).noneMatch(p -> p.equals("feedback:review"));
        assertThat(referenced).noneMatch(p -> p.equals("feedback:export"));
        assertThat(referenced).noneMatch(p -> p.equals("complaint:create"));
        assertThat(referenced).noneMatch(p -> p.equals("complaint:convert"));
    }

    @Test
    void everyEndpointChecksTheFeedbackModule() {
        for (Method method : FeedbackController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            assertThat(permissionsOf(method))
                    .as("%s must check the feedback module", method.getName())
                    .anyMatch(p -> p.startsWith("feedback:"));
        }
    }

    @Test
    void readRoutesUseReadAndSubmissionUsesWrite() {
        assertThat(permissionsOf(methodNamed("listFeedback"))).containsExactly("feedback:read");
        assertThat(permissionsOf(methodNamed("getFeedback"))).containsExactly("feedback:read");
        assertThat(permissionsOf(methodNamed("submitFeedback"))).containsExactly("feedback:write");
    }

    @Test
    void analyticsRequiresOnlyFeedbackRead() {
        // L3.3-P4: the aggregates are Feedback-derived information, so they are
        // gated by the same permission as the list — not by report:read, which
        // would let a caller without feedback:read see feedback-derived data, and
        // not by any new feedback:analytics permission, which does not exist.
        assertThat(permissionsOf(methodNamed("getAnalytics"))).containsExactly("feedback:read");
    }

    @Test
    void complaintConversionRequiresBothFeedbackAndTicketWrite() {
        // The Ticket is created through TicketService (which owns validation, SLA,
        // events), and this permission pair is what stops a caller with only
        // feedback:write from minting tickets, or a caller with only ticket:write
        // from reaching into another tenant's feedback.
        assertThat(permissionsOf(methodNamed("convertComplaintToTicket")))
                .containsExactlyInAnyOrder("feedback:write", "ticket:write");
    }

    @Test
    void analyticsResponseIsCamelCase() {
        // L3.3-P4: the API boundary is camelCase throughout. A snake_case field
        // here would be invisible until a consumer breaks on it.
        Map<String, Object> response = MAPPER.convertValue(
                com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.builder()
                        .nps(com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.NpsMetrics
                                .builder().responseCount(2).promoterCount(1).passiveCount(0)
                                .detractorCount(1).promoterPercentage(50.0).passivePercentage(0.0)
                                .detractorPercentage(50.0).score(0.0).build())
                        .csat(com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.CsatMetrics
                                .builder().responseCount(1).averageScore(4.0)
                                .score1Count(0).score2Count(0).score3Count(0)
                                .score4Count(1).score5Count(0).build())
                        .ces(com.shivang.crm.modules.feedback.dto.FeedbackAnalyticsResponse.CesMetrics
                                .builder().responseCount(0).averageScore(null)
                                .score1Count(0).score2Count(0).score3Count(0).score4Count(0)
                                .score5Count(0).score6Count(0).score7Count(0).build())
                        .build(),
                Map.class);

        assertThat(namesOf(response)).containsExactlyInAnyOrder("nps", "csat", "ces");
        assertThat(namesOf((Map<?, ?>) response.get("nps")))
                .contains("responseCount", "promoterCount", "passiveCount", "detractorCount",
                        "promoterPercentage", "passivePercentage", "detractorPercentage", "score");
        assertThat(namesOf((Map<?, ?>) response.get("csat")))
                .contains("responseCount", "averageScore",
                        "score1Count", "score2Count", "score3Count", "score4Count", "score5Count");
        assertThat(namesOf((Map<?, ?>) response.get("ces")))
                .contains("responseCount",
                        "score1Count", "score2Count", "score3Count", "score4Count",
                        "score5Count", "score6Count", "score7Count");
        // Built with a null average: NON_NULL omits it rather than sending a
        // zero that a consumer could mistake for a real score.
        assertThat(namesOf((Map<?, ?>) response.get("ces"))).doesNotContain("averageScore");
    }

    private static java.util.List<String> namesOf(Map<?, ?> map) {
        return map.keySet().stream().map(String::valueOf).toList();
    }

    @Test
    void submissionIdentifiesTheCustomerByEmailNotInternalIds() {
        Map<String, Object> submission = MAPPER.convertValue(
                FeedbackCreateRequest.builder()
                        .email("customer@example.com")
                        .type(com.shivang.crm.modules.feedback.entity.FeedbackType.CSAT)
                        .score(4)
                        .comment("Resolved quickly")
                        .source(com.shivang.crm.modules.feedback.entity.FeedbackSource.WEBSITE)
                        .externalReference("resp-1")
                        .expectedAccountId(java.util.UUID.randomUUID())
                        .build(),
                Map.class);

        assertThat(submission.keySet())
                .contains("email", "type", "score", "comment", "source", "externalReference",
                        "expectedAccountId");
        assertThat(submission.keySet())
                .as("external systems must not need internal identity UUIDs")
                .doesNotContain("accountId", "contactId", "tenantId");
    }

    // --- helpers ---

    private static Set<String> permissionsOf(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (annotation == null) {
            return Set.of();
        }
        Set<String> permissions = new java.util.LinkedHashSet<>();
        var matcher = java.util.regex.Pattern
                .compile("@rbac\\.has\\(authentication,\\s*'([^']+)',\\s*'([^']+)'\\)")
                .matcher(annotation.value());
        while (matcher.find()) {
            permissions.add(matcher.group(1) + ":" + matcher.group(2));
        }
        return permissions;
    }

    private static String permissionOf(Method method) {
        Set<String> permissions = permissionsOf(method);
        return permissions.isEmpty() ? null : permissions.iterator().next();
    }

    private static boolean hasHttpMapping(Method method) {
        return method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null;
    }

    private static Method methodNamed(String name) {
        return Arrays.stream(FeedbackController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no controller method " + name));
    }
}
