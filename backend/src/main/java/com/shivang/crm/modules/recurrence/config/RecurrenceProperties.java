package com.shivang.crm.modules.recurrence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recurrence")
public class RecurrenceProperties {

    private boolean enabled = true;
    private long generationDelayMs = 10000;
    private int generationWindowDays = 7;
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getGenerationDelayMs() {
        return generationDelayMs;
    }

    public void setGenerationDelayMs(long generationDelayMs) {
        this.generationDelayMs = generationDelayMs;
    }

    public int getGenerationWindowDays() {
        return generationWindowDays;
    }

    public void setGenerationWindowDays(int generationWindowDays) {
        this.generationWindowDays = generationWindowDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
