package com.shivang.crm.modules.tenant.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantUpdateRequest {

    @NotBlank
    private String companyName;

    private String companyEmail;
    private String companyPhone;
    private String website;

    private String country;
    private String state;
    private String city;
    private String addressLine1;
    private String postalCode;

    private String logoUrl;
    private String primaryColor;

    private String industry;

    private String timezone;
    private String currencyCode;
    private String language;

    private String planType;

    @Min(1)
    private Integer maxUsers;

    private LocalDate subscriptionEndDate;

    private Boolean isActive;
}