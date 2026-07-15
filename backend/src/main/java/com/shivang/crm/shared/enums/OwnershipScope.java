package com.shivang.crm.shared.enums;

public enum OwnershipScope {
    OWN, TEAM, ALL;

    public static OwnershipScope fromString(String value) {
        if (value == null) return null;
        try {
            return OwnershipScope.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid OwnershipScope: " + value);
        }
    }
}

