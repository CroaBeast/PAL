package com.bitaspire.pal.migration;

public interface MigrationResult {

    boolean isSuccessful();

    int getAccounts();

    int getSessions();
}
