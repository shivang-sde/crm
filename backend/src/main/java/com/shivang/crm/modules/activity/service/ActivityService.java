package com.shivang.crm.modules.activity.service;

import java.util.HashMap;
import java.util.List;
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
            java.util.Map<String, Object> metadata) {

        try {
            Activity activity = Activity.builder()
                    .tenantId(tenantId)
                    .entityId(entityId)
                    .entityType(entityType)
                    .activityType(activityType)
                    .description(description)
                    .performedBy(userId)
                    .metadata(metadata == null ? new HashMap<>() : metadata)
                    .build(); 

            Activity saved = activityRepository.save(activity);
            return activityMapper.toResponse(saved);

        } catch (Exception e) {
            log.error("Failed to log activity {} for entity {}:{} - {}", activityType, entityType, entityId, e.getMessage());
            throw new RuntimeException("Failed to record activity", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> getEntityActivities(UUID entityId, String entityType, UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Activity> activities = activityRepository.findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(entityType, entityId, tenantId, pageable);
        return activities.map(activityMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getRecentActivities(UUID entityId, String entityType, UUID tenantId, List<String> activityTypes) {
        List<Activity> activities = activityRepository.findByEntityTypeAndEntityIdAndTenantIdAndActivityTypeInOrderByCreatedAtDesc(entityType, entityId, tenantId, activityTypes);
        return activityMapper.toResponseList(activities);
    }
}
