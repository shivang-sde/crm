package com.shivang.crm.modules.reminder.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.entity.Call.CallStatus;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReminderPlanningService {

    private final ReminderRepository reminderRepository;

    public void planForTask(Task task) {
        validateTaskReminder(task);

        if (task.getDueDate() == null || task.getRemindAt() == null) {
            return;
        }

        if (task.isDeleted() || task.getStatus() == TaskStatus.COMPLETED) {
            return;
        }

        createReminderIfMissing(
            task.getTenantId(),
            ReminderSourceType.TASK,
            task.getId(),
            task.getDueDate(),
            task.getRemindAt(),
            "Task reminder",
            String.format("Task \"%s\" is due soon.", task.getSubject()),
            Map.of(
                "subject", task.getSubject(),
                "sourceType", ReminderSourceType.TASK.name(),
                "sourceId", task.getId().toString(),
                "occurrenceAt", task.getDueDate().toString()
            )
        );
    }

    public void planForCall(Call call) {
        validateCallReminder(call);

        if (call.getStartTime() == null || call.getRemindAt() == null) {
            return;
        }

        if (call.isDeleted() || call.getStatus() != CallStatus.PLANNED) {
            return;
        }

        createReminderIfMissing(
            call.getTenantId(),
            ReminderSourceType.CALL,
            call.getId(),
            call.getStartTime(),
            call.getRemindAt(),
            "Call reminder",
            String.format("Scheduled call \"%s\" starts soon.", call.getSubject()),
            Map.of(
                "subject", call.getSubject(),
                "sourceType", ReminderSourceType.CALL.name(),
                "sourceId", call.getId().toString(),
                "occurrenceAt", call.getStartTime().toString()
            )
        );
    }

    public void planForMeeting(Meeting meeting) {
        validateMeetingReminder(meeting);

        if (meeting.getStartTime() == null || meeting.getRemindAt() == null) {
            return;
        }

        if (meeting.isDeleted() || meeting.getStatus() != Meeting.MeetingStatus.PLANNED) {
            return;
        }

        createReminderIfMissing(
            meeting.getTenantId(),
            ReminderSourceType.MEETING,
            meeting.getId(),
            meeting.getStartTime(),
            meeting.getRemindAt(),
            "Meeting reminder",
            String.format("Meeting \"%s\" starts soon.", meeting.getSubject()),
            Map.of(
                "subject", meeting.getSubject(),
                "sourceType", ReminderSourceType.MEETING.name(),
                "sourceId", meeting.getId().toString(),
                "occurrenceAt", meeting.getStartTime().toString()
            )
        );
    }

    public void createRecurrenceReminderIfMissing(
        UUID tenantId,
        ReminderSourceType sourceType,
        UUID sourceId,
        Instant occurrenceAt,
        Long reminderOffsetSeconds,
        String subject
    ) {
        if (occurrenceAt == null || reminderOffsetSeconds == null) {
            return;
        }

        Instant scheduledAt = occurrenceAt.minusSeconds(reminderOffsetSeconds);
        String title = switch (sourceType) {
            case TASK -> "Task reminder";
            case CALL -> "Call reminder";
            case MEETING -> "Meeting reminder";
        };
        String message = switch (sourceType) {
            case TASK -> String.format("Task \"%s\" is due soon.", subject);
            case CALL -> String.format("Scheduled call \"%s\" starts soon.", subject);
            case MEETING -> String.format("Meeting \"%s\" starts soon.", subject);
        };
        Map<String, Object> metadata = Map.of(
            "subject", subject,
            "sourceType", sourceType.name(),
            "sourceId", sourceId.toString(),
            "occurrenceAt", occurrenceAt.toString()
        );

        createReminderIfMissing(
            tenantId,
            sourceType,
            sourceId,
            occurrenceAt,
            scheduledAt,
            title,
            message,
            metadata
        );
    }

    public void cancelPending(UUID tenantId, ReminderSourceType sourceType, UUID sourceId) {
        reminderRepository.cancelPendingByTenantIdAndSourceTypeAndSourceId(
            tenantId,
            sourceType,
            sourceId,
            ReminderStatus.PENDING,
            ReminderStatus.CANCELLED
        );
    }

    private void validateTaskReminder(Task task) {
        if (task.getRemindAt() != null && task.getDueDate() == null) {
            throw new BusinessException("INVALID_REMINDER", "Task remindAt requires a dueDate");
        }
        if (task.getRemindAt() != null && task.getDueDate() != null && task.getRemindAt().isAfter(task.getDueDate())) {
            throw new BusinessException("INVALID_REMINDER", "Task remindAt cannot be after dueDate");
        }
    }

    private void validateCallReminder(Call call) {
        if (call.getRemindAt() != null && call.getStartTime() == null) {
            throw new BusinessException("INVALID_REMINDER", "Call remindAt requires a startTime");
        }
        if (call.getRemindAt() != null && call.getStartTime() != null && call.getRemindAt().isAfter(call.getStartTime())) {
            throw new BusinessException("INVALID_REMINDER", "Call remindAt cannot be after startTime");
        }
    }

    private void validateMeetingReminder(Meeting meeting) {
        if (meeting.getRemindAt() != null && meeting.getStartTime() == null) {
            throw new BusinessException("INVALID_REMINDER", "Meeting remindAt requires a startTime");
        }
        if (meeting.getRemindAt() != null && meeting.getStartTime() != null && meeting.getRemindAt().isAfter(meeting.getStartTime())) {
            throw new BusinessException("INVALID_REMINDER", "Meeting remindAt cannot be after startTime");
        }
    }

    public void createReminderIfMissing(
        UUID tenantId,
        ReminderSourceType sourceType,
        UUID sourceId,
        Instant occurrenceAt,
        Instant scheduledAt,
        String title,
        String message,
        Map<String, Object> metadata
    ) {
        if (reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId,
            sourceType,
            sourceId,
            occurrenceAt,
            scheduledAt
        )) {
            return;
        }

        ReminderStatus status = scheduledAt.compareTo(Instant.now()) <= 0
            ? ReminderStatus.SKIPPED
            : ReminderStatus.PENDING;

        Reminder reminder = Reminder.builder()
            .tenantId(tenantId)
            .sourceType(sourceType)
            .sourceId(sourceId)
            .occurrenceAt(occurrenceAt)
            .scheduledAt(scheduledAt)
            .status(status)
            .resolvedRecipientUserId(null)
            .title(title)
            .message(message)
            .metadata(metadata)
            .processedAt(null)
            .lastError(null)
            .build();

        reminderRepository.save(reminder);
    }
}
