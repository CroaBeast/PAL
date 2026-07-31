package com.bitaspire.pal.auth;

public interface PasswordPolicy {

    int getMinLength();

    int getMaxLength();

    boolean isRepeatRequired();

    boolean isUsernameAllowed();
}
