package com.shivang.crm.modules.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.shared.event.ReturnedMessageTracker;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

class DeadLetterRedriveServiceTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final String REQUEST_ID = "req-1";

    /**
     * The single canonical body every test message carries. Asserting the re-drive
     * preserves it byte-for-byte is what makes the consumer-side
     * ON CONFLICT (workflow_version_id, trigger_event_id) dedup effective.
     */
    private static final String DEAD_LETTER_PAYLOAD = """
        {"eventId":"evt-1","entityType":"LEAD","eventType":"CREATED","tenantId":"%s","entityId":"%s"}
        """.formatted(TENANT_A, UUID.randomUUID());

    private static String deadLetterPayload() {
        return DEAD_LETTER_PAYLOAD;
    }

    private DeadLetterQueueInspector inspector;
    private OperatorActionAuditService auditService;
    private OperatorScopeResolver scope;
    private OperatorToolsProperties properties;
    private RabbitTemplate rabbitTemplate;
    private RabbitOperations operations;
    private ReturnedMessageTracker returnedTracker;
    private DeadLetterRedriveService service;

    @BeforeEach
    void setUp() {
        inspector = mock(DeadLetterQueueInspector.class);
        auditService = mock(OperatorActionAuditService.class);
        scope = mock(OperatorScopeResolver.class);
        properties = new OperatorToolsProperties();
        properties.setEnabled(true);
        properties.getDlq().setManagementUrl("http://rabbitmq:15672");
        properties.getDlq().setConfirmTimeoutMs(5_000);

        DeadLetterMessageMapper mapper =
                new DeadLetterMessageMapper(properties, new ConnectorAuditSanitizer());

        rabbitTemplate = mock(RabbitTemplate.class);
        operations = mock(RabbitOperations.class);
        // Run the callback the way the real template would, so the send is observable.
        // The explicit type witness targets the single-argument invoke overload; an
        // untyped any() would also match invoke(callback, acks, returns).
        when(rabbitTemplate.invoke(ArgumentMatchers.<RabbitOperations.OperationsCallback<Object>>any()))
                .thenAnswer(invocation -> {
                    RabbitOperations.OperationsCallback<?> callback =
                            (RabbitOperations.OperationsCallback<?>) invocation.getArgument(0);
                    return callback.doInRabbit(operations);
                });

        returnedTracker = mock(ReturnedMessageTracker.class);
        when(returnedTracker.wasReturned(anyString())).thenReturn(false);

        when(auditService.record(any()))
                .thenReturn(new com.shivang.crm.modules.operations.entity.OperatorActionRecord());

        when(scope.isPlatformScope()).thenReturn(true);
        when(scope.tenantId()).thenReturn(null);
        when(scope.userId()).thenReturn(UUID.randomUUID());
        when(scope.role()).thenReturn("SUPERADMIN");

        when(inspector.scanFor("evt-1")).thenReturn(Optional.of(message("evt-1", TENANT_A)));

        service = new DeadLetterRedriveService(inspector, mapper, auditService, scope, properties,
                rabbitTemplate, returnedTracker);
    }

    // ── valid operator can re-drive; original destination preserved ───────────

    @Test
    void reDriveRepublishesToTheOriginalExchangeAndRoutingKey() {
        DeadLetterRedriveService.RedriveResult result = service.redrive("evt-1", REQUEST_ID);

        assertThat(result.getExchange()).isEqualTo("crm.events");
        assertThat(result.getRoutingKey()).isEqualTo("crm.LEAD.CREATED");
        assertThat(result.getOriginalMessageId()).isEqualTo("evt-1");
        assertThat(result.getTenantId()).isEqualTo(TENANT_A);
        assertThat(result.getReplayedAt()).isNotNull();
    }

    @Test
    void reDriveSendsToTheOriginalDestinationAndRequiresAConfirm() {
        service.redrive("evt-1", REQUEST_ID);

        ArgumentCaptor<String> exchange = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);

        verify(operations).send(exchange.capture(), routingKey.capture(), message.capture());
        verify(operations).waitForConfirmsOrDie(5_000L);

        assertThat(exchange.getValue()).isEqualTo("crm.events");
        assertThat(routingKey.getValue()).isEqualTo("crm.LEAD.CREATED");
    }

    @Test
    void reDrivenCopyKeepsTheBodyByteForByteAndCarriesLineage() {
        service.redrive("evt-1", REQUEST_ID);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(anyString(), anyString(), message.capture());
        Message published = message.getValue();

        assertThat(new String(published.getBody(), StandardCharsets.UTF_8))
                .as("body must be preserved so the canonical eventId - and consumer-side ON CONFLICT dedup - survives")
                .isEqualTo(deadLetterPayload());
        assertThat(published.getMessageProperties().getMessageId())
                .as("the copy gets a fresh AMQP id so a later failure is independently re-drivable")
                .isNotEqualTo("evt-1");
    }

    @Test
    void reDrivenCopyIsTaggedWithItsAncestor() {
        service.redrive("evt-1", REQUEST_ID);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(operations).send(anyString(), anyString(), message.capture());
        assertThat(message.getValue().getMessageProperties().getHeaders())
                .containsEntry("x-crm-redrive-of", "evt-1");
    }

    // ── duplicate re-drive prevented by the unique index ─────────────────────

    @Test
    void duplicateReDriveIsReportedAsConflictAndNeverReportedAsSuccess() {
        when(inspector.scanFor("evt-1")).thenReturn(Optional.empty());
        when(inspector.scanFor("evt-1")).thenReturn(Optional.of(message("evt-1", TENANT_A)));
        // The index rejects the second successful insert for the same target.
        when(auditService.record(any()))
                .thenThrow(new DataIntegrityViolationException("uq_operator_action_success_target"));

        assertThatThrownBy(() -> service.redrive("evt-1", "req-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already re-driven");
    }

    // ── tenant isolation ────────────────────────────────────────────────────

    @Test
    void tenantCallerCannotReDriveAnotherTenantsMessage() {
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(PermissionDeniedException.class);
        verify(operations, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void rejectedReDriveIsStillAuditedAsNotAllowed() {
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(PermissionDeniedException.class);

        ArgumentCaptor<OperatorActionAuditService.Entry> captor =
                ArgumentCaptor.forClass(OperatorActionAuditService.Entry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(OperatorActionResult.NOT_ALLOWED);
        assertThat(captor.getValue().getTargetId()).isEqualTo("evt-1");
    }

    // ── destination allow list ──────────────────────────────────────────────

    @Test
    void destinationOutsideTheApprovedTopologyIsRejected() {
        when(inspector.scanFor("evt-1")).thenReturn(Optional.of(new RabbitManagementApiClient.ManagementMessage(
                DEAD_LETTER_PAYLOAD, "string", false, "other.exchange", "secret.key",
                Map.of("message_id", "evt-1", "headers", Map.of(
                        "x-death", List.of(Map.of("count", 1, "reason", "rejected",
                                "exchange", "other.exchange", "queue", "crm.workflow.events",
                                "routing-keys", List.of("secret.key"))))))));

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("approved re-drive destination");
        verify(operations, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void explicitAllowListOverridesTheDefault() {
        properties.getDlq().setAllowedExchanges(List.of("crm.events", "other.exchange"));
        when(inspector.scanFor("evt-1")).thenReturn(Optional.of(new RabbitManagementApiClient.ManagementMessage(
                DEAD_LETTER_PAYLOAD, "string", false, "other.exchange", "k",
                Map.of("message_id", "evt-1", "headers", Map.of(
                        "x-death", List.of(Map.of("count", 1, "reason", "rejected",
                                "exchange", "other.exchange", "queue", "crm.workflow.events",
                                "routing-keys", List.of("k"))))))));

        assertThat(service.redrive("evt-1", REQUEST_ID).getExchange()).isEqualTo("other.exchange");
    }

    // ── publisher confirmation and returns ──────────────────────────────────

    @Test
    void publishFailureIsReportedAndNothingIsMarkedReDriven() {
        // doThrow, not when(...): re-stubbing with when() would invoke the existing
        // answer first, and that answer dereferences the callback argument.
        org.mockito.Mockito.doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .invoke(ArgumentMatchers.<RabbitOperations.OperationsCallback<Object>>any());

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("could not be confirmed");

        ArgumentCaptor<OperatorActionAuditService.Entry> captor =
                ArgumentCaptor.forClass(OperatorActionAuditService.Entry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(OperatorActionResult.FAILED);
    }

    @Test
    void publisherReturnIsTreatedAsFailureNotSuccess() {
        when(returnedTracker.wasReturned(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("could not be routed");

        ArgumentCaptor<OperatorActionAuditService.Entry> captor =
                ArgumentCaptor.forClass(OperatorActionAuditService.Entry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(OperatorActionResult.FAILED);
    }

    // ── failed re-drive preserves the dead letter ───────────────────────────

    @Test
    void failedReDriveNeverRemovesTheDeadLetter() {
        org.mockito.Mockito.doThrow(new AmqpException("down"))
                .when(rabbitTemplate)
                .invoke(ArgumentMatchers.<RabbitOperations.OperationsCallback<Object>>any());

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(BusinessException.class);

        verify(operations, never()).send(anyString(), anyString(), any(Message.class));
    }

    // ── unknown message / disabled tooling ──────────────────────────────────

    @Test
    void unknownMessageIdIsNotFound() {
        when(inspector.scanFor("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redrive("nope", REQUEST_ID))
                .isInstanceOf(NotFoundException.class);
        verify(operations, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void blankMessageIdIsRejected() {
        assertThatThrownBy(() -> service.redrive("  ", REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("messageId is required");
    }

    @Test
    void disabledToolingRefusesReDrive() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.redrive("evt-1", REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disabled");
        verify(operations, never()).send(anyString(), anyString(), any(Message.class));
    }

    // ── audit shape ─────────────────────────────────────────────────────────

    @Test
    void auditEntryCarriesNoMessageBodyField() {
        service.redrive("evt-1", REQUEST_ID);

        assertThat(Arrays.stream(com.shivang.crm.modules.operations.entity.OperatorActionRecord.class
                        .getDeclaredFields()).map(java.lang.reflect.Field::getName).toList())
                .doesNotContain("payload", "body", "headers", "rawMessage", "credentials", "password", "token");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String payloadFor(String eventId, UUID tenantId) {
        return """
            {"eventId":"%s","entityType":"LEAD","eventType":"CREATED","tenantId":"%s","entityId":"%s"}
            """.formatted(eventId, tenantId, UUID.randomUUID());
    }

    private static RabbitManagementApiClient.ManagementMessage message(String messageId, UUID tenantId) {
        return new RabbitManagementApiClient.ManagementMessage(
                DEAD_LETTER_PAYLOAD, "string", false,
                "crm.workflow.events.dlx", "crm.workflow.events",
                Map.of("message_id", messageId, "content_type", "application/json",
                        "headers", Map.of(
                                "x-death", List.of(Map.of("count", 3, "reason", "rejected",
                                        "exchange", "crm.events", "queue", "crm.workflow.events",
                                        "routing-keys", List.of("crm.LEAD.CREATED"))),
                                "x-first-death-exchange", "crm.events",
                                "x-first-death-routing-key", "crm.LEAD.CREATED")));
    }
}
