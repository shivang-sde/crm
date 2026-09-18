package com.shivang.crm.modules.commercial.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSyncRequest {

    @NotBlank(message = "externalCustomerId is required")
    private String externalCustomerId;

    private String customerName;
    private String companyName;
    private String email;
    private String phone;
    private String gstin;
}
