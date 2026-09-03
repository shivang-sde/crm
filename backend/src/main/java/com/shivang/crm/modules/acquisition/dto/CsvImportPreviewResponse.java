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
@Schema(name = "CsvImportPreviewResponse", description = "CSV column discovery and preview using current mapping")
public class CsvImportPreviewResponse {

    @JsonProperty("columns")
    private List<String> columns;

    @JsonProperty("columnCount")
    private Integer columnCount;

    @JsonProperty("rowCount")
    private Integer rowCount;

    @JsonProperty("samples")
    private List<CsvColumnSample> samples;

    @JsonProperty("previewRows")
    private List<CsvRowPreview> previewRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvColumnSample {
        @JsonProperty("column")
        private String column;
        @JsonProperty("sampleValue")
        private String sampleValue;
        @JsonProperty("detectedType")
        private String detectedType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvRowPreview {
        @JsonProperty("rowNumber")
        private Integer rowNumber;
        @JsonProperty("rawPayload")
        private Map<String, Object> rawPayload;
        @JsonProperty("mapped")
        private MappedLeadData mapped;
        @JsonProperty("validated")
        private ValidatedLeadIngestionData validated;
        @JsonProperty("status")
        private String status;
        @JsonProperty("failureStage")
        private String failureStage;
    }
}
