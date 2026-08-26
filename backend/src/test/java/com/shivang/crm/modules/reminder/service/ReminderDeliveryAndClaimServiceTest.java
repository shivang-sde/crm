package com.shivang.crm.modules.reminder.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.shivang.crm.modules.reminder.config.ReminderProperties;
import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.entity.UserNotification;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;
import com.shivang.crm.modules.reminder.repository.UserNotificationRepository;

class ReminderDeliveryAndClaimServiceTest {

    private ReminderRepository reminderRepository;
    private UserNotificationRepository notificationRepository;
    private ReminderOwnerResolverRegistry ownerResolverRegistry;
    private ReminderClaimService claimService;
    private ReminderDeliveryService deliveryService;
    private ReminderProperties properties;
    private ReminderOwnerResolver ownerResolver;

    @BeforeEach
    void setUp() {
        reminderRepository = mock(ReminderRepository.class);
        notificationRepository = mock(UserNotificationRepository.class);
        properties = new ReminderProperties();
        properties.setMaxAttempts(3);
        ownerResolver = mock(ReminderOwnerResolver.class);
        when(ownerResolver.supportedType()).thenReturn(ReminderSourceType.TASK);
        when(ownerResolver.resolveOwner(any(UUID.class), any(UUID.class))).thenReturn(Optional.of(UUID.randomUUID()));
        ownerResolverRegistry = new ReminderOwnerResolverRegistry(List.of(ownerResolver));

        claimService = new ReminderClaimService(reminderRepository, ownerResolverRegistry, properties);
        deliveryService = new ReminderDeliveryService(reminderRepository, notificationRepository, properties, mock(MeetingAttendeeRecipientResolver.class));
    }

    @Test
    void claimDueRemindersUsesConfiguredBatchAndLockingQuery() {
        when(reminderRepository.claimDueReminders(100)).thenReturn(List.of());
        claimService.claimDueReminders(properties.getBatchSize());
        verify(reminderRepository).claimDueReminders(100);
    }

    @Test
    void deliveryDoesNotDuplicateNotificationWhenAlreadySent() {
        UUID reminderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
            .id(reminderId)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(userId)
            .status(ReminderStatus.PROCESSING)
            .title("Title")
            .message("Message")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();

        when(reminderRepository.findAllById(List.of(reminderId))).thenReturn(List.of(reminder));
        when(notificationRepository.findByReminderIdAndUserId(reminderId, userId)).thenReturn(Optional.of(new UserNotification()));

        deliveryService.deliverReminders(List.of(reminderId));

        assertEquals(ReminderStatus.SENT, reminder.getStatus());
        verify(notificationRepository).findByReminderIdAndUserId(reminderId, userId);
        verify(notificationRepository, never()).save(any(UserNotification.class));
    }

    @Test
    void deliveryRetriesTwiceThenFailsOnThirdAttempt() {
        UUID reminderId = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
            .id(reminderId)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(UUID.randomUUID())
            .status(ReminderStatus.PROCESSING)
            .attemptCount(0)
            .title("Title")
            .message("Message")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();

        when(reminderRepository.findAllById(List.of(reminderId))).thenReturn(List.of(reminder));
        when(notificationRepository.findByReminderIdAndUserId(reminderId, reminder.getResolvedRecipientUserId()))
            .thenReturn(Optional.empty());
        doThrow(new RuntimeException("delivery failure")).when(notificationRepository).save(any(UserNotification.class));

        deliveryService.deliverReminders(List.of(reminderId));
        assertEquals(1, reminder.getAttemptCount());
        assertEquals(ReminderStatus.PENDING, reminder.getStatus());
        assertTrue(reminder.getNextAttemptAt().isAfter(Instant.now()));

        reminder.setStatus(ReminderStatus.PROCESSING);
        deliveryService.deliverReminders(List.of(reminderId));
        assertEquals(2, reminder.getAttemptCount());
        assertEquals(ReminderStatus.PENDING, reminder.getStatus());
        assertTrue(reminder.getNextAttemptAt().isAfter(Instant.now()));

        reminder.setStatus(ReminderStatus.PROCESSING);
        deliveryService.deliverReminders(List.of(reminderId));
        assertEquals(3, reminder.getAttemptCount());
        assertEquals(ReminderStatus.FAILED, reminder.getStatus());
        assertFalse(reminder.getNextAttemptAt() != null && reminder.getNextAttemptAt().isAfter(Instant.now().plusSeconds(0)));
    }

    @Test
    void ownerResolutionUsesOriginalResolvedRecipientAfterRetry() {
        UUID reminderId = UUID.randomUUID();
        UUID originalOwner = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
            .id(reminderId)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(null)
            .status(ReminderStatus.PENDING)
            .attemptCount(0)
            .title("Title")
            .message("Message")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();

        when(reminderRepository.claimDueReminders(100)).thenReturn(List.of(reminder));
        when(ownerResolver.resolveOwner(reminder.getTenantId(), reminder.getSourceId())).thenReturn(Optional.of(originalOwner));

        claimService.claimDueReminders(100);
        assertEquals(originalOwner, reminder.getResolvedRecipientUserId());

        // owner changes in the repository now
        when(ownerResolver.resolveOwner(reminder.getTenantId(), reminder.getSourceId())).thenReturn(Optional.of(UUID.randomUUID()));

        reminder.setStatus(ReminderStatus.PENDING);
        reminder.setAttemptCount(0);
        claimService.claimDueReminders(100);
        assertEquals(originalOwner, reminder.getResolvedRecipientUserId());
    }

    @Test
    void deliveryFailureForOneReminderDoesNotStopRemainingBatch() {
        UUID reminderId1 = UUID.randomUUID();
        UUID reminderId2 = UUID.randomUUID();
        Reminder reminder1 = Reminder.builder()
            .id(reminderId1)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(UUID.randomUUID())
            .status(ReminderStatus.PROCESSING)
            .attemptCount(2)
            .title("Title1")
            .message("Message1")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();
        Reminder reminder2 = Reminder.builder()
            .id(reminderId2)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(UUID.randomUUID())
            .status(ReminderStatus.PROCESSING)
            .attemptCount(0)
            .title("Title2")
            .message("Message2")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();

        when(reminderRepository.findAllById(List.of(reminderId1, reminderId2))).thenReturn(List.of(reminder1, reminder2));
        when(notificationRepository.findByReminderIdAndUserId(reminderId1, reminder1.getResolvedRecipientUserId()))
            .thenReturn(Optional.empty());
        when(notificationRepository.findByReminderIdAndUserId(reminderId2, reminder2.getResolvedRecipientUserId()))
            .thenReturn(Optional.empty());
        doThrow(new RuntimeException("delivery failed for reminder1"))
            .when(notificationRepository).save(argThat(notification -> reminderId1.equals(notification.getReminderId())));

        deliveryService.deliverReminders(List.of(reminderId1, reminderId2));

        assertEquals(ReminderStatus.FAILED, reminder1.getStatus());
        assertEquals(ReminderStatus.SENT, reminder2.getStatus());
    }

    @Test
    void missingOwnerDoesNotClaimAndSchedulesRetry() {
        UUID reminderId = UUID.randomUUID();
        Reminder reminder = Reminder.builder()
            .id(reminderId)
            .tenantId(UUID.randomUUID())
            .resolvedRecipientUserId(null)
            .status(ReminderStatus.PENDING)
            .attemptCount(0)
            .title("Title")
            .message("Message")
            .sourceType(ReminderSourceType.TASK)
            .sourceId(UUID.randomUUID())
            .build();

        when(reminderRepository.claimDueReminders(100)).thenReturn(List.of(reminder));
        when(ownerResolver.resolveOwner(reminder.getTenantId(), reminder.getSourceId())).thenReturn(Optional.empty());

        List<UUID> claimed = claimService.claimDueReminders(100);

        assertTrue(claimed.isEmpty());
        assertEquals(ReminderStatus.PENDING, reminder.getStatus());
        assertEquals(1, reminder.getAttemptCount());
        assertTrue(reminder.getNextAttemptAt().isAfter(Instant.now()));
    }

    @Test
    void schedulerUsesPropertyPlaceholders() throws Exception {
        Method method = ReminderProcessingScheduler.class.getMethod("processDueReminders");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("${app.reminders.poll-delay-ms}", scheduled.fixedDelayString());
        assertEquals("${app.reminders.poll-delay-ms}", scheduled.initialDelayString());
    }
}
