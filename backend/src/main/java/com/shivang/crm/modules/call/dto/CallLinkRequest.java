package com.shivang.crm.modules.call.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for linking a call to an entity")
public class CallLinkRequest {

    @NotBlank(message = "Entity type is required")
    @Schema(description = "Entity type to link", example = "LEAD", required = true)
    private String entityType;

    @NotNull(message = "Entity ID is required")
    @Schema(description = "UUID of the entity to link", required = true)
    private UUID entityId;
}
