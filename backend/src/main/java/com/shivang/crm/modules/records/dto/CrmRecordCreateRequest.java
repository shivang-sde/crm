package com.shivang.crm.modules.records.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmRecordCreateRequest {

    @NotNull(message = "recordTypeId is required")
    private UUID recordTypeId;

    private Map<String, Object> data;
}
