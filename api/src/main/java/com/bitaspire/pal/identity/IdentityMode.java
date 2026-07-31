package com.bitaspire.pal.identity;

public enum IdentityMode {
    HYBRID,
    PREMIUM_ONLY,
    OFFLINE_ONLY;

    public static IdentityMode from(String value) {
        if (value == null) return HYBRID;

        try {
            return valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return HYBRID;
        }
    }
}
