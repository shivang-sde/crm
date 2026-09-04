package com.shivang.crm.modules.call.service;

import java.util.List;
import java.util.UUID;

import com.shivang.crm.modules.call.dto.CallingProviderOption;

public interface CallingProviderService {
    List<CallingProviderOption> getAvailableCallingProviders(UUID tenantId);
}
