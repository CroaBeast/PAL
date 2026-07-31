package com.bitaspire.pal.integration;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

public interface IntegrationManager {

    @NotNull
    Collection<Integration> getIntegrations();

    @NotNull
    Optional<Integration> getIntegration(@NotNull Integration.Type integration);

    boolean isEnabled(@NotNull Integration.Type integration);
}
