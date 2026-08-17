package com.shivang.crm.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowVersionCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String triggerEntityType;

    @NotBlank
    @Size(max = 100)
    private String triggerEventType;
}