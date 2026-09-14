package com.shivang.crm.modules.records.entity;

public enum RecordFieldType {
    TEXT,
    LONG_TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    DATE,
    DATETIME,
    ENUM,
    URL,
    PHONE,
    EMAIL,
    JSON,
    REFERENCE;

    public static RecordFieldType fromString(String value) {
        if (value == null) throw new IllegalArgumentException("Field type is required");
        return RecordFieldType.valueOf(value.trim().toUpperCase());
    }
}
