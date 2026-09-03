package com.shivang.crm.modules.acquisition.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shivang.crm.modules.acquisition.config.LeadIngestionConfig;
import com.shivang.crm.modules.acquisition.config.LeadIngestionTransportType;
import com.shivang.crm.modules.acquisition.repository.LeadIngestionConfigRepository;
import com.shivang.crm.modules.acquisition.service.LeadIngestionPollingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PollingScheduler {

    private final LeadIngestionConfigRepository configRepository;
    private final LeadIngestionPollingService pollingService;

    @Value("${app.polling.enabled:true}")
    private boolean enabled;

    @Value("${app.polling.interval-ms:300000}")
    private long intervalMs;

    @Scheduled(fixedDelayString = "${app.polling.interval-ms:300000}")
    public void pollDueSources() {
        if (!enabled) return;
        List<LeadIngestionConfig> configs = configRepository.findByTransportTypeAndActiveTrueAndDeletedFalse(LeadIngestionTransportType.POLLING);
        for (LeadIngestionConfig config : configs) {
            try {
                if (!isDue(config)) continue;
                log.info("Polling scheduled for config {} tenant {}", config.getId(), config.getTenantId());
                // Use tenant's system actor? For now use tenantId as actor
                pollingService.pollNow(config.getTenantId(), config.getId(), config.getTenantId());
                log.info("Polling completed for config {}", config.getId());
            } catch (Exception ex) {
                log.error("Polling failed for config {} tenant {}", config.getId(), config.getTenantId(), ex);
            }
        }
    }

    private boolean isDue(LeadIngestionConfig config) {
        Map<String, Object> settings = config.getSettings();
        if (settings == null) return true;
        Object pollingObj = settings.get("polling");
        if (!(pollingObj instanceof Map<?,?> polling)) return true;
        Object intervalObj = polling.get("intervalMinutes");
        int intervalMinutes = 15; // default
        if (intervalObj != null) {
            try { intervalMinutes = Integer.parseInt(String.valueOf(intervalObj)); } catch (Exception ignored) {}
        }
        // Check lastPollAt
        Object pollingStateObj = settings.get("pollingState");
        if (pollingStateObj instanceof Map<?,?> state) {
            Object lastPollAtObj = state.get("lastPollAt");
            if (lastPollAtObj != null) {
                try {
                    Instant lastPollAt = Instant.parse(String.valueOf(lastPollAtObj));
                    Instant nextDue = lastPollAt.plusSeconds((long) intervalMinutes * 60);
                    return Instant.now().isAfter(nextDue);
                } catch (Exception ignored) {}
            }
            // If currently running, skip
            Object running = state.get("pollingRunning");
            if (Boolean.TRUE.equals(running)) {
                Object startedAt = state.get("pollingStartedAt");
                if (startedAt != null) {
                    try {
                        Instant started = Instant.parse(String.valueOf(startedAt));
                        // If running for >10 minutes, consider stale and allow again
                        if (Instant.now().isBefore(started.plusSeconds(600))) {
                            return false;
                        }
                    } catch (Exception ignored) {}
                } else {
                    return false;
                }
            }
        }
        return true;
    }
}
