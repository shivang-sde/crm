package com.shivang.crm.modules.workflow.dto;

import java.util.Map;

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

    // Optional trigger filter predicate — reuses existing condition shape {logic, conditions:[{field,operator,value}]}
    private Map<String, Object> triggerFilter;
}