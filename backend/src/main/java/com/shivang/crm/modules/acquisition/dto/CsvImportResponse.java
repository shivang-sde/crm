package com.shivang.crm.modules.acquisition.dto;

import java.util.List;
import java.util.Map;
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
@Schema(name = "CsvImportResponse", description = "CSV import result per row, reusing universal pipeline")
public class CsvImportResponse {

    @JsonProperty("ingestionConfigId")
    private UUID ingestionConfigId;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("totalRows")
    private Integer totalRows;

    @JsonProperty("created")
    private Integer created;

    @JsonProperty("duplicate")
    private Integer duplicate;

    @JsonProperty("rejected")
    private Integer rejected;

    @JsonProperty("failed")
    private Integer failed;

    @JsonProperty("rows")
    private List<RowResult> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        @JsonProperty("rowNumber")
        private Integer rowNumber;
        @JsonProperty("status")
        private String status;
        @JsonProperty("failureStage")
        private String failureStage;
        @JsonProperty("errorCode")
        private String errorCode;
        @JsonProperty("errorMessage")
        private String errorMessage;
        @JsonProperty("leadId")
        private UUID leadId;
        @JsonProperty("eventId")
        private UUID eventId;
        @JsonProperty("rawPayload")
        private Map<String, Object> rawPayload;
    }
}
