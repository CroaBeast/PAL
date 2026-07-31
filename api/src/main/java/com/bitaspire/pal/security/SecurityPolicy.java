package com.bitaspire.pal.security;

public interface SecurityPolicy {

    int getMaxLoginAttempts();

    int getLockoutSeconds();

    boolean isMovementBlockedBeforeAuth();

    boolean isCommandBlockedBeforeAuth();

    boolean isInventoryBlockedBeforeAuth();
}
