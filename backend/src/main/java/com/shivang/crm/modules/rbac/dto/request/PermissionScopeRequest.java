package com.shivang.crm.modules.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PermissionScopeRequest {
    @NotNull
    private UUID permissionId;
    
    @NotBlank
    private String accessScope; // ALL, TEAM, OWN, NONE
}
