package com.shivang.crm.modules.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoInstallationResponse {
    private String templateKey;
    private Integer templateVersion;
    private boolean alreadyInstalled;
    private Instant installedAt;
    private Map<String, Integer> counts;
}
