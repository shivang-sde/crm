package com.shivang.crm.modules.records.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhookCreateRequest {

    @NotBlank(message = "name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "webhookKey is required")
    @Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "webhookKey must be lowercase alphanumeric with _ or -, starting with a letter")
    @Size(min = 3, max = 100)
    private String webhookKey;

    @Size(max = 2000)
    private String description;

    @NotNull(message = "recordTypeId is required")
    private UUID recordTypeId;

    private UUID mappingProfileId;

    private Boolean isActive;

    private String authMode;
}
