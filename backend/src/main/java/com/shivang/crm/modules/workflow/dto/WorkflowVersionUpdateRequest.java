package com.shivang.crm.modules.workflow.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowVersionUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String triggerEntityType;

    @NotBlank
    @Size(max = 100)
    private String triggerEventType;

    private Map<String, Object> triggerFilter;
}
