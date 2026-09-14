package com.shivang.crm.modules.records.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordWebhookUpdateRequest {

    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    private UUID recordTypeId;

    private UUID mappingProfileId;

    @JsonIgnore
    @Builder.Default
    private boolean mappingProfileIdPresent = false;

    private Boolean isActive;

    private String authMode;

    @JsonSetter(value = "mappingProfileId", nulls = Nulls.SET)
    public void setMappingProfileId(UUID mappingProfileId) {
        this.mappingProfileId = mappingProfileId;
        this.mappingProfileIdPresent = true;
    }

    public boolean isMappingProfileIdPresent() {
        return mappingProfileIdPresent;
    }
}
