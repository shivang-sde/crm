package com.shivang.crm.modules.contact.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ContactUpdateRequest", description = "Request to update an existing contact")
public class ContactUpdateRequest {

    @Schema(example = "Amit")
    private String firstName;

    @Schema(example = "Sharma")
    private String lastName;

    @Schema(example = "amit@example.com")
    private String email;

    @Schema(example = "+91-9876543210")
    private String phone;

    @Schema(example = "+91-9876543211")
    private String mobile;

    @Schema(example = "Sales Manager")
    private String jobTitle;

    @Schema(example = "Sales")
    private String department;

    @Schema(description = "UUID of the contact owner")
    private UUID ownerUserId;

    @Schema(description = "Custom fields data")
    @JsonProperty("customData")
    private Map<String, Object> customData;
}
