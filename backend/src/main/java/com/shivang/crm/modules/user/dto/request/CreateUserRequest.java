package com.shivang.crm.modules.user.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank @Email
    private String email;
    
    @NotBlank
    private String firstName;
    
    @NotBlank
    private String lastName;
    
    @NotBlank
    private String password;
    
    private UUID roleId; // Optional role to assign; tenant context users default to EMPLOYEE when omitted
    private Boolean isActive;
    private UUID tenantId; // Optional tenant target for platform-context tenant user creation
}
