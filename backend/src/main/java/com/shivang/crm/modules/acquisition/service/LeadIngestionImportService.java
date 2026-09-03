package com.shivang.crm.modules.acquisition.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.dto.CsvImportPreviewResponse;
import com.shivang.crm.modules.acquisition.dto.CsvImportResponse;
import com.shivang.crm.modules.acquisition.dto.MappedLeadData;
import com.shivang.crm.modules.acquisition.dto.ValidatedLeadIngestionData;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.event.LeadIngestionFailureStage;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionImportService {

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionMappingService leadIngestionMappingService;
    private final LeadIngestionValidationService leadIngestionValidationService;
    private final LeadIngestionProcessingService leadIngestionProcessingService;

    @Value("${app.import.csv.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes;

    @Value("${app.import.csv.max-rows:5000}")
    private int maxRows;

    @Value("${app.import.csv.max-columns:50}")
    private int maxColumns;

    public CsvImportPreviewResponse preview(UUID tenantId, UUID configId, MultipartFile file) {
        LeadIngestionConfig config = requireImportConfig(tenantId, configId, false);
        ParsedCsv parsed = parseAndValidate(file);

        List<String> columns = parsed.headers;
        int rowCount = parsed.records.size();

        // Build samples
        List<CsvImportPreviewResponse.CsvColumnSample> samples = new ArrayList<>();
        for (String col : columns) {
            String sample = null;
            String detectedType = "NULL";
            for (CSVRecord rec : parsed.records) {
                String val = rec.get(col);
                if (val != null && !val.isBlank()) {
                    sample = val;
                    detectedType = detectType(val);
                    break;
                }
            }
            samples.add(CsvImportPreviewResponse.CsvColumnSample.builder()
                .column(col)
                .sampleValue(sample)
                .detectedType(detectedType)
                .build());
        }

        // Preview first 10 rows using current mapping without persisting events
        List<CsvImportPreviewResponse.CsvRowPreview> previewRows = new ArrayList<>();
        int previewCount = Math.min(10, parsed.records.size());
        for (int i = 0; i < previewCount; i++) {
            CSVRecord rec = parsed.records.get(i);
            Map<String, Object> rawPayload = toRawPayload(rec, columns);
            MappedLeadData mapped = leadIngestionMappingService.previewFromPayload(tenantId, configId, rawPayload);
            ValidatedLeadIngestionData validated = leadIngestionValidationService.validateAndNormalize(tenantId, mapped);

            String status;
            String failureStage = null;
            if (!mapped.getErrors().isEmpty()) {
                status = LeadIngestionEventStatus.REJECTED.name();
                failureStage = LeadIngestionFailureStage.MAPPING.name();
            } else if (!validated.getErrors().isEmpty()) {
                status = LeadIngestionEventStatus.REJECTED.name();
                failureStage = LeadIngestionFailureStage.VALIDATION.name();
            } else {
                // Check duplicate would happen only on actual import; for preview we show VALID vs DUPLICATE hint via validated?
                // We treat preview as VALID if no errors
                status = "VALID";
            }

            previewRows.add(CsvImportPreviewResponse.CsvRowPreview.builder()
                .rowNumber(i + 1)
                .rawPayload(rawPayload)
                .mapped(mapped)
                .validated(validated)
                .status(status)
                .failureStage(failureStage)
                .build());
        }

        return CsvImportPreviewResponse.builder()
            .columns(columns)
            .columnCount(columns.size())
            .rowCount(rowCount)
            .samples(samples)
            .previewRows(previewRows)
            .build();
    }

    public CsvImportResponse importCsv(UUID tenantId, UUID configId, MultipartFile file) {
        LeadIngestionConfig config = requireImportConfig(tenantId, configId, true);
        ParsedCsv parsed = parseAndValidate(file);

        int totalRows = parsed.records.size();
        int created = 0, duplicate = 0, rejected = 0, failed = 0;
        List<CsvImportResponse.RowResult> rowResults = new ArrayList<>();

        int maxDetailRows = 50; // return detailed per-row for first 50 to avoid huge response

        for (int i = 0; i < parsed.records.size(); i++) {
            CSVRecord rec = parsed.records.get(i);
            int rowNumber = i + 1;
            Map<String, Object> rawPayload = toRawPayload(rec, parsed.headers);
            // Add traceability: keep row number in payload for mapping debug (optional)
            // We store rawPayload as column->value, plus _csvRowNumber for audit
            Map<String, Object> storedPayload = new LinkedHashMap<>(rawPayload);
            storedPayload.put("_csvRowNumber", rowNumber);
            storedPayload.put("_csvFileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.csv");

            // Create event per row (one event per row) — reuses universal pipeline
            LeadIngestionEvent event = LeadIngestionEvent.builder()
                .tenantId(tenantId)
                .ingestionConfigId(configId)
                .externalEventId("csv:" + configId + ":" + rowNumber + ":" + System.nanoTime())
                .idempotencyKey(null) // each import row is distinct; rely on lead dedup, not event dedup
                .rawPayload(storedPayload)
                .headers(Map.of("source", "CSV_IMPORT", "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.csv", "rowNumber", String.valueOf(rowNumber)))
                .status(LeadIngestionEventStatus.RECEIVED)
                .failureStage(null)
                .attemptCount(1)
                .receivedAt(Instant.now())
                .build();

            LeadIngestionEvent savedEvent = leadIngestionEventRepository.save(event);

            LeadIngestionEvent processed;
            try {
                processed = leadIngestionProcessingService.processEvent(tenantId, configId, savedEvent.getId());
            } catch (Exception ex) {
                // Fallback: mark as failed via failure service logic (already handled inside processing for most cases)
                // If processing threw, fetch current state
                processed = leadIngestionEventRepository.findById(savedEvent.getId()).orElse(savedEvent);
                if (processed.getStatus() == LeadIngestionEventStatus.RECEIVED || processed.getStatus() == LeadIngestionEventStatus.PROCESSING) {
                    processed.setStatus(LeadIngestionEventStatus.FAILED);
                    processed.setFailureStage(LeadIngestionFailureStage.UNKNOWN);
                    processed.setErrorCode("PROCESSING_ERROR");
                    processed.setErrorMessage(ex.getMessage() != null && ex.getMessage().length() > 1000 ? ex.getMessage().substring(0, 1000) : ex.getMessage());
                    processed.setProcessedAt(Instant.now());
                    processed = leadIngestionEventRepository.save(processed);
                }
            }

            // Aggregate
            switch (processed.getStatus()) {
                case PROCESSED -> created++;
                case DUPLICATE -> duplicate++;
                case REJECTED -> rejected++;
                case FAILED -> failed++;
                default -> failed++;
            }

            if (rowResults.size() < maxDetailRows) {
                rowResults.add(CsvImportResponse.RowResult.builder()
                    .rowNumber(rowNumber)
                    .status(processed.getStatus().name())
                    .failureStage(processed.getFailureStage() != null ? processed.getFailureStage().name() : null)
                    .errorCode(processed.getErrorCode())
                    .errorMessage(processed.getErrorMessage())
                    .leadId(processed.getLeadId())
                    .eventId(processed.getId())
                    .rawPayload(rawPayload)
                    .build());
            }
        }

        return CsvImportResponse.builder()
            .ingestionConfigId(configId)
            .fileName(file.getOriginalFilename())
            .totalRows(totalRows)
            .created(created)
            .duplicate(duplicate)
            .rejected(rejected)
            .failed(failed)
            .rows(rowResults)
            .build();
    }

    private LeadIngestionConfig requireImportConfig(UUID tenantId, UUID configId, boolean requireActive) {
        LeadIngestionConfig config = leadIngestionConfigRepository.findByIdAndTenantIdAndDeletedFalse(configId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Import source not found"));
        if (config.getTransportType() != LeadIngestionTransportType.IMPORT) {
            throw new BusinessException("VALIDATION_ERROR", "Source is not an IMPORT transport");
        }
        if (requireActive && !Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("VALIDATION_ERROR", "Import source is inactive — activate it before importing");
        }
        return config;
    }

    private ParsedCsv parseAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "CSV file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException("VALIDATION_ERROR", "CSV file too large (max " + (maxFileSizeBytes / 1024 / 1024) + "MB)");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".csv")) {
            throw new BusinessException("VALIDATION_ERROR", "File must have .csv extension");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase().contains("csv") && !contentType.equals("text/plain") && !contentType.equals("application/vnd.ms-excel") && !contentType.equals("application/octet-stream")) {
            // Allow but warn; don't strictly reject
            log.warn("Unexpected CSV content type: {}", contentType);
        }

        try (InputStream is = file.getInputStream();
             Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreHeaderCase(false)
                .setIgnoreSurroundingSpaces(true)
                .build();

            CSVParser parser = format.parse(reader);
            List<String> headers = parser.getHeaderNames();
            if (headers == null || headers.isEmpty()) {
                throw new BusinessException("VALIDATION_ERROR", "CSV header row is required");
            }
            // Normalize headers: trim
            List<String> normalizedHeaders = new ArrayList<>();
            for (String h : headers) {
                String trimmed = h != null ? h.trim() : "";
                if (trimmed.isBlank()) {
                    throw new BusinessException("VALIDATION_ERROR", "CSV header contains blank column name");
                }
                if (!trimmed.matches("[A-Za-z0-9_]+")) {
                    throw new BusinessException("VALIDATION_ERROR", "CSV header '" + trimmed + "' must be alphanumeric/underscore (e.g., first_name). Found: '" + h + "'");
                }
                normalizedHeaders.add(trimmed);
            }
            // Check duplicates case-insensitive
            long distinct = normalizedHeaders.stream().map(String::toLowerCase).distinct().count();
            if (distinct != normalizedHeaders.size()) {
                throw new BusinessException("VALIDATION_ERROR", "CSV header contains duplicate column names");
            }
            if (normalizedHeaders.size() > maxColumns) {
                throw new BusinessException("VALIDATION_ERROR", "CSV has too many columns (max " + maxColumns + ")");
            }

            List<CSVRecord> records = new ArrayList<>();
            for (CSVRecord rec : parser) {
                // Skip blank rows (all values blank)
                boolean allBlank = true;
                for (String col : normalizedHeaders) {
                    String val = rec.get(col);
                    if (val != null && !val.isBlank()) {
                        allBlank = false;
                        break;
                    }
                }
                if (allBlank) continue;
                // Check column count matches header (CSVRecord handles)
                records.add(rec);
                if (records.size() > maxRows) {
                    throw new BusinessException("VALIDATION_ERROR", "CSV has too many rows (max " + maxRows + ")");
                }
            }

            if (records.isEmpty()) {
                throw new BusinessException("VALIDATION_ERROR", "CSV contains no data rows");
            }

            return new ParsedCsv(normalizedHeaders, records);

        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            log.error("CSV parsing failed", ex);
            throw new BusinessException("VALIDATION_ERROR", "Failed to parse CSV: " + ex.getMessage());
        }
    }

    private Map<String, Object> toRawPayload(CSVRecord rec, List<String> headers) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String col : headers) {
            String val = rec.get(col);
            // Preserve empty as null for mapping default handling; trim string
            if (val != null) {
                String trimmed = val.trim();
                map.put(col, trimmed.isEmpty() ? null : trimmed);
            } else {
                map.put(col, null);
            }
        }
        return map;
    }

    private String detectType(String val) {
        if (val == null || val.isBlank()) return "NULL";
        String t = val.trim();
        // Try number
        try {
            new java.math.BigDecimal(t);
            return "NUMBER";
        } catch (Exception ignored) {}
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) return "BOOLEAN";
        // Could be array if contains comma? but treat as string
        return "STRING";
    }

    private static class ParsedCsv {
        final List<String> headers;
        final List<CSVRecord> records;

        ParsedCsv(List<String> headers, List<CSVRecord> records) {
            this.headers = headers;
            this.records = records;
        }
    }
}
