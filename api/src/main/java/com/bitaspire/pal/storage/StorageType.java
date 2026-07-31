package com.bitaspire.pal.storage;

public enum StorageType {
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL,
    CUSTOM;

    public static StorageType from(String value) {
        if (value == null) return SQLITE;

        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return SQLITE;
        }
    }
}
