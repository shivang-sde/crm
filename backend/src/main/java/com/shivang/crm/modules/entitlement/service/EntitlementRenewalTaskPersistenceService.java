package com.shivang.crm.modules.entitlement.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.entitlement.entity.CustomerEntitlement;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.service.ReminderPlanningService;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.repository.TaskRepository;
import com.shivang.crm.modules.task.service.TaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementRenewalTaskPersistenceService {

    private static final String TASK_ENTITY_TYPE = "ENTITLEMENT";
    private static final String CYCLE_KEY = "renewalCycle";
    private static final long REMINDER_DELAY_SECONDS = 30;

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final ReminderPlanningService reminderPlanningService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createRenewalFollowUpIfMissing(CustomerEntitlement entitlement) {
        UUID ownerId = entitlement.getOwnerId();
        if (ownerId == null) {
            log.warn("Skipping renewal follow-up for entitlement {} because it has no owner", entitlement.getId());
            return 0;
        }
        User owner = userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, entitlement.getTenantId())
                .orElse(null);
        if (owner == null || !Boolean.TRUE.equals(owner.getIsActive())) {
            log.warn("Skipping renewal follow-up for entitlement {} because owner {} is missing or inactive",
                    entitlement.getId(), ownerId);
            return 0;
        }

        String renewalCycle = entitlement.getEndDate().toString();
        if (renewalTaskAlreadyExists(entitlement.getTenantId(), entitlement.getId(), renewalCycle)) {
            return 0;
        }

        Instant dueAt = entitlement.getRenewalDueDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<String, Object> customData = new HashMap<>();
        customData.put(CYCLE_KEY, renewalCycle);
        customData.put("entitlementId", entitlement.getId().toString());
        customData.put("endDate", entitlement.getEndDate().toString());
        customData.put("renewalDueDate", entitlement.getRenewalDueDate().toString());
        customData.put("source", "ENTITLEMENT_RENEWAL_AUTOMATION");

        TaskCreateRequest request = TaskCreateRequest.builder()
                .subject("Renewal follow-up: " + entitlement.getName())
                .description("Renewal follow-up for entitlement \"" + entitlement.getName()
                        + "\". Expires on " + entitlement.getEndDate()
                        + "; follow-up due " + entitlement.getRenewalDueDate() + ".")
                .dueDate(dueAt)
                .entityType(TASK_ENTITY_TYPE)
                .entityId(entitlement.getId())
                .ownerUserId(ownerId)
                .customData(customData)
                .build();

        TaskResponse savedTask = taskService.createTaskInternal(
                entitlement.getTenantId(),
                ownerId,
                request,
                Map.of("source", "ENTITLEMENT_RENEWAL_AUTOMATION"));

        // The follow-up date is already due when the task is created, so the
        // generic task reminder (remindAt <= now) would be created as SKIPPED
        // and never delivered. Plan one reminder through the existing planning
        // service with a near-future schedule so the owner is notified.
        Instant scheduledAt = Instant.now().plusSeconds(REMINDER_DELAY_SECONDS);
        reminderPlanningService.createReminderIfMissing(
                entitlement.getTenantId(),
                ReminderSourceType.TASK,
                savedTask.getId(),
                dueAt,
                scheduledAt,
                "Renewal follow-up reminder",
                String.format("Renewal follow-up for entitlement \"%s\" is due (contract ends %s).",
                        entitlement.getName(), entitlement.getEndDate()),
                Map.of(
                        "sourceType", ReminderSourceType.TASK.name(),
                        "sourceId", savedTask.getId().toString(),
                        "entitlementId", entitlement.getId().toString(),
                        "endDate", renewalCycle,
                        "renewalDueDate", entitlement.getRenewalDueDate().toString()));
        return 1;
    }

    private boolean renewalTaskAlreadyExists(UUID tenantId, UUID entitlementId, String renewalCycle) {
        return taskRepository.findByTenantIdAndEntityTypeAndEntityId(tenantId, TASK_ENTITY_TYPE, entitlementId)
                .stream()
                .anyMatch(task -> task.getCustomData() != null
                        && renewalCycle.equals(String.valueOf(task.getCustomData().get(CYCLE_KEY))));
    }
}
