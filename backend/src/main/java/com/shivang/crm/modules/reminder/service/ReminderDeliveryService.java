package com.shivang.crm.modules.reminder.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.config.ReminderProperties;
import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.entity.UserNotification;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;
import com.shivang.crm.modules.reminder.repository.UserNotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class ReminderDeliveryService {

    private final ReminderRepository reminderRepository;
    private final UserNotificationRepository notificationRepository;
    private final ReminderProperties reminderProperties;
    private final MeetingAttendeeRecipientResolver meetingAttendeeRecipientResolver;

    @Transactional
    public void deliverReminders(List<UUID> reminderIds) {
        Instant now = Instant.now();
        List<Reminder> reminders = reminderRepository.findAllById(reminderIds);

        for (Reminder reminder : reminders) {
            if (reminder.getStatus() != ReminderStatus.PROCESSING) {
                continue;
            }

            try {
                Optional<UserNotification> existingNotification = notificationRepository
                    .findByReminderIdAndUserId(reminder.getId(), reminder.getResolvedRecipientUserId());

                if (existingNotification.isEmpty()) {
                    UserNotification notification = new UserNotification();
                    notification.setTenantId(reminder.getTenantId());
                    notification.setUserId(reminder.getResolvedRecipientUserId());
                    notification.setReminderId(reminder.getId());
                    notification.setNotificationType(NotificationType.REMINDER);
                    notification.setTitle(String.format("Reminder: %s", reminder.getTitle()));
                    notification.setMessage(reminder.getMessage());
                    notification.setMetadata(Map.of(
                        "sourceType", reminder.getSourceType().name(),
                        "sourceId", reminder.getSourceId().toString()
                    ));
                    notification.setIsRead(false);
                    notification.setCreatedAt(now);

                    notificationRepository.save(notification);
                }

                notifyMeetingAttendees(reminder, now);

                reminder.setStatus(ReminderStatus.SENT);
                reminder.setProcessedAt(now);
                reminder.setNextAttemptAt(null);
                reminder.setLastError(null);
                reminderRepository.save(reminder);
            } catch (Exception ex) {
                int nextAttemptCount = reminder.getAttemptCount() + 1;
                reminder.setAttemptCount(nextAttemptCount);
                if (nextAttemptCount >= reminderProperties.getMaxAttempts()) {
                    reminder.setStatus(ReminderStatus.FAILED);
                    reminder.setNextAttemptAt(null);
                } else {
                    reminder.setStatus(ReminderStatus.PENDING);
                    long delaySeconds = nextAttemptCount == 1 ? 30 : 120;
                    reminder.setNextAttemptAt(now.plusSeconds(delaySeconds));
                }
                reminder.setProcessedAt(now);
                reminder.setLastError(ex.getMessage());
                reminderRepository.save(reminder);
            }
        }
    }

    /**
     * Phase MEET-3: CRM-user attendees of a MEETING also receive the reminder
     * notification. External (non-CRM) attendee emails resolve to nothing and are
     * silently ignored; the owner recipient is unaffected either way.
     */
    private void notifyMeetingAttendees(Reminder reminder, Instant now) {
        if (reminder.getSourceType() != ReminderSourceType.MEETING) {
            return;
        }

        Set<UUID> attendeeUserIds = meetingAttendeeRecipientResolver.resolveRecipientUserIds(
            reminder.getTenantId(),
            reminder.getSourceId()
        );
        // The owner recipient is handled above; never duplicate it here.
        attendeeUserIds.remove(reminder.getResolvedRecipientUserId());

        for (UUID attendeeUserId : attendeeUserIds) {
            Optional<UserNotification> existingNotification = notificationRepository
                .findByReminderIdAndUserId(reminder.getId(), attendeeUserId);
            if (existingNotification.isPresent()) {
                continue;
            }

            UserNotification notification = new UserNotification();
            notification.setTenantId(reminder.getTenantId());
            notification.setUserId(attendeeUserId);
            notification.setReminderId(reminder.getId());
            notification.setNotificationType(NotificationType.REMINDER);
            notification.setTitle(String.format("Reminder: %s", reminder.getTitle()));
            notification.setMessage(reminder.getMessage());
            notification.setMetadata(Map.of(
                "sourceType", reminder.getSourceType().name(),
                "sourceId", reminder.getSourceId().toString()
            ));
            notification.setIsRead(false);
            notification.setCreatedAt(now);

            notificationRepository.save(notification);
        }
    }
}
