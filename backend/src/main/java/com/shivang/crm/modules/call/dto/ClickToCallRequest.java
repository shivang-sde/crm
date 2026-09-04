package com.shivang.crm.modules.call.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickToCallRequest {
    private String entityType;
    private UUID entityId;
    private String phoneNumber; // optional override
    private String subject;
    private String providerKey;
    private UUID connectorInstanceId;
}
