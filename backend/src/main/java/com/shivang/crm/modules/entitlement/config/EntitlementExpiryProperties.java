package com.shivang.crm.modules.entitlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.entitlements")
public class EntitlementExpiryProperties {

    private boolean expiryEnabled = true;
    private long expiryDelayMs = 86400000L;
    private boolean renewalTaskEnabled = true;
    private long renewalTaskDelayMs = 86400000L;

    public boolean isExpiryEnabled() {
        return expiryEnabled;
    }

    public void setExpiryEnabled(boolean expiryEnabled) {
        this.expiryEnabled = expiryEnabled;
    }

    public long getExpiryDelayMs() {
        return expiryDelayMs;
    }

    public void setExpiryDelayMs(long expiryDelayMs) {
        this.expiryDelayMs = expiryDelayMs;
    }

    public boolean isRenewalTaskEnabled() {
        return renewalTaskEnabled;
    }

    public void setRenewalTaskEnabled(boolean renewalTaskEnabled) {
        this.renewalTaskEnabled = renewalTaskEnabled;
    }

    public long getRenewalTaskDelayMs() {
        return renewalTaskDelayMs;
    }

    public void setRenewalTaskDelayMs(long renewalTaskDelayMs) {
        this.renewalTaskDelayMs = renewalTaskDelayMs;
    }
}
