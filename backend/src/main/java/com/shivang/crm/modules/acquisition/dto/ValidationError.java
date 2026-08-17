package com.shivang.crm.modules.acquisition.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ValidationError", description = "Structured validation error for ingestion normalization")
public class ValidationError {

    @Schema(description = "Field name or target key associated with the validation issue", example = "firstName")
    private String field;

    @Schema(description = "Stable validation code", example = "REQUIRED")
    private String code;

    @Schema(description = "Human-readable validation message")
    private String message;
}
