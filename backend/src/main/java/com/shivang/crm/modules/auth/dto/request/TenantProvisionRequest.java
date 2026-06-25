package com.shivang.crm.modules.auth.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisionRequest {

    @NotBlank(message = "Company name is required")
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

    private UUID resellerId;

    private String planType;
    private LocalDate subscriptionEndDate;

    @Min(value = 1, message = "Max users must be at least 1")
    @NotNull(message = "Max users is required")
    private Integer maxUsers;

    @Valid
    @NotNull(message = "Admin data is required")
    private TenantAdminRequest admin;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantAdminRequest {

        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email must be valid")
        private String email;

        @NotBlank(message = "Admin password is required")
        private String password;

        @NotBlank(message = "Admin first name is required")
        private String firstName;

        @NotBlank(message = "Admin last name is required")
        private String lastName;
    }
}
