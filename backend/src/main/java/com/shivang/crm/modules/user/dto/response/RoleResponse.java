package com.shivang.crm.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID id;
    private String name;
    private String level;
    private String description;
    private List<PermissionResponse> permissions;
}
