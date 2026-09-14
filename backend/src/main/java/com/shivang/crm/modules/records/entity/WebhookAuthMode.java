package com.shivang.crm.modules.records.entity;

public enum WebhookAuthMode {
    NONE,
    API_KEY,
    HMAC_SHA256;

    public static WebhookAuthMode fromString(String value) {
        if (value == null || value.isBlank()) return NONE;
        return WebhookAuthMode.valueOf(value.trim().toUpperCase());
    }
}
