package com.shivang.crm.modules.rbac.service;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserPermissionContext {
    private UUID userId;
    private UUID tenantId;
    private UUID roleId;
    private String roleName;
    private Map<String, String> permissions; // key: "module:action", value: "ALL/TEAM/OWN"
}
