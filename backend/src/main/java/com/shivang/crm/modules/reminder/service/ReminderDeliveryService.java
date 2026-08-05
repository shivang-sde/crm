package com.shivang.crm.modules.reminder.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.config.ReminderProperties;
import com.shivang.crm.modules.reminder.entity.NotificationType;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.entity.UserNotification;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;
import com.shivang.crm.modules.reminder.repository.UserNotificationRepository;

@Service
public class ReminderDeliveryService {

    private final ReminderRepository reminderRepository;
    private final UserNotificationRepository notificationRepository;
    private final ReminderProperties reminderProperties;

    public ReminderDeliveryService(
        ReminderRepository reminderRepository,
        UserNotificationRepository notificationRepository,
        ReminderProperties reminderProperties
    ) {
        this.reminderRepository = reminderRepository;
        this.notificationRepository = notificationRepository;
        this.reminderProperties = reminderProperties;
    }

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
}
