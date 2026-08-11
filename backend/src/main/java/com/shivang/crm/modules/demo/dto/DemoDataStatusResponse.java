package com.shivang.crm.modules.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoDataStatusResponse {
    private String templateKey;
    private Integer templateVersion;
    private boolean installed;
    private Instant installedAt;
    private UUID installedBy;
    private Map<String, Integer> counts;
}
