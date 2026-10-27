package com.shivang.crm.modules.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.operations.config.OperatorToolsProperties;
import com.shivang.crm.modules.operations.controller.OperatorMessageController;
import com.shivang.crm.modules.operations.enums.OperatorActionResult;
import com.shivang.crm.modules.operations.enums.OperatorActionType;
import com.shivang.crm.modules.operations.repository.OperatorActionRecordRepository;
import com.shivang.crm.shared.event.OutboxEvent;
import com.shivang.crm.shared.event.OutboxEventClaimService;
import com.shivang.crm.shared.event.OutboxEventRepository;
import com.shivang.crm.shared.event.OutboxEventStatus;
import com.shivang.crm.shared.event.ReturnedMessageTracker;
import com.shivang.crm.shared.resilience.InfrastructureMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Guarantees the two non-negotiable properties of this subsystem:
 * <ul>
 *   <li>no automatic replay and no automatic resurrection, and</li>
 *   <li>every operator action is authorized and audited.</li>
 * </ul>
 *
 * <p>These are behaviour-level assertions built on mocks rather than source-text
 * assertions, so they keep their meaning when the implementation is refactored.
 */
class OperatorMessageRecoverySafetyTest {

    private static final String READ = "@rbac.has(authentication, 'admin', 'operations_read')";
    private static final String WRITE = "@rbac.has(authentication, 'admin', 'operations_write')";

    private static final List<Class<?>> OPERATOR_COMPONENTS = List.of(
            DeadLetterQueueInspector.class, DeadLetterRedriveService.class,
            OutboxDeadEventService.class, OperatorActionAuditService.class,
            RabbitManagementApiClient.class, DeadLetterMessageMapper.class,
            OperatorScopeResolver.class, OperatorMessageController.class);

    // ── no automatic replay / no automatic resurrection ─────────────────────

    @Test
    void noOperatorComponentListensToAnyQueue() {
        for (Class<?> type : OPERATOR_COMPONENTS) {
            for (Method method : declaredMethods(type)) {
                assertThat(method.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class))
                        .as("%s#%s must not be a RabbitMQ listener", type.getSimpleName(), method.getName())
                        .isNull();
            }
        }
    }

    @Test
    void noOperatorComponentIsScheduled() {
        for (Class<?> type : OPERATOR_COMPONENTS) {
            for (Method method : declaredMethods(type)) {
                assertThat(method.getAnnotation(Scheduled.class))
                        .as("%s#%s must not be scheduled - there is no automatic recovery loop",
                                type.getSimpleName(), method.getName())
                        .isNull();
            }
        }
    }

    @Test
    void inspectionNeverUsesTheDestructiveAcknowledgementMode() {
        RabbitManagementApiClient client = mock(RabbitManagementApiClient.class);
        when(client.peek(anyString(), anyInt())).thenReturn(List.of());
        when(client.isAvailable()).thenReturn(true);

        DeadLetterQueueInspector inspector = new DeadLetterQueueInspector(
                client, new DeadLetterMessageMapper(props(), new ConnectorAuditSanitizer()),
                mock(OperatorActionAuditService.class), mock(OperatorScopeResolver.class), props());

        inspector.listDeadLetters(10);

        verify(client, never()).fetchAndRemove(anyString(), anyInt());
    }

    @Test
    void reDriveNeverConsumesTheDeadLetterQueue() {
        RabbitManagementApiClient client = mock(RabbitManagementApiClient.class);
        when(client.isAvailable()).thenReturn(true);
        when(client.peek(anyString(), anyInt())).thenReturn(List.of(dlqMessage()));

        DeadLetterQueueInspector inspector = new DeadLetterQueueInspector(
                client, new DeadLetterMessageMapper(props(), new ConnectorAuditSanitizer()),
                mock(OperatorActionAuditService.class), mock(OperatorScopeResolver.class), props());

        OperatorScopeResolver scope = mock(OperatorScopeResolver.class);
        when(scope.isPlatformScope()).thenReturn(true);
        when(scope.userId()).thenReturn(UUID.randomUUID());

        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitOperations operations = mock(RabbitOperations.class);
        when(rabbitTemplate.invoke(any())).thenAnswer(inv -> {
            ((RabbitOperations.OperationsCallback<?>) inv.getArgument(0)).doInRabbit(operations);
            return null;
        });

        new DeadLetterRedriveService(inspector,
                new DeadLetterMessageMapper(props(), new ConnectorAuditSanitizer()),
                mock(OperatorActionAuditService.class), scope, props(),
                rabbitTemplate, mock(ReturnedMessageTracker.class))
                .redrive("evt-1", "req-1");

        verify(client, never()).fetchAndRemove(anyString(), anyInt());
    }

    @Test
    void outboxRequeueDelegatesToTheExistingGuardedTransitionAndPreservesHistory() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventClaimService claimService = mock(OutboxEventClaimService.class);
        OperatorScopeResolver scope = mock(OperatorScopeResolver.class);
        when(scope.isPlatformScope()).thenReturn(false);
        when(scope.tenantId()).thenReturn(UUID.randomUUID());
        when(scope.canAccess(any())).thenReturn(true);
        when(scope.userId()).thenReturn(UUID.randomUUID());

        UUID eventId = UUID.randomUUID();
        OutboxEvent dead = OutboxEvent.builder()
                .id(UUID.randomUUID()).eventId(eventId).tenantId(scope.tenantId())
                .aggregateType("LEAD").eventType("CREATED")
                .payload(Map.of("eventId", eventId.toString()))
                .status(OutboxEventStatus.DEAD)
                .attempts(10)
                .lastError("broker down")
                .deadAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(repository.findByIdAndStatusAndDeletedFalse(eventId, OutboxEventStatus.DEAD))
                .thenReturn(Optional.of(dead));
        when(claimService.requeueDead(eventId)).thenReturn(true);
        when(repository.findById(eventId)).thenReturn(Optional.of(dead));

        new OutboxDeadEventService(repository, claimService, mock(OperatorActionAuditService.class),
                scope, props()).requeue(eventId, "req-1");

        verify(claimService).requeueDead(eventId);
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any());
        assertThat(dead.getAttempts()).isEqualTo(10);
        assertThat(dead.getLastError()).isEqualTo("broker down");
        assertThat(dead.getDeadAt()).isNotNull();
    }

    @Test
    void theOnlyOutboxWriterRemainsTheExistingClaimService() {
        // The requeue path must not gain its own status update. Assert the transition
        // still lives behind the existing conditional, PROCESSING/DEAD-guarded method.
        assertThat(hasMethod(OutboxEventClaimService.class, "requeueDead", UUID.class)).isTrue();
        assertThat(hasMethod(OutboxEventRepository.class, "requeueDeadEvent", UUID.class,
                Instant.class, Instant.class)).isTrue();
    }

    // ── authorization ───────────────────────────────────────────────────────

    @Test
    void everyEndpointRequiresTheOperatorPermission() {
        assertExpression("listDeadLetters", READ, Integer.class);
        assertExpression("getDeadLetter", READ, String.class);
        assertExpression("redrive", WRITE, String.class, String.class, jakarta.servlet.http.HttpServletRequest.class);
        assertExpression("listDeadOutbox", READ, int.class, int.class);
        assertExpression("getDeadOutbox", READ, UUID.class);
        assertExpression("requeue", WRITE, UUID.class, String.class, jakarta.servlet.http.HttpServletRequest.class);
        assertExpression("actions", READ, String.class, int.class, int.class);
    }

    @Test
    void inspectionAndRecoveryUseDifferentPermissions() throws Exception {
        assertThat(OperatorMessageController.class.getDeclaredMethod("listDeadLetters", Integer.class)
                .getAnnotation(PreAuthorize.class).value())
                .as("read access must not imply re-drive")
                .isNotEqualTo(WRITE);
    }

    @Test
    void theOperatorPermissionIsNotHandedOutByADayToDayCrmPermission() {
        for (Method method : declaredMethods(OperatorMessageController.class)) {
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            if (annotation == null) {
                continue;
            }
            assertThat(annotation.value())
                    .as("%s must not be granted by workflow:* or another ordinary module",
                            method.getName())
                    .doesNotContain("'workflow'")
                    .doesNotContain("'lead'")
                    .doesNotContain("'deal'")
                    .contains("'admin'");
        }
    }

    @Test
    void theControllerIsMountedUnderTheOperatorToolsPath() {
        RequestMapping mapping = OperatorMessageController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/operations");
    }

    // ── audit ───────────────────────────────────────────────────────────────

    @Test
    void bothActionTypesAreAuditable() {
        assertThat(OperatorActionType.values()).containsExactly(
                OperatorActionType.DLQ_REDRIVE, OperatorActionType.OUTBOX_REQUEUE);
    }

    @Test
    void auditRecordHasNoColumnForAPayloadOrSecret() {
        assertThat(Arrays.stream(com.shivang.crm.modules.operations.entity.OperatorActionRecord.class
                        .getDeclaredFields()).map(java.lang.reflect.Field::getName).toList())
                .doesNotContain("payload", "body", "headers", "messageBody", "rawMessage",
                        "credentials", "password", "token", "secret");
    }

    @Test
    void auditFailureReasonIsCapped() {
        OperatorActionRecordRepository repository = mock(OperatorActionRecordRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        OperatorActionAuditService service = new OperatorActionAuditService(
                repository, new InfrastructureMetrics(new SimpleMeterRegistry()));

        var record = service.record(OperatorActionAuditService.Entry.builder()
                .actionType(OperatorActionType.OUTBOX_REQUEUE)
                .targetId("evt")
                .operatorUserId(UUID.randomUUID())
                .result(OperatorActionResult.FAILED)
                .failureReason("x".repeat(5_000))
                .build());

        assertThat(record.getFailureReason()).hasSize(500);
    }

    @Test
    void auditTargetIdIsCapped() {
        OperatorActionRecordRepository repository = mock(OperatorActionRecordRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        OperatorActionAuditService service = new OperatorActionAuditService(
                repository, new InfrastructureMetrics(new SimpleMeterRegistry()));

        var record = service.record(OperatorActionAuditService.Entry.builder()
                .actionType(OperatorActionType.DLQ_REDRIVE)
                .targetId("m".repeat(1_000))
                .operatorUserId(UUID.randomUUID())
                .result(OperatorActionResult.SUCCESS)
                .build());

        assertThat(record.getTargetId()).hasSize(255);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static OperatorToolsProperties props() {
        OperatorToolsProperties properties = new OperatorToolsProperties();
        properties.setEnabled(true);
        properties.getDlq().setManagementUrl("http://rabbitmq:15672");
        return properties;
    }

    private static RabbitManagementApiClient.ManagementMessage dlqMessage() {
        return new RabbitManagementApiClient.ManagementMessage(
                """
                    {"eventId":"evt-1","entityType":"LEAD","eventType":"CREATED","tenantId":"%s","entityId":"%s"}
                    """.formatted(UUID.randomUUID(), UUID.randomUUID()),
                "string", false, "crm.workflow.events.dlx", "crm.workflow.events",
                Map.of("message_id", "evt-1", "headers", Map.of(
                        "x-death", List.of(Map.of("count", 1, "reason", "rejected",
                                "exchange", "crm.events", "queue", "crm.workflow.events",
                                "routing-keys", List.of("crm.LEAD.CREATED"))))));
    }

    private static List<Method> declaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList();
    }

    private static void assertExpression(String methodName, String expected, Class<?>... parameterTypes) {
        Method found;
        try {
            found = OperatorMessageController.class.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("handler not found: " + methodName, ex);
        }
        PreAuthorize annotation = found.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("%s must be authorized", methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            type.getDeclaredMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}
