package com.shivang.crm.modules.records.entity;

public enum MappingTransform {
    IDENTITY,
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    ENUM;

    public static MappingTransform fromString(String value) {
        if (value == null || value.isBlank()) return IDENTITY;
        return MappingTransform.valueOf(value.trim().toUpperCase());
    }
}
