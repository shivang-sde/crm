// TenantResponseDTO.java
package com.shivang.crm.modules.tenant.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TenantResponseDTO {
    private UUID id;
    private String name;
    private String slug;

    private String planType;
    private Integer maxUsers;
    private Integer currentUsers;

    private String companyName;
    private String companyEmail;
    private String companyPhone;
    private String website;

    private String industry;

    private String timezone;
    private String currencyCode;
    private String language;

    private String addressLine1;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String logoUrl;
    private String primaryColor;
    private LocalDate subscriptionEndDate;

    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private ResellerInfo reseller;

    @Data
    @Builder
    public static class ResellerInfo {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
    }
}