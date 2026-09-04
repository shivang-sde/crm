package com.shivang.crm.modules.call.dto;

import java.util.UUID;

public record CallingProviderOption(
    String providerKey,
    String providerName,
    UUID connectorInstanceId,
    String connectorName,
    String environment,
    boolean active
) {}
