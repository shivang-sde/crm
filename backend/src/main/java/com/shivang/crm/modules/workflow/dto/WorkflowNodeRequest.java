package com.shivang.crm.modules.workflow.dto;

import java.util.Map;

import com.shivang.crm.modules.workflow.entity.WorkflowNodeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowNodeRequest {

    @NotBlank
    @Size(max = 150)
    private String nodeKey;

    @NotNull
    private WorkflowNodeType nodeType;

    @NotBlank
    @Size(max = 200)
    private String name;

    private Map<String, Object> configuration;
}