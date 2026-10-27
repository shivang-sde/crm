package com.shivang.crm.modules.operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shivang.crm.modules.integration.service.ConnectorAuditSanitizer;
import com.shivang.crm.modules.operations.config.OperatorToolsProperties;

class DeadLetterMessageMapperTest {

    private OperatorToolsProperties properties;
    private DeadLetterMessageMapper mapper;

    @BeforeEach
    void setUp() {
        properties = new OperatorToolsProperties();
        mapper = new DeadLetterMessageMapper(properties, new ConnectorAuditSanitizer());
    }

    @Test
    void readsXDeathMetadataThatRabbitMqActuallyProvides() {
        DeadLetterView view = mapper.toView(message(), true);

        assertThat(view.getDeathCount()).isEqualTo(7);
        assertThat(view.getDeathReason()).isEqualTo("rejected");
        assertThat(view.getOriginalExchange()).isEqualTo("crm.events");
        assertThat(view.getOriginalQueue()).isEqualTo("crm.workflow.events");
        assertThat(view.getOriginalRoutingKey()).isEqualTo("crm.LEAD.CREATED");
        assertThat(view.getFirstDeathExchange()).isEqualTo("crm.events");
        assertThat(view.getFirstDeathRoutingKey()).isEqualTo("crm.LEAD.CREATED");
    }

    @Test
    void resolvesDestinationFromXDeathNotFromAGuess() {
        assertThat(mapper.resolveDestination(message()).exchange()).isEqualTo("crm.events");
        assertThat(mapper.resolveDestination(message()).routingKey()).isEqualTo("crm.LEAD.CREATED");
    }

    @Test
    void fallsBackToTheMessageOwnExchangeWhenNoDeathHeaderIsPresent() {
        RabbitManagementApiClient.ManagementMessage noHeaders = new RabbitManagementApiClient.ManagementMessage(
                "{}", "string", false, "crm.workflow.events.dlx", "crm.workflow.events",
                Map.of("message_id", "evt-1"));

        assertThat(mapper.resolveDestination(noHeaders).exchange()).isEqualTo("crm.workflow.events.dlx");
        assertThat(mapper.resolveDestination(noHeaders).routingKey()).isEqualTo("crm.workflow.events");
        assertThat(mapper.toView(noHeaders, true).getDeathCount()).isNull();
    }

    @Test
    void extractsOnlyTheDeclaredCanonicalEventFields() {
        DeadLetterView view = mapper.toView(message(), true);

        assertThat(view.getEventId()).isEqualTo("evt-1");
        assertThat(view.getEntityType()).isEqualTo("LEAD");
        assertThat(view.getEventType()).isEqualTo("CREATED");
        assertThat(view.getEntityId()).isNotBlank();
        assertThat(view.getTenantId()).isNotNull();
    }

    @Test
    void unparseableBodyYieldsNoTenantSoTheMessageStaysPlatformOnly() {
        RabbitManagementApiClient.ManagementMessage junk = new RabbitManagementApiClient.ManagementMessage(
                "this is not json", "string", false, "crm.workflow.events.dlx", "crm.workflow.events",
                Map.of("message_id", "evt-1"));

        DeadLetterView view = mapper.toView(junk, true);

        assertThat(view.getTenantId()).isNull();
        assertThat(view.getEventId()).isNull();
    }

    @Test
    void defaultDestinationAllowListIsTheCrmEventExchangeOnly() {
        DeadLetterMessageMapper.Destination approved =
                new DeadLetterMessageMapper.Destination("crm.events", "crm.LEAD.CREATED");
        DeadLetterMessageMapper.Destination hostile =
                new DeadLetterMessageMapper.Destination("some.other.exchange", "k");

        assertThat(mapper.isApprovedDestination(approved)).isTrue();
        assertThat(mapper.isApprovedDestination(hostile)).isFalse();
    }

    @Test
    void nullDestinationIsNeverApproved() {
        assertThat(mapper.isApprovedDestination((DeadLetterMessageMapper.Destination) null)).isFalse();
        assertThat(mapper.isApprovedDestination(new DeadLetterMessageMapper.Destination(null, "k"))).isFalse();
    }

    @Test
    void xCrmRedriveOfHeaderIsSurfaced() {
        assertThat(mapper.toView(message(), true).getRedriveOf()).isEqualTo("ancestor-evt-0");
    }

    private static RabbitManagementApiClient.ManagementMessage message() {
        String payload = """
            {"eventId":"evt-1","entityType":"LEAD","eventType":"CREATED","tenantId":"%s",
             "entityId":"%s","occurredAt":"2026-01-01T00:00:00Z",
             "metadata":{"actorId":"a","password":"leaked"}}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        return new RabbitManagementApiClient.ManagementMessage(
                payload, "string", false,
                "crm.workflow.events.dlx", "crm.workflow.events",
                Map.of("message_id", "evt-1",
                        "content_type", "application/json",
                        "headers", Map.of(
                                "x-death", List.of(Map.of(
                                        "count", 7,
                                        "reason", "rejected",
                                        "exchange", "crm.events",
                                        "queue", "crm.workflow.events",
                                        "routing-keys", List.of("crm.LEAD.CREATED"))),
                                "x-first-death-exchange", "crm.events",
                                "x-first-death-routing-key", "crm.LEAD.CREATED",
                                "x-first-death-queue", "crm.workflow.events",
                                "x-crm-redrive-of", "ancestor-evt-0")));
    }
}
