package com.shivang.crm.modules.activity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shivang.crm.modules.activity.entity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Page<Activity> findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
            String entityType, UUID entityId, UUID tenantId, Pageable pageable);

    Page<Activity> findByActivityTypeAndTenantIdOrderByCreatedAtDesc(String activityType, UUID tenantId, Pageable pageable);

    List<Activity> findByEntityTypeAndEntityIdAndTenantIdAndActivityTypeInOrderByCreatedAtDesc(
            String entityType, UUID entityId, UUID tenantId, List<String> activityTypes);
}
