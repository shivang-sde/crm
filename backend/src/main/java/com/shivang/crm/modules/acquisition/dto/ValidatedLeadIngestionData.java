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
@Schema(name = "ValidatedLeadIngestionData", description = "Canonical, validated lead ingestion payload before entity creation")
public class ValidatedLeadIngestionData {

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("company")
    private String company;

    @JsonProperty("sourceValue")
    private Object sourceValue;

    @JsonProperty("statusValue")
    private Object statusValue;

    @JsonProperty("customData")
    private Map<String, Object> customData;

    @JsonProperty("errors")
    private List<ValidationError> errors;
}
