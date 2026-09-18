package com.shivang.crm.modules.commercial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.commercial-integration")
public class CommercialIntegrationProperties {
    /**
     * Feature flag for COMMERCIAL_INTEGRATION capability.
     * When false, all integration APIs return FEATURE_DISABLED.
     */
    private boolean enabled = false;
}
