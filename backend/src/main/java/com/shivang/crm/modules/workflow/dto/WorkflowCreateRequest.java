package com.shivang.crm.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String name;
}