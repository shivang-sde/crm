package com.shivang.crm.modules.auth.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInfo {

    private UUID id;

    private String name;

    private String slug;

    private Integer maxUsers;
    private Integer currentUsers;
    private String status;
    private UUID resellerId;
    private LocalDate subscriptionEndDate;

    private String planType;

    private Boolean isActive;

    private Instant createdAt;

    private Instant updatedAt;
}