package com.shivang.crm.modules.user.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    private Boolean isActive;
    private UUID roleId;
    private UUID managerId;
}
