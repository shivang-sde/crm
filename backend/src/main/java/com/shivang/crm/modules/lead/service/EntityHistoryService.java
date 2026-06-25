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
     * Log activity with metadata
     */
    public void logHistoryWithMetadata(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String eventType,
            String description,
            UUID userId,
            Map<String, Object> metadata) {

        try {   

            EntityHistory activity = EntityHistory.builder()
                .tenantId(tenantId)
                .entityId(entityId)
                .entityType(entityType)
                .eventType(eventType)
                .description(description)
                .performedBy(userId)
                .changes(metadata)
                .build();

            entityHistoryRepository.save(activity);
            log.info("History logged: {} for lead: {}", eventType, entityId);

        } catch (Exception e) {
            log.error("Failed to log activity: {} for lead: {}. Error: {}", eventType, entityId, e.getMessage());
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
