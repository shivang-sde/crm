package com.shivang.crm.modules.records.dto.display;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayConfigRequest {

    @Valid
    private ListConfig list;

    @Valid
    private DetailConfig detail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListConfig {
        @Valid
        private List<Column> columns;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Column {
            private UUID fieldId;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailConfig {
        @Valid
        private List<Section> sections;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Section {
            private String id;

            @NotBlank(message = "Section name is required")
            @Size(max = 100, message = "Section name must be at most 100 characters")
            private String name;

            private List<UUID> fieldIds;
        }
    }
}
