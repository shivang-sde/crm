package com.shivang.crm.modules.reminder.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.reminder.config.ReminderProperties;

@Component
public class ReminderProcessingScheduler {

    private final ReminderClaimService reminderClaimService;
    private final ReminderDeliveryService reminderDeliveryService;
    private final ReminderProperties reminderProperties;

    public ReminderProcessingScheduler(
        ReminderClaimService reminderClaimService,
        ReminderDeliveryService reminderDeliveryService,
        ReminderProperties reminderProperties
    ) {
        this.reminderClaimService = reminderClaimService;
        this.reminderDeliveryService = reminderDeliveryService;
        this.reminderProperties = reminderProperties;
    }

    @Scheduled(fixedDelayString = "${app.reminders.poll-delay-ms}", initialDelayString = "${app.reminders.poll-delay-ms}")
    public void processDueReminders() {
        if (!reminderProperties.isEnabled()) {
            return;
        }

        List<java.util.UUID> claimedReminders = reminderClaimService.claimDueReminders(reminderProperties.getBatchSize());
        if (!claimedReminders.isEmpty()) {
            reminderDeliveryService.deliverReminders(claimedReminders);
        }
    }
}
