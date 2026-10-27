package com.shivang.crm.modules.operations.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * Audit view of one operator recovery action.
 *
 * <p>Identifier and routing fields only. There is no field for a message body, an
 * event payload, a credential or a token, so this DTO cannot leak one.
 */
@Data
@Builder
public class OperatorActionView {

    private UUID id;
    private String actionType;
    private String targetId;
    private UUID tenantId;
    private UUID operatorUserId;
    private String operatorRole;
    private String requestId;
    private String originalExchange;
    private String originalRoutingKey;
    private String destinationExchange;
    private String destinationRoutingKey;
    private String result;

    /** Sanitised and length-capped by the audit service. */
    private String failureReason;

    private Instant createdAt;
}
