package com.shivang.crm.modules.demo.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoResetResponse {

    private String templateKey;
    private Integer templateVersion;
    private boolean reset;
    private Map<String, Integer> deletedCounts;
    private Map<String, Integer> preservedCounts;
}
