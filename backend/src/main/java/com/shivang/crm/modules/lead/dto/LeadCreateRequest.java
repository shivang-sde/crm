package com.shivang.crm.modules.lead.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(name = "LeadCreateRequest", description = "Request to create a new lead")
public class LeadCreateRequest {

    @NotBlank(message = "First name is required")
    @Schema(example = "John", description = "First name of the lead")
    private String firstName;

    @Schema(example = "Doe", description = "Last name of the lead")
    private String lastName;

    @Email(message = "Email should be valid")
    @Schema(example = "john@example.com", description = "Email address of the lead")
    private String email;

    @Schema(example = "+91-9876543210", description = "Phone number of the lead")
    private String phone;

    @Schema(example = "ABC Corporation", description = "Company name of the lead")
    private String company;

    @NotNull(message = "Status ID is required")
    @Schema(description = "UUID of the lead status")
    private UUID statusId;

    @Schema(description = "UUID of the lead source")
    private UUID sourceId;

    @Schema(description = "UUID of the user who owns this lead")
    private UUID ownerUserId;

    @Schema(example = "50", description = "Lead score (0-100)")
    private Integer score;

    @Schema(description = "Custom fields data")
    @JsonProperty("customData")
    private Map<String, Object> customData;
}
