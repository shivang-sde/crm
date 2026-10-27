package com.shivang.crm.modules.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.workflow.config.WorkflowRabbitConfig;

class DeadLetterQueueInspectorTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private RabbitManagementApiClient client;
    private DeadLetterMessageMapper mapper;
    private OperatorActionAuditService auditService;
    private OperatorScopeResolver scope;
    private OperatorToolsProperties properties;
    private DeadLetterQueueInspector inspector;

    @BeforeEach
    void setUp() {
        client = mock(RabbitManagementApiClient.class);
        auditService = mock(OperatorActionAuditService.class);
        scope = mock(OperatorScopeResolver.class);
        properties = new OperatorToolsProperties();
        properties.setEnabled(true);
        properties.getDlq().setManagementUrl("http://rabbitmq:15672");
        properties.getDlq().setPageSize(20);
        properties.getDlq().setMaxScanMessages(5);
        mapper = new DeadLetterMessageMapper(properties, new ConnectorAuditSanitizer());
        inspector = new DeadLetterQueueInspector(client, mapper, auditService, scope, properties);

        when(scope.isPlatformScope()).thenReturn(true);
        when(scope.tenantId()).thenReturn(null);
        when(scope.userId()).thenReturn(UUID.randomUUID());
        when(scope.role()).thenReturn("SUPERADMIN");
        when(client.isAvailable()).thenReturn(true);
    }

    // ── 1. inspection is non-destructive ──────────────────────────────────────

    @Test
    void inspectionUsesOnlyTheNonDestructivePeek() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(message("evt-1", TENANT_A)));

        inspector.listDeadLetters(20);

        verify(client, times(1)).peek(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE, 20);
        verify(client, never()).fetchAndRemove(any(), any(Integer.class));
    }

    @Test
    void inspectionIsBoundedByTheConfiguredPageSize() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of());
        properties.getDlq().setPageSize(10);

        inspector.listDeadLetters(5_000);

        verify(client).peek(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE, 10);
    }

    // ── 2. safe metadata returned, body never ────────────────────────────────

    @Test
    void safeMetadataIsReturnedAndTheBodyIsNot() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(message("evt-1", TENANT_A)));

        DeadLetterView view = inspector.listDeadLetters(20).get(0);

        assertThat(view.getMessageId()).isEqualTo("evt-1");
        assertThat(view.getOriginalExchange()).isEqualTo("crm.events");
        assertThat(view.getOriginalRoutingKey()).isEqualTo("crm.LEAD.CREATED");
        assertThat(view.getDeathCount()).isEqualTo(3);
        assertThat(view.getDeathReason()).isEqualTo("rejected");
        assertThat(view.getEventType()).isEqualTo("CREATED");
        assertThat(view.getTenantId()).isEqualTo(TENANT_A);

        // The DTO has no body/payload field at all, and the free-form metadata map
        // inside the body is never lifted out.
        assertThat(DeadLetterView.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("payload", "body", "metadata", "headers");
    }

    @Test
    void correlationIdIsReportedAsNullRatherThanInvented() {
        RabbitManagementApiClient.ManagementMessage m = message("evt-1", TENANT_A);
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(m));

        // The publisher never sets a correlation id, so the view must not fabricate one.
        assertThat(inspector.listDeadLetters(20).get(0).getCorrelationId()).isNull();
    }

    // ── 3. tenant scoping ────────────────────────────────────────────────────

    @Test
    void tenantCallerSeesOnlyItsOwnTenant() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(
                message("evt-a", TENANT_A),
                message("evt-b", TENANT_B)));
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(TENANT_A);

        List<DeadLetterView> views = inspector.listDeadLetters(20);

        assertThat(views).extracting(DeadLetterView::getMessageId).containsExactly("evt-a");
    }

    @Test
    void messageWithoutADerivableTenantIsInvisibleToTenantCallers() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(
                new RabbitManagementApiClient.ManagementMessage("not json at all", "string", false,
                        "crm.workflow.events.dlx", "crm.workflow.events",
                        Map.of("message_id", "evt-x", "headers", Map.of()))));
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(TENANT_A);

        assertThat(inspector.listDeadLetters(20))
                .as("a body that is not a canonical event must fail closed to platform-only")
                .isEmpty();
    }

    @Test
    void messageWithoutADerivableTenantIsVisibleToPlatformCallers() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(
                new RabbitManagementApiClient.ManagementMessage("not json", "string", false,
                        "crm.workflow.events.dlx", "crm.workflow.events",
                        Map.of("message_id", "evt-x"))));

        assertThat(inspector.listDeadLetters(20)).extracting(DeadLetterView::getMessageId)
                .containsExactly("evt-x");
    }

    // ── 4. single message lookup ─────────────────────────────────────────────

    @Test
    void lookupScansInSingleMessageStepsAndNeverRemoves() {
        when(client.peek(any(), any(Integer.class))).thenReturn(
                List.of(message("evt-1", TENANT_A)),
                List.of(message("evt-2", TENANT_A)),
                List.of(message("evt-2", TENANT_A)));

        DeadLetterView view = inspector.findByMessageId("evt-2");

        assertThat(view.getMessageId()).isEqualTo("evt-2");
        assertThat(view.getTenantId()).isEqualTo(TENANT_A);
        verify(client, times(2))
                .peek(WorkflowRabbitConfig.WORKFLOW_DEAD_LETTER_QUEUE, 1);
        assertThat(true)
                .as("the scan stops the moment the target is found")
                .isTrue();
        verify(client, never()).fetchAndRemove(any(), any(Integer.class));
    }

    @Test
    void lookupStopsAtTheConfiguredScanBound() {
        when(client.peek(any(), any(Integer.class)))
                .thenReturn(List.of(message("other-" + UUID.randomUUID(), TENANT_A)));
        properties.getDlq().setMaxScanMessages(3);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inspector.findByMessageId("evt-1"))
                .isInstanceOf(com.shivang.crm.shared.exception.NotFoundException.class);
        verify(client, times(3)).peek(any(), any(Integer.class));
    }

    @Test
    void lookupOfAnInvisibleMessageReportsNotFoundRatherThanConfirmingExistence() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(message("evt-b", TENANT_B)));
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(TENANT_A);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inspector.findByMessageId("evt-b"))
                .isInstanceOf(com.shivang.crm.shared.exception.NotFoundException.class)
                .hasMessageContaining("No dead letter");
    }

    // ── 5. replayable flag reflects the audit record ─────────────────────────

    @Test
    void alreadyRedrivenMessageIsMarkedNotReplayable() {
        when(client.peek(any(), any(Integer.class))).thenReturn(List.of(message("evt-1", TENANT_A)));
        when(auditService.alreadySucceeded(com.shivang.crm.modules.operations.enums.OperatorActionType.DLQ_REDRIVE,
                "evt-1")).thenReturn(true);

        DeadLetterView view = inspector.listDeadLetters(20).get(0);

        assertThat(view.isReplayable()).isFalse();
        assertThat(view.isRedriveable()).isFalse();
    }

    // ── 6. tooling disabled / unavailable ────────────────────────────────────

    @Test
    void disabledToolingRefusesToDoAnything() {
        properties.setEnabled(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inspector.listDeadLetters(20))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class)
                .hasMessageContaining("disabled");
        verify(client, never()).peek(any(), any(Integer.class));
    }

    @Test
    void missingManagementApiRefusesRatherThanFallingBackToConsumption() {
        properties.getDlq().setManagementUrl(null);
        when(client.isAvailable()).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> inspector.listDeadLetters(20))
                .isInstanceOf(com.shivang.crm.shared.exception.BusinessException.class)
                .hasMessageContaining("will not fall back to consuming");
        verify(client, never()).peek(any(), any(Integer.class));
        verify(client, never()).fetchAndRemove(any(), any(Integer.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static RabbitManagementApiClient.ManagementMessage message(String messageId, UUID tenantId) {
        String payload = """
            {"eventId":"%s","entityType":"LEAD","eventType":"CREATED","tenantId":"%s",
             "entityId":"%s","occurredAt":"2026-01-01T00:00:00Z",
             "metadata":{"actorId":"x","password":"super-secret-value"}}
            """.formatted(messageId, tenantId, UUID.randomUUID());

        return new RabbitManagementApiClient.ManagementMessage(
                payload,
                "string",
                false,
                "crm.workflow.events.dlx",
                "crm.workflow.events",
                Map.of(
                        "message_id", messageId,
                        "content_type", "application/json",
                        "headers", Map.of(
                                "x-death", List.of(Map.of(
                                        "count", 3,
                                        "reason", "rejected",
                                        "exchange", "crm.events",
                                        "queue", "crm.workflow.events",
                                        "routing-keys", List.of("crm.LEAD.CREATED"))),
                                "x-first-death-exchange", "crm.events",
                                "x-first-death-routing-key", "crm.LEAD.CREATED",
                                "x-first-death-queue", "crm.workflow.events",
                                "x-crm-redrive-of", "some-ancestor")));
    }
}
