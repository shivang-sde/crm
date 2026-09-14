package com.shivang.crm.modules.records.entity;

public enum MappingProfileMode {
    DIRECT,
    CUSTOM;

    public static MappingProfileMode fromString(String value) {
        if (value == null) throw new IllegalArgumentException("Mode is required");
        return MappingProfileMode.valueOf(value.trim().toUpperCase());
    }
}
