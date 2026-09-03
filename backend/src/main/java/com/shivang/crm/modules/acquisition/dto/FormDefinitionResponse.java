package com.shivang.crm.modules.acquisition.dto;

import java.util.List;
import java.util.UUID;

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
@Schema(name = "FormDefinitionResponse", description = "Public form definition derived from active mappings")
public class FormDefinitionResponse {

    @JsonProperty("configId")
    private UUID configId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("publicKey")
    private String publicKey;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("submitButtonLabel")
    private String submitButtonLabel;

    @JsonProperty("successMessage")
    private String successMessage;

    @JsonProperty("fields")
    private List<FormField> fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormField {
        @JsonProperty("name")
        private String name; // sourcePath, e.g., first_name

        @JsonProperty("label")
        private String label;

        @JsonProperty("required")
        private Boolean required;

        @JsonProperty("dataType")
        private String dataType;

        @JsonProperty("placeholder")
        private String placeholder;

        @JsonProperty("helpText")
        private String helpText;

        @JsonProperty("type")
        private String type; // input type hint: text, email, tel, select, etc.

        @JsonProperty("options")
        private java.util.List<java.util.Map<String, String>> options;

        @JsonProperty("defaultValue")
        private String defaultValue;
    }
}
