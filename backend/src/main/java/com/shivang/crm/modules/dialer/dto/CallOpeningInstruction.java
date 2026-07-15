package com.shivang.crm.modules.dialer.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallOpeningInstruction {
    private String actionType;
    private String displayMode;
    private String entityType;
    private String entityId;
    private String callId;
    private String externalCallId;
    private String layoutId;
    private String route;
    private String title;
    private String reason;
    private Boolean resolved;
    private Map<String, Object> metadata;
}
