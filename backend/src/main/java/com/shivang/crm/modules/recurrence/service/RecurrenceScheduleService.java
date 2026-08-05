package com.shivang.crm.modules.recurrence.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.recurrence.calculator.RecurrenceCalculator;
import com.shivang.crm.modules.recurrence.entity.RecurrenceSchedule;
import com.shivang.crm.modules.recurrence.repository.RecurrenceScheduleRepository;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.model.Recurrence;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecurrenceScheduleService {

    private final RecurrenceScheduleRepository recurrenceScheduleRepository;
    private final TenantRepository tenantRepository;
    private final RecurrenceCalculator recurrenceCalculator;

    @Transactional
    public RecurrenceSchedule upsertSchedule(
            UUID tenantId,
            ReminderSourceType sourceType,
            UUID sourceId,
            Instant occurrenceAt,
            Instant remindAt,
            Recurrence recurrence,
            String tenantTimezone
    ) {
        if (tenantId == null || sourceId == null || occurrenceAt == null || recurrence == null) {
            throw new IllegalArgumentException("tenantId, sourceId, occurrenceAt and recurrence are required");
        }

        String resolvedTenantTimezone = resolveTenantTimezone(tenantId, tenantTimezone);
        RecurrenceSchedule schedule = recurrenceScheduleRepository
                .findByTenantIdAndSourceTypeAndSourceIdAndDeletedFalse(tenantId, sourceType, sourceId)
                .orElseGet(() -> {
                    RecurrenceSchedule newSchedule = new RecurrenceSchedule();
                    newSchedule.setTenantId(tenantId);
                    newSchedule.setSourceType(sourceType);
                    newSchedule.setSourceId(sourceId);
                    newSchedule.setActive(true);
                    return newSchedule;
                });

        Long reminderOffsetSeconds = calculateReminderOffsetSeconds(occurrenceAt, remindAt, sourceType);

        schedule.setRecurrence(recurrence);
        schedule.setInitialOccurrenceAt(occurrenceAt);
        schedule.setLastOccurrenceAt(occurrenceAt);
        schedule.setGeneratedOccurrenceCount(1);
        schedule.setReminderOffsetSeconds(reminderOffsetSeconds);
        schedule.setActive(true);

        Instant nextOccurrenceAt = recurrenceCalculator.calculateNextOccurrence(
                occurrenceAt,
                recurrence,
                resolvedTenantTimezone,
                1
        );
        schedule.setNextOccurrenceAt(nextOccurrenceAt);

        return recurrenceScheduleRepository.save(schedule);
    }

    @Transactional
    public void deactivateSchedule(UUID tenantId, ReminderSourceType sourceType, UUID sourceId) {
        recurrenceScheduleRepository.findByTenantIdAndSourceTypeAndSourceIdAndDeletedFalse(tenantId, sourceType, sourceId)
                .ifPresent(schedule -> {
                    schedule.setActive(false);
                    schedule.setNextOccurrenceAt(null);
                    recurrenceScheduleRepository.save(schedule);
                });
    }

    private Long calculateReminderOffsetSeconds(Instant occurrenceAt, Instant remindAt, ReminderSourceType sourceType) {
        if (occurrenceAt == null || remindAt == null) {
            return null;
        }

        if (remindAt.isAfter(occurrenceAt)) {
            String message = switch (sourceType) {
                case TASK -> "Task remindAt cannot be after dueDate";
                case CALL -> "Call remindAt cannot be after startTime";
                case MEETING -> "Meeting remindAt cannot be after startTime";
            };
            throw new BusinessException("INVALID_REMINDER", message);
        }

        return ChronoUnit.SECONDS.between(remindAt, occurrenceAt);
    }

    private String resolveTenantTimezone(UUID tenantId, String tenantTimezone) {
        if (tenantTimezone != null && !tenantTimezone.isBlank()) {
            return tenantTimezone;
        }
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getTimezone())
                .filter(timezone -> timezone != null && !timezone.isBlank())
                .orElse("UTC");
    }
}
