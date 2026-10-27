package com.shivang.crm.modules.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shivang.crm.modules.feedback.controller.IntegrationFeedbackController;
import com.shivang.crm.modules.feedback.controller.PublicFeedbackController;
import com.shivang.crm.modules.feedback.controller.TicketFeedbackRequestController;
import com.shivang.crm.modules.feedback.entity.FeedbackRequestStatus;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.modules.feedback.entity.FeedbackType;

/**
 * L3.5: the HTTP surface stays in the separate controllers and every staff
 * operation names both the ticket and feedback permissions. The public and
 * integration controllers must not acquire an authenticated permission check.
 */
class FeedbackRequestContractTest {

    @Test
    void staffRoutesAreNestedUnderTheTicket() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(
                TicketFeedbackRequestController.class, RequestMapping.class).value())
                .containsExactly("/api/v1/tickets/{ticketId}/feedback-requests");
    }

    @Test
    void createAndRegenerateNeedTicketAndFeedbackWrite() {
        assertThat(permissionsOf(methodNamed("create")))
                .containsExactlyInAnyOrder("ticket:write", "feedback:write");
        assertThat(permissionsOf(methodNamed("revoke")))
                .containsExactlyInAnyOrder("ticket:write", "feedback:write");
        assertThat(permissionsOf(methodNamed("regenerate")))
                .containsExactlyInAnyOrder("ticket:write", "feedback:write");
    }

    @Test
    void listNeedsTicketAndFeedbackRead() {
        assertThat(permissionsOf(methodNamed("list")))
                .containsExactlyInAnyOrder("ticket:read", "feedback:read");
    }

    @Test
    void everyStaffMethodIsMappedAndPermissionChecked() {
        for (Method method : TicketFeedbackRequestController.class.getDeclaredMethods()) {
            if (!isHttpMapped(method)) {
                continue;
            }
            Set<String> permissions = permissionsOf(method);
            assertThat(permissions)
                    .as("%s must check both modules", method.getName())
                    .anyMatch(p -> p.startsWith("ticket:"));
            assertThat(permissions)
                    .as("%s must check both modules", method.getName())
                    .anyMatch(p -> p.startsWith("feedback:"));
        }
    }

    @Test
    void publicAndIntegrationControllersCarryNoAuthenticatedPermission() {
        for (Class<?> controller : new Class<?>[] {
                PublicFeedbackController.class, IntegrationFeedbackController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class))
                        .as("%s must not rely on a JWT permission", method.getName())
                        .isNull();
            }
        }
        assertThat(AnnotatedElementUtils.findMergedAnnotation(
                PublicFeedbackController.class, RequestMapping.class).value())
                .containsExactly("/api/v1/public/feedback");
        assertThat(AnnotatedElementUtils.findMergedAnnotation(
                IntegrationFeedbackController.class, RequestMapping.class).value())
                .containsExactly("/api/v1/integrations/commercial/feedback");
    }

    @Test
    void thePublicFormCanOnlySubmitScoreAndComment() {
        Set<String> fields = Arrays.stream(
                        com.shivang.crm.modules.feedback.dto.PublicFeedbackSubmitRequest.class
                                .getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("score", "comment");
    }

    @Test
    void requestTypesAreTheThreeScoredTypes() {
        // The enum itself is the source of truth; this pins the public-facing set
        // so a future unscored type cannot silently become requestable.
        assertThat(FeedbackType.NPS.isScored()).isTrue();
        assertThat(FeedbackType.CSAT.isScored()).isTrue();
        assertThat(FeedbackType.CES.isScored()).isTrue();
        assertThat(FeedbackType.GENERAL_FEEDBACK.isScored()).isFalse();
        assertThat(FeedbackType.COMPLAINT.isScored()).isFalse();
        assertThat(FeedbackRequestStatus.values())
                .containsExactlyInAnyOrder(
                        FeedbackRequestStatus.PENDING,
                        FeedbackRequestStatus.SUBMITTED,
                        FeedbackRequestStatus.EXPIRED,
                        FeedbackRequestStatus.REVOKED);
        assertThat(FeedbackSource.values()).contains(FeedbackSource.WEBSITE, FeedbackSource.API);
    }

    private Method methodNamed(String name) {
        return Arrays.stream(TicketFeedbackRequestController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private boolean isHttpMapped(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PostMapping.class);
    }

    private Set<String> permissionsOf(Method method) {
        PreAuthorize annotation =
                AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
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
}
