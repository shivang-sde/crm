package com.shivang.crm.modules.call.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
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
public class CallDispositionRequest {

    @NotBlank(message = "Disposition is required")
    private String disposition;

    private String notes;

    private String nextAction;

    private Instant followUpAt;
}
