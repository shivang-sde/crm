package com.shivang.crm.modules.auth.service;

import com.shivang.crm.modules.auth.dto.request.TenantProvisionRequest;
import com.shivang.crm.modules.auth.service.impl.TenantProvisionResult;

public interface TenantProvisioningService {

    TenantProvisionResult provisionTenant(TenantProvisionRequest request, String authenticatedUserId);
}
