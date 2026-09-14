package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;

@Component
public class RecordWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final CrmRecordRepository crmRecordRepository;
    private final RecordFieldRepository recordFieldRepository;
    private final WorkflowRelatedRecordResolver relatedResolver;

    public RecordWorkflowEntityContextProvider(CrmRecordRepository crmRecordRepository,
                                               RecordFieldRepository recordFieldRepository,
                                               WorkflowRelatedRecordResolver relatedResolver) {
        this.crmRecordRepository = crmRecordRepository;
        this.recordFieldRepository = recordFieldRepository;
        this.relatedResolver = relatedResolver;
    }

    @Override
    public String entityType() {
        return "RECORD";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return crmRecordRepository.findByIdAndTenantIdAndDeletedFalse(entityId, tenantId)
                .map(record -> {
                    Map<String, Object> ctx = new LinkedHashMap<>();
                    ctx.put("id", record.getId());
                    ctx.put("recordTypeId", record.getRecordTypeId());
                    ctx.put("ownerId", record.getOwnerId());
                    ctx.put("createdBy", record.getCreatedBy());
                    ctx.put("createdAt", record.getCreatedAt());
                    ctx.put("updatedAt", record.getUpdatedAt());
                    // Preserve native JSON types from JSONB; nested objects/arrays remain as Maps/Lists
                    Map<String, Object> data = record.getData();
                    Map<String, Object> dataCopy = data == null ? Map.of() : new LinkedHashMap<>(data);
                    ctx.put("data", dataCopy);
                    // One-hop explicit relationships: resolve via RecordField REFERENCE declarations
                    try {
                        var refFields = recordFieldRepository.findActiveByRecordTypeIdAndTenantId(record.getRecordTypeId(), tenantId)
                                .stream()
                                .filter(f -> f.getFieldType() == RecordFieldType.REFERENCE)
                                .filter(f -> f.getReferenceEntityType() != null && !f.getReferenceEntityType().isBlank())
                                .filter(f -> Boolean.TRUE.equals(f.getIsActive()) && !Boolean.TRUE.equals(f.getDeleted()))
                                .toList();
                        // Track which entity types already resolved (one per type, first wins)
                        java.util.Set<String> resolvedTypes = new java.util.HashSet<>();
                        for (var field : refFields) {
                            String refTypeRaw = field.getReferenceEntityType().trim().toUpperCase();
                            String relatedKey = refTypeRaw.toLowerCase(); // entity.lead, entity.contact, etc.
                            if (resolvedTypes.contains(relatedKey)) continue;
                            Object rawVal = dataCopy.get(field.getFieldKey());
                            if (rawVal == null) continue;
                            String s = String.valueOf(rawVal).trim();
                            if (s.isEmpty()) continue;
                            UUID relatedId;
                            try { relatedId = UUID.fromString(s); } catch (IllegalArgumentException e) { continue; }
                            Optional<Map<String, Object>> relatedOpt = switch (refTypeRaw) {
                                case "LEAD" -> relatedResolver.lead(tenantId, relatedId);
                                case "CONTACT" -> relatedResolver.contact(tenantId, relatedId);
                                case "ACCOUNT" -> relatedResolver.account(tenantId, relatedId);
                                case "DEAL" -> relatedResolver.deal(tenantId, relatedId);
                                // TASK/MEETING/CALL not yet supported via WorkflowRelatedRecordResolver - treat as missing (deferred)
                                default -> Optional.empty();
                            };
                            relatedOpt.ifPresent(map -> {
                                // Store one-hop related context under entity.<relatedKey>
                                ctx.put(relatedKey, map);
                                resolvedTypes.add(relatedKey);
                            });
                        }
                    } catch (Exception ignored) {
                        // Relationship resolution must not break direct Record context
                    }
                    return ctx;
                });
    }
}
