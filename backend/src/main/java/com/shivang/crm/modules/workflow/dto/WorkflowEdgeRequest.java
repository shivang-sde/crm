package com.shivang.crm.modules.workflow.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowEdgeRequest {

    @NotNull
    private UUID sourceNodeId;

    @NotNull
    private UUID targetNodeId;

    @Size(max = 150)
    private String edgeKey;

    private Map<String, Object> configuration;
}