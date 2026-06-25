package com.shivang.crm.modules.user.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean isActive;
    private Boolean emailVerified;
    private String roleName;
    private UUID roleId;
    private UUID tenantId;
    private String tenantName;
    private UUID resellerId;
    private String resellerName;
    private Instant lastLoginAt;
    private Instant createdAt;
}
