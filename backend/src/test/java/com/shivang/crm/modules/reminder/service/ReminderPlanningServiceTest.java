package com.shivang.crm.modules.reminder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.modules.call.entity.Call.CallStatus;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.entity.TaskStatus;

class ReminderPlanningServiceTest {

    private ReminderRepository reminderRepository;
    private ReminderPlanningService reminderPlanningService;
    private UUID tenantId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        reminderRepository = Mockito.mock(ReminderRepository.class);
        reminderPlanningService = new ReminderPlanningService(reminderRepository);
        tenantId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
    }

    @Test
    void taskCreatesPendingReminder() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Call client")
            .dueDate(Instant.now().plusSeconds(3600))
            .remindAt(Instant.now().plusSeconds(1800))
            .status(TaskStatus.NOT_STARTED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.TASK, sourceId, task.getDueDate(), task.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.planForTask(task);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertEquals(ReminderStatus.PENDING, savedReminder.getStatus());
        assertEquals("Task reminder", savedReminder.getTitle());
        assertEquals("Task \"Call client\" is due soon.", savedReminder.getMessage());
        assertEquals(tenantId, savedReminder.getTenantId());
        assertEquals(sourceId, savedReminder.getSourceId());
    }

    @Test
    void callCreatesPendingReminder() {
        Call call = Call.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Sales call")
            .startTime(Instant.now().plusSeconds(3600))
            .remindAt(Instant.now().plusSeconds(1800))
            .status(CallStatus.PLANNED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.CALL, sourceId, call.getStartTime(), call.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.planForCall(call);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertEquals(ReminderStatus.PENDING, savedReminder.getStatus());
        assertEquals("Call reminder", savedReminder.getTitle());
        assertEquals("Scheduled call \"Sales call\" starts soon.", savedReminder.getMessage());
        assertEquals(ReminderSourceType.CALL, savedReminder.getSourceType());
    }

    @Test
    void meetingCreatesPendingReminder() {
        Meeting meeting = Meeting.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Team sync")
            .startTime(Instant.now().plusSeconds(3600))
            .remindAt(Instant.now().plusSeconds(1800))
            .status(Meeting.MeetingStatus.PLANNED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.MEETING, sourceId, meeting.getStartTime(), meeting.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.planForMeeting(meeting);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertEquals(ReminderStatus.PENDING, savedReminder.getStatus());
        assertEquals("Meeting reminder", savedReminder.getTitle());
        assertEquals("Meeting \"Team sync\" starts soon.", savedReminder.getMessage());
    }

    @Test
    void pastRemindAtCreatesSkippedReminder() {
        Instant now = Instant.now();
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Follow up")
            .dueDate(now.plusSeconds(3600))
            .remindAt(now.minusSeconds(10))
            .status(TaskStatus.NOT_STARTED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.TASK, sourceId, task.getDueDate(), task.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.planForTask(task);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());
        Reminder savedReminder = captor.getValue();

        assertEquals(ReminderStatus.SKIPPED, savedReminder.getStatus());
    }

    @Test
    void missingRemindAtCreatesNoReminder() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("No reminder")
            .dueDate(Instant.now().plusSeconds(3600))
            .status(TaskStatus.NOT_STARTED)
            .build();

        reminderPlanningService.planForTask(task);

        verify(reminderRepository, times(0)).save(any(Reminder.class));
    }

    @Test
    void remindAtAfterOccurrenceIsRejected() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Invalid reminder")
            .dueDate(Instant.now().plusSeconds(1800))
            .remindAt(Instant.now().plusSeconds(3600))
            .status(TaskStatus.NOT_STARTED)
            .build();

        assertThrows(BusinessException.class, () -> reminderPlanningService.planForTask(task));
        verify(reminderRepository, times(0)).save(any(Reminder.class));
    }

    @Test
    void duplicatePlanningCreatesNoDuplicate() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Duplicate")
            .dueDate(Instant.now().plusSeconds(3600))
            .remindAt(Instant.now().plusSeconds(1800))
            .status(TaskStatus.NOT_STARTED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.TASK, sourceId, task.getDueDate(), task.getRemindAt()
        )).thenReturn(true);

        reminderPlanningService.planForTask(task);
        verify(reminderRepository, times(0)).save(any(Reminder.class));
    }

    @Test
    void cancelPendingAndCreateOnUpdate() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Update reminder")
            .dueDate(Instant.now().plusSeconds(7200))
            .remindAt(Instant.now().plusSeconds(3600))
            .status(TaskStatus.NOT_STARTED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.TASK, sourceId, task.getDueDate(), task.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.cancelPending(tenantId, ReminderSourceType.TASK, sourceId);
        reminderPlanningService.planForTask(task);

        verify(reminderRepository, times(1)).cancelPendingByTenantIdAndSourceTypeAndSourceId(
            tenantId, ReminderSourceType.TASK, sourceId, ReminderStatus.PENDING, ReminderStatus.CANCELLED);
        verify(reminderRepository, times(1)).save(any(Reminder.class));
    }

    @Test
    void resolvedRecipientUserIdRemainsNull() {
        Task task = Task.builder()
            .tenantId(tenantId)
            .id(sourceId)
            .subject("Null recipient")
            .dueDate(Instant.now().plusSeconds(3600))
            .remindAt(Instant.now().plusSeconds(1800))
            .status(TaskStatus.NOT_STARTED)
            .build();

        when(reminderRepository.existsByTenantIdAndSourceTypeAndSourceIdAndOccurrenceAtAndScheduledAt(
            tenantId, ReminderSourceType.TASK, sourceId, task.getDueDate(), task.getRemindAt()
        )).thenReturn(false);

        reminderPlanningService.planForTask(task);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository, times(1)).save(captor.capture());
        assertEquals(null, captor.getValue().getResolvedRecipientUserId());
    }
}
