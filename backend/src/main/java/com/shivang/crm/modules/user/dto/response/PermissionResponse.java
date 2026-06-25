package com.shivang.crm.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class PermissionResponse {
    private UUID id;
    private String module;
    private String action;
    private String accessScope; // ALL/TEAM/OWN
    private String description;
}
