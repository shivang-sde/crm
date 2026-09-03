package com.shivang.crm.modules.form.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormResponse {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private String status;
    private String publicKey;
    private UUID acquisitionConfigId;
    private Map<String, Object> settings;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<FormFieldResponse> fields;
    private String publicUrl;
    private Long submissionCount;
}
