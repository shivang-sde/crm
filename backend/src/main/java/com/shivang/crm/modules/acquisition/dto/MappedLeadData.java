package com.shivang.crm.modules.acquisition.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MappedLeadData", description = "Preview output of ingestion mapping execution")
public class MappedLeadData {

    @JsonProperty("standardFields")
    private Map<String, Object> standardFields;

    @JsonProperty("systemFields")
    private Map<String, Object> systemFields;

    @JsonProperty("customFields")
    private Map<String, Object> customFields;

    @JsonProperty("errors")
    private List<String> errors;
}
