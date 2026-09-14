package com.shivang.crm.modules.records.dto.display;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayConfigResponse {

    private UUID recordTypeId;
    private UUID tenantId;
    private DisplayConfigRequest.ListConfig list;
    private DisplayConfigRequest.DetailConfig detail;
    private boolean isCustom;
    private Instant createdAt;
    private Instant updatedAt;
}
