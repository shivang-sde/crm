package com.shivang.crm.modules.form.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTargetType;
import com.shivang.crm.modules.acquisition.mapping.LeadIngestionTransformType;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionFieldMappingRepository;
import com.shivang.crm.modules.acquisition.service.LeadIngestionTargetFieldService;
import com.shivang.crm.modules.form.dto.FormCreateRequest;
import com.shivang.crm.modules.form.dto.FormFieldRequest;
import com.shivang.crm.modules.form.dto.FormFieldResponse;
import com.shivang.crm.modules.form.dto.FormResponse;
import com.shivang.crm.modules.form.dto.FormUpdateRequest;
import com.shivang.crm.modules.form.entity.Form;
import com.shivang.crm.modules.form.entity.FormField;
import com.shivang.crm.modules.form.entity.FormFieldType;
import com.shivang.crm.modules.form.entity.FormStatus;
import com.shivang.crm.modules.form.repository.FormFieldRepository;
import com.shivang.crm.modules.form.repository.FormRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormService {

    private static final String PUBLIC_KEY_PREFIX = "form_";
    private static final int RANDOM_BYTES_LENGTH = 18;
    private static final int MAX_KEY_ATTEMPTS = 10;

    private final FormRepository formRepository;
    private final FormFieldRepository formFieldRepository;
    private final LeadIngestionConfigRepository configRepository;
    private final LeadIngestionFieldMappingRepository mappingRepository;
    private final LeadIngestionTargetFieldService targetFieldService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public FormResponse createForm(UUID tenantId, FormCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Form name is required");
        }
        String publicKey = generateUniquePublicKey();
        Form form = Form.builder()
            .tenantId(tenantId)
            .name(request.getName().trim())
            .description(request.getDescription())
            .status(FormStatus.DRAFT)
            .publicKey(publicKey)
            .settings(new HashMap<>(Map.of("submitButtonLabel", "Submit", "successMessage", "Thanks! We'll contact you shortly.")))
            .build();
        Form saved = formRepository.save(form);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FormResponse> listForms(UUID tenantId) {
        return formRepository.findByTenantIdAndDeletedFalseOrderByUpdatedAtDesc(tenantId).stream()
            .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FormResponse getForm(UUID tenantId, UUID formId) {
        Form form = requireForm(tenantId, formId);
        return toResponse(form);
    }

    @Transactional
    public FormResponse updateForm(UUID tenantId, UUID formId, FormUpdateRequest request) {
        Form form = requireForm(tenantId, formId);

        if (request.getName() != null) {
            if (request.getName().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Form name cannot be blank");
            form.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            form.setDescription(request.getDescription());
        }
        if (request.getSettings() != null) {
            form.setSettings(request.getSettings());
        }

        // Atomic fields update
        if (request.getFields() != null) {
            updateFields(tenantId, form, request.getFields());
        }

        form.setUpdatedAt(Instant.now());
        Form saved = formRepository.save(form);
        // If published, sync mappings immediately (immediate live)
        if (saved.getStatus() == FormStatus.PUBLISHED) {
            syncMappings(tenantId, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteForm(UUID tenantId, UUID formId, UUID userId) {
        Form form = requireForm(tenantId, formId);
        form.softDelete(userId);
        formRepository.save(form);
        // Also soft delete fields
        List<FormField> fields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(form.getId());
        for (FormField f : fields) {
            f.softDelete(userId);
            formFieldRepository.save(f);
        }
    }

    @Transactional
    public FormResponse publishForm(UUID tenantId, UUID formId) {
        Form form = requireForm(tenantId, formId);

        // Validation
        if (form.getName() == null || form.getName().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Form must have a name");
        }
        List<FormField> fields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(formId);
        if (fields.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Form must have at least one field");
        }
        // Validate field keys unique, labels not blank, options valid
        validateFields(tenantId, fields);

        // Ensure publicKey
        if (form.getPublicKey() == null || form.getPublicKey().isBlank()) {
            form.setPublicKey(generateUniquePublicKey());
        }

        // Ensure acquisition config exists and sync
        LeadIngestionConfig config = ensureAcquisitionConfig(tenantId, form);
        syncMappings(tenantId, form);

        // Activate config
        config.setActive(true);
        configRepository.save(config);

        form.setStatus(FormStatus.PUBLISHED);
        form.setPublishedAt(Instant.now());
        form.setAcquisitionConfigId(config.getId());
        Form saved = formRepository.save(form);
        log.info("Form {} published for tenant {} with config {}", formId, tenantId, config.getId());
        return toResponse(saved);
    }

    @Transactional
    public FormResponse unpublishForm(UUID tenantId, UUID formId) {
        Form form = requireForm(tenantId, formId);
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new BusinessException("VALIDATION_ERROR", "Only published forms can be unpublished");
        }
        form.setStatus(FormStatus.UNPUBLISHED);
        Form saved = formRepository.save(form);
        // Deactivate acquisition config but keep it
        if (form.getAcquisitionConfigId() != null) {
            configRepository.findById(form.getAcquisitionConfigId()).ifPresent(cfg -> {
                cfg.setActive(false);
                configRepository.save(cfg);
            });
        }
        return toResponse(saved);
    }

    @Transactional
    public FormResponse duplicateForm(UUID tenantId, UUID formId) {
        Form original = requireForm(tenantId, formId);
        List<FormField> originalFields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(formId);

        Form clone = Form.builder()
            .tenantId(tenantId)
            .name(original.getName() + " Copy")
            .description(original.getDescription())
            .status(FormStatus.DRAFT)
            .publicKey(generateUniquePublicKey())
            .settings(original.getSettings() != null ? new HashMap<>(original.getSettings()) : new HashMap<>())
            .build();
        Form savedClone = formRepository.save(clone);

        for (FormField f : originalFields) {
            FormField clonedField = FormField.builder()
                .formId(savedClone.getId())
                .tenantId(tenantId)
                .fieldKey(f.getFieldKey())
                .type(f.getType())
                .label(f.getLabel())
                .placeholder(f.getPlaceholder())
                .helpText(f.getHelpText())
                .required(f.getRequired())
                .orderIndex(f.getOrderIndex())
                .defaultValue(f.getDefaultValue())
                .options(f.getOptions() != null ? new ArrayList<>(f.getOptions()) : null)
                .crmTargetType(f.getCrmTargetType())
                .crmTargetField(f.getCrmTargetField())
                .transformType(f.getTransformType())
                .transformConfig(f.getTransformConfig() != null ? new HashMap<>(f.getTransformConfig()) : null)
                .build();
            formFieldRepository.save(clonedField);
        }

        return toResponse(savedClone);
    }

    private void updateFields(UUID tenantId, Form form, List<FormFieldRequest> requests) {
        // Validate keys unique in request
        List<String> keys = requests.stream().map(r -> r.getFieldKey() != null ? r.getFieldKey().trim() : "").toList();
        if (keys.stream().distinct().count() != keys.size()) {
            throw new BusinessException("VALIDATION_ERROR", "Duplicate field keys in form");
        }
        for (String k : keys) {
            if (k.isBlank()) throw new BusinessException("VALIDATION_ERROR", "Field key cannot be blank");
            if (!k.matches("[A-Za-z0-9_]+")) throw new BusinessException("VALIDATION_ERROR", "Field key must be alphanumeric/underscore: " + k);
        }

        List<FormField> existing = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(form.getId());
        Map<String, FormField> existingById = existing.stream().collect(Collectors.toMap(f -> f.getId().toString(), f -> f));
        Map<String, FormField> existingByKey = existing.stream().collect(Collectors.toMap(FormField::getFieldKey, f -> f, (a,b)->a));

        // Track which existing should be kept
        List<UUID> keepIds = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            FormFieldRequest req = requests.get(i);
            String key = req.getFieldKey().trim();
            FormField field;
            if (req.getId() != null && existingById.containsKey(req.getId())) {
                field = existingById.get(req.getId());
                // If key changed, ensure not duplicate with another kept
                field.setFieldKey(key);
            } else if (existingByKey.containsKey(key) && existingByKey.get(key).getId().toString().equals(req.getId() == null ? "" : req.getId())) {
                field = existingByKey.get(key);
            } else {
                // Check if key already exists for different field
                if (existingByKey.containsKey(key) && (req.getId() == null || !existingByKey.get(key).getId().toString().equals(req.getId()))) {
                    // Allow if that existing will be deleted (not in keep)
                    // But for simplicity, if key exists and not same id, treat as update of that field
                    field = existingByKey.get(key);
                } else {
                    field = new FormField();
                    field.setId(UUID.randomUUID());
                    field.setFormId(form.getId());
                    field.setTenantId(tenantId);
                    field.setCreatedAt(Instant.now());
                }
            }

            // Validate type
            FormFieldType type;
            try {
                type = FormFieldType.valueOf(req.getType().toUpperCase());
            } catch (Exception e) {
                throw new BusinessException("VALIDATION_ERROR", "Invalid field type: " + req.getType());
            }

            field.setFieldKey(key);
            field.setType(type);
            if (req.getLabel() == null || req.getLabel().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Field label is required for " + key);
            field.setLabel(req.getLabel().trim());
            field.setPlaceholder(req.getPlaceholder());
            field.setHelpText(req.getHelpText());
            field.setRequired(Boolean.TRUE.equals(req.getRequired()));
            field.setOrderIndex(req.getOrderIndex() != null ? req.getOrderIndex() : i);
            field.setDefaultValue(req.getDefaultValue());
            field.setOptions(req.getOptions());

            // CRM mapping validation if provided
            if (req.getCrmTargetField() != null && !req.getCrmTargetField().isBlank()) {
                if (req.getCrmTargetType() == null || req.getCrmTargetType().isBlank()) {
                    throw new BusinessException("VALIDATION_ERROR", "CRM target type required when target field is set for " + key);
                }
                // Validate target exists
                LeadIngestionTargetType targetType;
                try {
                    targetType = LeadIngestionTargetType.valueOf(req.getCrmTargetType().toUpperCase());
                } catch (Exception e) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid CRM target type for " + key);
                }
                if (!targetFieldService.isSupportedTarget(targetType, req.getCrmTargetField().trim(), tenantId)) {
                    throw new BusinessException("VALIDATION_ERROR", "Unsupported CRM target for " + key + ": " + req.getCrmTargetType() + ":" + req.getCrmTargetField());
                }
                field.setCrmTargetType(targetType.name());
                field.setCrmTargetField(req.getCrmTargetField().trim());
            } else {
                field.setCrmTargetType(null);
                field.setCrmTargetField(null);
            }

            field.setTransformType(req.getTransformType() != null ? req.getTransformType().toUpperCase() : "NONE");
            field.setTransformConfig(req.getTransformConfig());
            field.setUpdatedAt(Instant.now());
            field.setDeleted(false);

            FormField saved = formFieldRepository.save(field);
            keepIds.add(saved.getId());
        }

        // Soft delete removed fields
        for (FormField existingField : existing) {
            if (!keepIds.contains(existingField.getId())) {
                existingField.softDelete(tenantId); // using tenantId as deleted_by for simple
                formFieldRepository.save(existingField);
            }
        }
    }

    private void validateFields(UUID tenantId, List<FormField> fields) {
        // Check at least one field has CRM mapping? Not required, but warn if none mapped
        // Validate each field's CRM target if set
        for (FormField f : fields) {
            if (f.getFieldKey() == null || f.getFieldKey().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Field key required");
            if (f.getLabel() == null || f.getLabel().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Field label required for " + f.getFieldKey());
            if (f.getCrmTargetField() != null && f.getCrmTargetType() == null) {
                throw new BusinessException("VALIDATION_ERROR", "CRM target type required for " + f.getFieldKey());
            }
            if (f.getCrmTargetField() != null) {
                LeadIngestionTargetType t;
                try { t = LeadIngestionTargetType.valueOf(f.getCrmTargetType()); } catch (Exception e) { throw new BusinessException("VALIDATION_ERROR", "Invalid CRM target type for " + f.getFieldKey()); }
                if (!targetFieldService.isSupportedTarget(t, f.getCrmTargetField(), tenantId)) {
                    throw new BusinessException("VALIDATION_ERROR", "Invalid CRM target for " + f.getFieldKey());
                }
            }
            // Validate options for SELECT etc
            if (f.getType() == FormFieldType.SELECT || f.getType() == FormFieldType.RADIO || f.getType() == FormFieldType.MULTISELECT) {
                if (f.getOptions() == null || f.getOptions().isEmpty()) {
                    throw new BusinessException("VALIDATION_ERROR", "Options required for " + f.getFieldKey());
                }
            }
        }
    }

    private LeadIngestionConfig ensureAcquisitionConfig(UUID tenantId, Form form) {
        if (form.getAcquisitionConfigId() != null) {
            return configRepository.findByIdAndTenantIdAndDeletedFalse(form.getAcquisitionConfigId(), tenantId)
                .orElseGet(() -> createAcquisitionConfig(tenantId, form));
        }
        LeadIngestionConfig cfg = createAcquisitionConfig(tenantId, form);
        form.setAcquisitionConfigId(cfg.getId());
        formRepository.save(form);
        return cfg;
    }

    private LeadIngestionConfig createAcquisitionConfig(UUID tenantId, Form form) {
        // Reuse existing config if one exists with same publicKey? For now create new
        LeadIngestionConfig cfg = LeadIngestionConfig.builder()
            .tenantId(tenantId)
            .name("Form: " + form.getName())
            .transportType(LeadIngestionTransportType.FORM)
            .publicKey(form.getPublicKey())
            .active(true)
            .settings(new HashMap<>(Map.of("formId", form.getId().toString())))
            .build();
        return configRepository.save(cfg);
    }

    private void syncMappings(UUID tenantId, Form form) {
        LeadIngestionConfig config = ensureAcquisitionConfig(tenantId, form);
        List<FormField> fields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(form.getId());

        // Existing mappings for this config
        var existingMappings = mappingRepository.findByTenantIdAndIngestionConfigIdAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(tenantId, config.getId());
        Map<String, com.shivang.crm.modules.acquisition.mapping.LeadIngestionFieldMapping> existingByTarget = new HashMap<>();
        for (var m : existingMappings) {
            if (Boolean.TRUE.equals(m.getActive()) && !Boolean.TRUE.equals(m.getDeleted())) {
                existingByTarget.put(m.getTargetType() + ":" + m.getTargetField(), m);
            }
        }

        // Build desired mappings from form fields that have CRM target
        Map<String, FormField> desiredByTarget = new HashMap<>();
        for (FormField f : fields) {
            if (f.getCrmTargetField() != null && f.getCrmTargetType() != null) {
                String key = f.getCrmTargetType() + ":" + f.getCrmTargetField();
                // If duplicate target in form fields themselves, keep first, later will be validated but we take last
                desiredByTarget.put(key, f);
            }
        }

        // Create/update desired
        for (Map.Entry<String, FormField> entry : desiredByTarget.entrySet()) {
            FormField f = entry.getValue();
            String targetTypeStr = f.getCrmTargetType();
            String targetField = f.getCrmTargetField();
            LeadIngestionTargetType targetType = LeadIngestionTargetType.valueOf(targetTypeStr);

            var existing = existingByTarget.get(entry.getKey());
            if (existing != null) {
                existing.setSourcePath(f.getFieldKey());
                existing.setTransformType(LeadIngestionTransformType.valueOf(f.getTransformType() != null ? f.getTransformType() : "NONE"));
                existing.setTransformConfig(f.getTransformConfig());
                existing.setRequired(Boolean.TRUE.equals(f.getRequired()));
                existing.setActive(true);
                existing.setDisplayOrder(f.getOrderIndex());
                existing.setUpdatedAt(Instant.now());
                mappingRepository.save(existing);
            } else {
                var newMapping = com.shivang.crm.modules.acquisition.mapping.LeadIngestionFieldMapping.builder()
                    .tenantId(tenantId)
                    .ingestionConfigId(config.getId())
                    .sourcePath(f.getFieldKey())
                    .targetType(targetType)
                    .targetField(targetField)
                    .transformType(LeadIngestionTransformType.valueOf(f.getTransformType() != null ? f.getTransformType() : "NONE"))
                    .transformConfig(f.getTransformConfig())
                    .required(Boolean.TRUE.equals(f.getRequired()))
                    .active(true)
                    .displayOrder(f.getOrderIndex())
                    .build();
                mappingRepository.save(newMapping);
            }
        }

        // Delete mappings that are no longer desired (soft delete)
        for (String key : existingByTarget.keySet()) {
            if (!desiredByTarget.containsKey(key)) {
                var toDelete = existingByTarget.get(key);
                toDelete.setActive(false);
                toDelete.softDelete(tenantId);
                mappingRepository.save(toDelete);
            }
        }

        // Also update config name if form name changed
        if (!config.getName().equals("Form: " + form.getName())) {
            config.setName("Form: " + form.getName());
            configRepository.save(config);
        }
    }

    private Form requireForm(UUID tenantId, UUID formId) {
        return formRepository.findByIdAndTenantIdAndDeletedFalse(formId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Form not found"));
    }

    private FormResponse toResponse(Form form) {
        List<FormField> fields = formFieldRepository.findByFormIdAndDeletedFalseOrderByOrderIndexAsc(form.getId());
        List<FormFieldResponse> fieldResponses = fields.stream().map(this::toFieldResponse).toList();

        String publicUrl = null;
        if (form.getPublicKey() != null) {
            publicUrl = "/forms/public/" + form.getPublicKey();
        }

        // Submission count: count events for linked acquisition config if exists
        Long submissionCount = null;
        if (form.getAcquisitionConfigId() != null) {
            try {
                // Use event repository count - we don't have direct count method, so approximate via query
                // For now, leave null and frontend can fetch via events API
            } catch (Exception ignored) {}
        }

        return FormResponse.builder()
            .id(form.getId())
            .tenantId(form.getTenantId())
            .name(form.getName())
            .description(form.getDescription())
            .status(form.getStatus().name())
            .publicKey(form.getPublicKey())
            .acquisitionConfigId(form.getAcquisitionConfigId())
            .settings(form.getSettings())
            .publishedAt(form.getPublishedAt())
            .createdAt(form.getCreatedAt())
            .updatedAt(form.getUpdatedAt())
            .fields(fieldResponses)
            .publicUrl(publicUrl)
            .submissionCount(submissionCount)
            .build();
    }

    private FormFieldResponse toFieldResponse(FormField field) {
        return FormFieldResponse.builder()
            .id(field.getId())
            .fieldKey(field.getFieldKey())
            .type(field.getType().name())
            .label(field.getLabel())
            .placeholder(field.getPlaceholder())
            .helpText(field.getHelpText())
            .required(field.getRequired())
            .orderIndex(field.getOrderIndex())
            .defaultValue(field.getDefaultValue())
            .options(field.getOptions())
            .crmTargetType(field.getCrmTargetType())
            .crmTargetField(field.getCrmTargetField())
            .transformType(field.getTransformType())
            .transformConfig(field.getTransformConfig())
            .createdAt(field.getCreatedAt())
            .updatedAt(field.getUpdatedAt())
            .build();
    }

    private String generateUniquePublicKey() {
        for (int attempt = 0; attempt < MAX_KEY_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[RANDOM_BYTES_LENGTH];
            secureRandom.nextBytes(randomBytes);
            String candidate = PUBLIC_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (!formRepository.existsByPublicKey(candidate) && !configRepository.existsByPublicKey(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("KEY_GENERATION_FAILED", "Unable to generate a unique public key");
    }
}
