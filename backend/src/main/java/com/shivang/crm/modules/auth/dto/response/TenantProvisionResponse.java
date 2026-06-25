package com.shivang.crm.modules.auth.dto.response;


import com.shivang.crm.modules.user.dto.response.UserResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisionResponse {
    private TenantInfo tenant;
    private UserResponse admin;
}
