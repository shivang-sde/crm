package com.shivang.crm.modules.acquisition.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shivang.crm.modules.acquisition.dto.CsvImportPreviewResponse;
import com.shivang.crm.modules.acquisition.dto.CsvImportResponse;
import com.shivang.crm.modules.acquisition.service.LeadIngestionImportService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/acquisition/configs/{configId}/import")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Import", description = "CSV import into universal lead ingestion pipeline")
public class LeadIngestionImportController {

    private final LeadIngestionImportService leadIngestionImportService;
    private final TenantContext tenantContext;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview CSV import", description = "Upload CSV, discover columns, and preview first 10 rows through current mapping without creating leads")
    public ResponseEntity<ApiResponse<CsvImportPreviewResponse>> preview(
            @Parameter(description = "Ingestion config UUID (must be IMPORT)") @PathVariable UUID configId,
            @Parameter(description = "CSV file") @RequestParam("file") MultipartFile file) {

        UUID tenantId = requireTenantId();
        CsvImportPreviewResponse response = leadIngestionImportService.preview(tenantId, configId, file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import CSV", description = "Upload CSV and import each row as an acquisition event through the universal pipeline (mapping, transform, normalization, validation, dedup, lead creation, workflow)")
    public ResponseEntity<ApiResponse<CsvImportResponse>> importCsv(
            @Parameter(description = "Ingestion config UUID (must be IMPORT)") @PathVariable UUID configId,
            @Parameter(description = "CSV file") @RequestParam("file") MultipartFile file) {

        UUID tenantId = requireTenantId();
        CsvImportResponse response = leadIngestionImportService.importCsv(tenantId, configId, file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID requireTenantId() {
        return tenantContext.requireTenantId();
    }
}
