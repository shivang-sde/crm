package com.shivang.crm.modules.reminder.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.reminder.config.ReminderProperties;
import com.shivang.crm.modules.reminder.entity.Reminder;
import com.shivang.crm.modules.reminder.entity.ReminderStatus;
import com.shivang.crm.modules.reminder.repository.ReminderRepository;

@Service
public class ReminderClaimService {

    private final ReminderRepository reminderRepository;
    private final ReminderOwnerResolverRegistry reminderOwnerResolverRegistry;
    private final ReminderProperties reminderProperties;

    public ReminderClaimService(
        ReminderRepository reminderRepository,
        ReminderOwnerResolverRegistry reminderOwnerResolverRegistry,
        ReminderProperties reminderProperties
    ) {
        this.reminderRepository = reminderRepository;
        this.reminderOwnerResolverRegistry = reminderOwnerResolverRegistry;
        this.reminderProperties = reminderProperties;
    }

    @Transactional
    public List<UUID> claimDueReminders(int batchSize) {
        List<Reminder> reminders = reminderRepository.claimDueReminders(batchSize);
        List<UUID> claimedIds = new ArrayList<>();
        Instant now = Instant.now();

        for (Reminder reminder : reminders) {
            if (reminder.getResolvedRecipientUserId() == null) {
                reminderOwnerResolverRegistry.resolveOwner(
                    reminder.getSourceType(),
                    reminder.getTenantId(),
                    reminder.getSourceId()
                ).ifPresentOrElse(
                    ownerId -> reminder.setResolvedRecipientUserId(ownerId),
                    () -> {
                        int nextAttemptCount = reminder.getAttemptCount() + 1;
                        reminder.setAttemptCount(nextAttemptCount);
                        if (nextAttemptCount >= reminderProperties.getMaxAttempts()) {
                            reminder.setStatus(ReminderStatus.FAILED);
                            reminder.setProcessedAt(now);
                            reminder.setLastError(String.format("No owner found for %s:%s", reminder.getSourceType(), reminder.getSourceId()));
                        } else {
                            reminder.setStatus(ReminderStatus.PENDING);
                            reminder.setNextAttemptAt(now.plusSeconds(30));
                            reminder.setLastError(String.format("Owner resolution failed for %s:%s", reminder.getSourceType(), reminder.getSourceId()));
                        }
                    }
                );
            }

            if (reminder.getResolvedRecipientUserId() == null) {
                reminderRepository.save(reminder);
                continue;
            }

            reminder.setStatus(ReminderStatus.PROCESSING);
            reminder.setProcessingStartedAt(now);
            reminder.setAttemptCount(reminder.getAttemptCount() + 1);
            reminder.setNextAttemptAt(null);
            reminderRepository.save(reminder);
            claimedIds.add(reminder.getId());
        }

        return claimedIds;
    }
}
