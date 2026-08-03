package com.shivang.crm.modules.rbac.dto.request;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRoleRequest {
    @NotBlank
    private String name;
    
    private String description;

    @Valid
    private List<PermissionScopeRequest> permissions = new ArrayList<>();
}
