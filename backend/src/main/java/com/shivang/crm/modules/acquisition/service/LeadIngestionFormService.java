package com.shivang.crm.modules.acquisition.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.dto.FormDefinitionResponse;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEvent;
import com.shivang.crm.modules.acquisition.event.LeadIngestionEventStatus;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionFieldMapping;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionEventRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionFieldMappingRepository;
import com.shivang.crm.modules.form.entity.Form;
import com.shivang.crm.modules.form.entity.FormField;
import com.shivang.crm.modules.form.repository.FormFieldRepository;
import com.shivang.crm.modules.form.repository.FormRepository;
import com.shivang.crm.modules.integration.webhook.HeaderSanitizer;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadIngestionFormService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LeadIngestionConfigRepository leadIngestionConfigRepository;
    private final LeadIngestionFieldMappingRepository leadIngestionFieldMappingRepository;
    private final LeadIngestionEventRepository leadIngestionEventRepository;
    private final LeadIngestionProcessingService leadIngestionProcessingService;
    private final LeadIngestionFailureService leadIngestionFailureService;
    private final LeadIngestionTargetFieldService leadIngestionTargetFieldService;
    private final FormRepository formRepository;
    private final FormFieldRepository formFieldRepository;
    private final HeaderSanitizer headerSanitizer;
    private final ObjectMapper objectMapper;

    public FormDefinitionResponse getDefinition(String publicKey) {
        // Prefer new Form builder definition if exists and published
        var formOpt = formRepository.findByPublicKeyAndDeletedFalse(publicKey);
        if (formOpt.isPresent()) {
            Form form = formOpt.get();
            // Only published forms are publicly usable; draft/unpublished handled as inactive
            boolean isActive = form.getStatus() == com.shivang.crm.modules.form.entity.FormStatus.PUBLISHED;
            List<FormField> formFields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(form.getId());
            List<FormDefinitionResponse.FormField> fields = new ArrayList<>();
            for (FormField ff : formFields) {
                String dataType = ff.getType() != null ? ff.getType().name() : "TEXT";
                String inputType = toInputType(dataType, ff.getFieldKey());
                fields.add(FormDefinitionResponse.FormField.builder()
                    .name(ff.getFieldKey())
                    .label(ff.getLabel())
                    .required(Boolean.TRUE.equals(ff.getRequired()))
                    .dataType(dataType)
                    .placeholder(ff.getPlaceholder())
                    .helpText(ff.getHelpText())
                    .type(inputType)
                    .options(ff.getOptions())
                    .defaultValue(ff.getDefaultValue())
                    .build());
            }
            Map<String, Object> settings = form.getSettings();
            String submitLabel = settings != null ? (String) settings.get("submitButtonLabel") : null;
            String successMsg = settings != null ? (String) settings.get("successMessage") : null;
            return FormDefinitionResponse.builder()
                .configId(form.getId())
                .name(form.getName())
                .publicKey(form.getPublicKey())
                .active(isActive)
                .submitButtonLabel(submitLabel != null ? submitLabel : "Submit")
                .successMessage(successMsg != null ? successMsg : "Your information has been submitted successfully.")
                .fields(fields)
                .build();
        }

        // Fallback to legacy acquisition config-derived form (for backward compat with pre-builder FORMS)
        LeadIngestionConfig config = leadIngestionConfigRepository.findByPublicKeyAndDeletedFalse(publicKey)
            .orElseThrow(() -> new NotFoundException("Form not found"));

        if (config.getTransportType() != LeadIngestionTransportType.FORM) {
            throw new NotFoundException("Form not found");
        }

        List<LeadIngestionFieldMapping> mappings = leadIngestionFieldMappingRepository
            .findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(config.getTenantId(), config.getId())
            .stream().filter(m -> Boolean.TRUE.equals(m.getActive())).toList();

        List<FormDefinitionResponse.FormField> fields = new ArrayList<>();
        for (LeadIngestionFieldMapping mapping : mappings) {
            String dataType = resolveDataType(config.getTenantId(), mapping);
            String label = resolveLabel(config.getTenantId(), mapping, dataType);
            String inputType = toInputType(dataType, mapping.getTargetField());
            boolean required = Boolean.TRUE.equals(mapping.getRequired());

            fields.add(FormDefinitionResponse.FormField.builder()
                .name(mapping.getSourcePath())
                .label(label)
                .required(required)
                .dataType(dataType)
                .placeholder(null)
                .helpText(null)
                .type(inputType)
                .options(null)
                .defaultValue(mapping.getDefaultValue())
                .build());
        }

        Map<String, Object> legacySettings = config.getSettings();
        String legacySubmitLabel = legacySettings != null ? (String) legacySettings.get("submitButtonLabel") : null;
        String legacySuccessMsg = legacySettings != null ? (String) legacySettings.get("successMessage") : null;
        return FormDefinitionResponse.builder()
            .configId(config.getId())
            .name(config.getName())
            .publicKey(config.getPublicKey())
            .active(config.getActive())
            .submitButtonLabel(legacySubmitLabel != null ? legacySubmitLabel : "Submit")
            .successMessage(legacySuccessMsg != null ? legacySuccessMsg : "Your information has been submitted successfully.")
            .fields(fields)
            .build();
    }

    public LeadIngestionEvent submit(String publicKey, String rawBody, HttpHeaders headers) {
        LeadIngestionConfig config = leadIngestionConfigRepository.findByPublicKeyAndDeletedFalse(publicKey)
            .orElseThrow(() -> new NotFoundException("Form not found"));

        if (config.getTransportType() != LeadIngestionTransportType.FORM) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "Form endpoint is not available");
        }
        if (!Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("INGESTION_ENDPOINT_NOT_ACCEPTING", "This form is currently unavailable");
        }

        Map<String, Object> payload = parsePayload(rawBody);
        Map<String, Object> sanitizedHeaders = sanitizeHeaders(headers);

        // Honeypot spam check: if hidden field "website" is filled, treat as spam but return success to not reveal
        Object honeypot = payload.get("website");
        if (honeypot instanceof String s && !s.isBlank()) {
            log.warn("Form honeypot triggered for configId={} publicKey={}", config.getId(), publicKey);
            // Return a dummy processed event status to indicate success without creating lead
            LeadIngestionEvent dummy = LeadIngestionEvent.builder()
                .tenantId(config.getTenantId())
                .ingestionConfigId(config.getId())
                .status(LeadIngestionEventStatus.PROCESSED)
                .receivedAt(Instant.now())
                .processedAt(Instant.now())
                .build();
            return dummy;
        }
        // Remove honeypot from payload before processing
        payload.remove("website");

        String externalEventId = null;
        String idempotencyKey = null;

        if (rawBody != null && rawBody.length() > 20000) {
            throw new BusinessException("VALIDATION_ERROR", "Form submission too large");
        }

        LeadIngestionEvent event = LeadIngestionEvent.builder()
            .tenantId(config.getTenantId())
            .ingestionConfigId(config.getId())
            .externalEventId(externalEventId)
            .idempotencyKey(idempotencyKey)
            .rawPayload(payload)
            .headers(sanitizedHeaders)
            .status(LeadIngestionEventStatus.RECEIVED)
            .receivedAt(Instant.now())
            .build();

        LeadIngestionEvent savedEvent = leadIngestionEventRepository.save(event);
        log.info("Form submission captured for tenant={} configId={} eventId={}", config.getTenantId(), config.getId(), savedEvent.getId());

        LeadIngestionEvent processedEvent;
        try {
            processedEvent = leadIngestionProcessingService.processEvent(config.getTenantId(), config.getId(), savedEvent.getId());
        } catch (Exception ex) {
            log.error("Form processing failed for event {} tenant={} configId={}", savedEvent.getId(), config.getTenantId(), config.getId(), ex);
            LeadIngestionEvent failedEvent = leadIngestionFailureService.markFailed(config.getTenantId(), savedEvent.getId(), "PROCESSING_ERROR", ex.getMessage());
            if (failedEvent == null) throw ex;
            processedEvent = failedEvent;
        }

        return processedEvent;
    }

    private String resolveDataType(java.util.UUID tenantId, LeadIngestionFieldMapping mapping) {
        // Try to get from target field service
        try {
            var fields = leadIngestionTargetFieldService.listTargetFields(tenantId);
            for (var f : fields) {
                if (f.getTargetType() == mapping.getTargetType() && f.getFieldKey().equals(mapping.getTargetField())) {
                    return f.getDataType() != null ? f.getDataType() : "TEXT";
                }
            }
        } catch (Exception ignored) {}
        return "TEXT";
    }

    private String resolveLabel(java.util.UUID tenantId, LeadIngestionFieldMapping mapping, String dataType) {
        try {
            var fields = leadIngestionTargetFieldService.listTargetFields(tenantId);
            for (var f : fields) {
                if (f.getTargetType() == mapping.getTargetType() && f.getFieldKey().equals(mapping.getTargetField())) {
                    return f.getLabel() != null ? f.getLabel() : mapping.getTargetField();
                }
            }
        } catch (Exception ignored) {}
        return mapping.getTargetField();
    }

    private String toInputType(String dataType, String targetField) {
        if (dataType == null) return "text";
        String dt = dataType.toLowerCase();
        String tf = targetField != null ? targetField.toLowerCase() : "";
        if (dt.contains("email") || tf.contains("email")) return "email";
        if (dt.contains("phone") || tf.contains("phone") || tf.contains("mobile")) return "tel";
        if (dt.contains("url")) return "url";
        if (dt.contains("number")) return "number";
        if (dt.contains("date")) return "date";
        if (dt.contains("boolean")) return "checkbox";
        if (dt.contains("textarea")) return "textarea";
        return "text";
    }

    private Map<String, Object> parsePayload(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Form data is required");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid form data");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException("VALIDATION_ERROR", "Form data must be an object");
        }
        Map<String, Object> map = objectMapper.convertValue(root, MAP_TYPE);
        // Basic allowed fields check: limit to 30 fields, each value length reasonable
        if (map.size() > 30) {
            throw new BusinessException("VALIDATION_ERROR", "Too many form fields");
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isBlank() || k.length() > 100) {
                throw new BusinessException("VALIDATION_ERROR", "Invalid form field name");
            }
            Object v = e.getValue();
            if (v instanceof String s && s.length() > 2000) {
                throw new BusinessException("VALIDATION_ERROR", "Field '" + k + "' too long");
            }
        }
        return map;
    }

    private Map<String, Object> sanitizeHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) return Map.of();
        Map<String, java.util.List<String>> rawHeaders = new HashMap<>();
        headers.forEach(rawHeaders::put);
        return headerSanitizer.sanitize(rawHeaders);
    }
}
