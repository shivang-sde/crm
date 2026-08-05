package com.shivang.crm.modules.recurrence.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.meeting.repository.MeetingRepository;
import com.shivang.crm.modules.recurrence.calculator.RecurrenceCalculator;
import com.shivang.crm.modules.recurrence.config.RecurrenceProperties;
import com.shivang.crm.modules.recurrence.entity.RecurrenceSchedule;
import com.shivang.crm.modules.recurrence.repository.RecurrenceScheduleRepository;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.service.ReminderPlanningService;
import com.shivang.crm.modules.task.repository.TaskRepository;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.shared.model.Recurrence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurrenceGenerationService {

    private final RecurrenceScheduleRepository recurrenceScheduleRepository;
    private final RecurrenceCalculator recurrenceCalculator;
    private final ReminderPlanningService reminderPlanningService;
    private final TaskRepository taskRepository;
    private final CallRepository callRepository;
    private final MeetingRepository meetingRepository;
    private final TenantRepository tenantRepository;
    private final RecurrenceProperties recurrenceProperties;

    @Transactional
    public int generateBatch(Instant now, int batchSize) {
        Instant windowEnd = now.plus(recurrenceProperties.getGenerationWindowDays(), ChronoUnit.DAYS);
        List<RecurrenceSchedule> dueSchedules = recurrenceScheduleRepository.findDueSchedulesForGeneration(windowEnd, batchSize);
        if (dueSchedules.isEmpty()) {
            return 0;
        }

        int generated = 0;
        for (RecurrenceSchedule schedule : dueSchedules) {
            try {
                generated += processSchedule(schedule, windowEnd);
            } catch (Exception ex) {
                log.warn("Failed to process recurrence schedule {}", schedule.getId(), ex);
            }
        }
        return generated;
    }

    private int processSchedule(RecurrenceSchedule schedule, Instant windowEnd) {
        if (!Boolean.TRUE.equals(schedule.getActive()) || schedule.getNextOccurrenceAt() == null) {
            return 0;
        }

        if (!scheduleIsEligible(schedule)) {
            deactivate(schedule, "source became ineligible");
            return 0;
        }

        int processed = 0;
        while (Boolean.TRUE.equals(schedule.getActive())
                && schedule.getNextOccurrenceAt() != null
                && !schedule.getNextOccurrenceAt().isAfter(windowEnd)) {
            Instant currentOccurrence = schedule.getNextOccurrenceAt();
            if (!scheduleIsEligible(schedule)) {
                deactivate(schedule, "source became ineligible");
                break;
            }

            if (schedule.getReminderOffsetSeconds() != null) {
                reminderPlanningService.createRecurrenceReminderIfMissing(
                    schedule.getTenantId(),
                    schedule.getSourceType(),
                    schedule.getSourceId(),
                    currentOccurrence,
                    schedule.getReminderOffsetSeconds(),
                    resolveSubject(schedule.getSourceType(), schedule.getSourceId(), schedule.getTenantId())
                );
            }

            Instant nextOccurrence = calculateNextOccurrence(schedule, currentOccurrence);
            schedule.setLastOccurrenceAt(currentOccurrence);
            schedule.setGeneratedOccurrenceCount(schedule.getGeneratedOccurrenceCount() + 1);
            schedule.setNextOccurrenceAt(nextOccurrence);

            if (nextOccurrence == null || !nextOccurrence.isAfter(currentOccurrence)) {
                log.warn(
                    "Deactivating recurrence schedule {} because the calculated next occurrence {} is not after current occurrence {}",
                    schedule.getId(),
                    nextOccurrence,
                    currentOccurrence
                );
                deactivate(schedule, "calculated occurrence did not advance");
                break;
            }

            recurrenceScheduleRepository.save(schedule);
            processed++;
        }

        return processed;
    }

    private boolean scheduleIsEligible(RecurrenceSchedule schedule) {
        if (schedule.getRecurrence() == null) {
            return false;
        }

        if (schedule.getSourceType() == ReminderSourceType.TASK) {
            return taskRepository.findByIdAndTenantIdAndDeletedFalse(schedule.getSourceId(), schedule.getTenantId())
                .map(task -> !task.isDeleted()
                    && task.getStatus() != null
                    && task.getStatus() != com.shivang.crm.modules.task.entity.TaskStatus.COMPLETED
                    && !Boolean.TRUE.equals(task.getIsClosed()))
                .orElse(false);
        }
        if (schedule.getSourceType() == ReminderSourceType.CALL) {
            return callRepository.findById(schedule.getSourceId())
                .filter(call -> !call.isDeleted() && call.getTenantId().equals(schedule.getTenantId()))
                .map(call -> call.getStatus() == com.shivang.crm.modules.call.entity.Call.CallStatus.PLANNED)
                .orElse(false);
        }
        if (schedule.getSourceType() == ReminderSourceType.MEETING) {
            return meetingRepository.findById(schedule.getSourceId())
                .filter(meeting -> !meeting.isDeleted() && meeting.getTenantId().equals(schedule.getTenantId()))
                .map(meeting -> meeting.getStatus() == com.shivang.crm.modules.meeting.entity.Meeting.MeetingStatus.PLANNED)
                .orElse(false);
        }
        return false;
    }

    private Instant calculateNextOccurrence(RecurrenceSchedule schedule, Instant currentOccurrence) {
        Recurrence recurrence = schedule.getRecurrence();
        String tenantTimezone = resolveTenantTimezone(schedule.getTenantId());
        return recurrenceCalculator.calculateNextOccurrence(
            currentOccurrence,
            recurrence,
            tenantTimezone,
            schedule.getGeneratedOccurrenceCount()
        );
    }

    private void deactivate(RecurrenceSchedule schedule, String reason) {
        log.debug("Deactivating recurrence schedule {} because {}", schedule.getId(), reason);
        schedule.setActive(false);
        schedule.setNextOccurrenceAt(null);
        recurrenceScheduleRepository.save(schedule);
    }

    private String resolveSubject(ReminderSourceType sourceType, UUID sourceId, UUID tenantId) {
        return switch (sourceType) {
            case TASK -> taskRepository.findByIdAndTenantIdAndDeletedFalse(sourceId, tenantId)
                .map(task -> task.getSubject())
                .orElse("Task");
            case CALL -> callRepository.findById(sourceId)
                .filter(call -> call.getTenantId().equals(tenantId))
                .map(call -> call.getSubject())
                .orElse("Call");
            case MEETING -> meetingRepository.findById(sourceId)
                .filter(meeting -> meeting.getTenantId().equals(tenantId))
                .map(meeting -> meeting.getSubject())
                .orElse("Meeting");
        };
    }

    private String resolveTenantTimezone(UUID tenantId) {
        return tenantRepository
            .findById(tenantId)
            .map(tenant -> tenant.getTimezone())
            .filter(timezone -> timezone != null && !timezone.isBlank())
            .orElse("UTC");
    }
}
