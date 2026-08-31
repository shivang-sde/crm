package com.shivang.crm.modules.lead.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.lead.dto.EntityHistoryResponse;
import com.shivang.crm.modules.lead.entity.EntityHistory;
import com.shivang.crm.modules.lead.mapper.EntityHistoryMapper;
import com.shivang.crm.modules.lead.repository.EntityHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EntityHistoryService {

    private final EntityHistoryRepository entityHistoryRepository;
    private final EntityHistoryMapper entityHistoryMapper;

    /**
     * Log entity creation activity
     */
    public void logEntityCreated(UUID tenantId, UUID entityId, String entityType, UUID userId) {
        logHistory(tenantId, entityId, entityType, "ENTITY_CREATED", "Entity created", userId);
    }

    /**
     * Log entity update activity
     */
    public void logEntityUpdated(UUID tenantId, UUID entityId, String entityType,    UUID userId, Map<String, Object> changes) {
        logHistoryWithMetadata(tenantId, entityId, entityType, "ENTITY_UPDATED", "Entity updated", userId, changes);
    }

    /**
     * Log activity with simple description
     */
    public void logHistory(UUID tenantId, UUID entityId, String entityType, String eventType, String description, UUID userId) {
        logHistoryWithMetadata(tenantId, entityId, entityType, eventType, description, userId, new HashMap<>());
    }

    /**
     * Log activity with metadata (best-effort: a persistence failure is logged
     * and swallowed so optional audit history never blocks the business op).
     */
    public void logHistoryWithMetadata(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String eventType,
            String description,
            UUID userId,
            Map<String, Object> metadata) {
        logHistory(tenantId, entityId, entityType, eventType, description, userId, metadata, false);
    }

    /**
     * Log authoritative analytics-critical history. A persistence failure is
     * NOT swallowed: it propagates and rolls back the enclosing transaction so
     * the transition never silently loses its history record.
     */
    public void logHistoryWithMetadataRequired(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String eventType,
            String description,
            UUID userId,
            Map<String, Object> metadata) {
        logHistory(tenantId, entityId, entityType, eventType, description, userId, metadata, true);
    }

    private void logHistory(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String eventType,
            String description,
            UUID userId,
            Map<String, Object> metadata,
            boolean requireSuccess) {

        EntityHistory activity = EntityHistory.builder()
            .tenantId(tenantId)
            .entityId(entityId)
            .entityType(entityType)
            .eventType(eventType)
            .description(description)
            .performedBy(userId)
            .changes(metadata)
            .build();

        try {
            entityHistoryRepository.save(activity);
            log.info("History logged: {} for entity: {}", eventType, entityId);
        } catch (Exception e) {
            if (requireSuccess) {
                log.error("FAILED to write authoritative history {} for entity {} - rolling back", eventType, entityId, e);
                throw new com.shivang.crm.shared.exception.BusinessException(
                    "HISTORY_WRITE_FAILED", "Failed to persist " + eventType + " history");
            }
            log.error("Failed to log activity: {} for entity: {}. Error: {}", eventType, entityId, e.getMessage());
        }
    }

    /**
     * Get activities for a lead with pagination
     */
    @Transactional(readOnly = true)
    public Page<EntityHistoryResponse> getLeadHistories(UUID entityId, UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EntityHistory> activities = entityHistoryRepository.findByEntityIdAndTenant(entityId, tenantId, pageable);
        return activities.map(entityHistoryMapper::toResponse);
    }

    /**
     * Get activities by type
     */
    @Transactional(readOnly = true)
    public Page<EntityHistoryResponse> getLeadHistoriesByType(
            UUID tenantId,
            String eventType,
             int page,
             int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<EntityHistory> activities = entityHistoryRepository
            .findByEventTypeAndTenant(eventType, tenantId, pageable);

        return activities.map(entityHistoryMapper::toResponse);
    }

    /**
     * Get recent activities for a lead
     */
    @Transactional(readOnly = true)
    public List<EntityHistoryResponse> getRecentActivities(UUID entityId, UUID tenantId, List<String> eventTypes) {
        List<EntityHistory> activities = entityHistoryRepository
            .findHistoriesByEntityIdAndTypes(entityId, tenantId, eventTypes);

        return entityHistoryMapper.toResponseList(activities);
    }

    /**
     * Count activities for a lead
     */
    @Transactional(readOnly = true)
    public Integer countActivitiesForLead(UUID entityId, UUID tenantId) {
        return entityHistoryRepository.countByEntityIdAndTenant(entityId  , tenantId);
    }
}
