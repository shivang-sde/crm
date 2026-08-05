package com.shivang.crm.modules.recurrence.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.recurrence.config.RecurrenceProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurrenceGenerationScheduler {

    private final RecurrenceGenerationService recurrenceGenerationService;
    private final RecurrenceProperties recurrenceProperties;

    @Scheduled(fixedDelayString = "${app.recurrence.generation-delay-ms}", initialDelayString = "${app.recurrence.generation-delay-ms}")
    public void generateRecurrenceReminders() {
        if (!recurrenceProperties.isEnabled()) {
            return;
        }

        int generated = recurrenceGenerationService.generateBatch(Instant.now(), recurrenceProperties.getBatchSize());
        if (generated > 0) {
            log.debug("Generated recurrence reminders for {} schedules", generated);
        }
    }
}
