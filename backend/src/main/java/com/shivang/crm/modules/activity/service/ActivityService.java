package com.shivang.crm.modules.activity.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.activity.mapper.ActivityMapper;
import com.shivang.crm.modules.activity.repository.ActivityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityMapper activityMapper;

    public ActivityResponse logActivity(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String activityType,
            String description,
            UUID userId,
            Map<String, Object> metadata) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required for user activity");
        }

        return saveActivity(
                tenantId,
                entityId,
                entityType,
                activityType,
                description,
                userId,
                "USER",
                null,
                metadata);
    }

    public ActivityResponse logSystemActivity(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String activityType,
            String description,
            String actorSource,
            Map<String, Object> metadata) {

        return saveActivity(
                tenantId,
                entityId,
                entityType,
                activityType,
                description,
                null,
                "SYSTEM",
                actorSource,
                metadata);
    }

    private ActivityResponse saveActivity(
            UUID tenantId,
            UUID entityId,
            String entityType,
            String activityType,
            String description,
            UUID performedBy,
            String actorType,
            String actorSource,
            Map<String, Object> metadata) {

        try {
            Activity activity = Activity.builder()
                    .tenantId(tenantId)
                    .entityId(entityId)
                    .entityType(entityType)
                    .activityType(activityType)
                    .description(description)
                    .performedBy(performedBy)
                    .actorType(actorType)
                    .actorSource(actorSource)
                    .metadata(
                            metadata == null
                                    ? new HashMap<>()
                                    : new HashMap<>(metadata))
                    .build();

            Activity saved = activityRepository.save(activity);
            return activityMapper.toResponse(saved);

        } catch (Exception e) {
            log.error(
                    "Failed to log activity {} for entity {}:{}",
                    activityType,
                    entityType,
                    entityId,
                    e);

            throw new RuntimeException(
                    "Failed to record activity",
                    e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getEntityActivities(
            UUID entityId,
            String entityType,
            UUID tenantId,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size);

        return activityRepository
                .findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
                        entityType,
                        entityId,
                        tenantId,
                        pageable)
                .map(activityMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getRecentActivities(
            UUID entityId,
            String entityType,
            UUID tenantId,
            List<String> activityTypes) {

        List<Activity> activities = activityRepository
                .findByEntityTypeAndEntityIdAndTenantIdAndActivityTypeInOrderByCreatedAtDesc(
                        entityType,
                        entityId,
                        tenantId,
                        activityTypes);

        return activityMapper.toResponseList(activities);
    }
}