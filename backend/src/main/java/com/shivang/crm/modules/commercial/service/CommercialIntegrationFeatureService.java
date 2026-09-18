package com.shivang.crm.modules.commercial.service;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.commercial.config.CommercialIntegrationProperties;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommercialIntegrationFeatureService {

    private final CommercialIntegrationProperties properties;

    public void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException("FEATURE_DISABLED",
                "Commercial integration is disabled for this deployment");
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
