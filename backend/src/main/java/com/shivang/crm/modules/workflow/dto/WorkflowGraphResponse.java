package com.shivang.crm.modules.workflow.dto;

import java.util.List;

public record WorkflowGraphResponse(
    WorkflowVersionResponse version,
    List<WorkflowGraphNodeResponse> nodes,
    List<WorkflowGraphEdgeResponse> edges
) {
}
