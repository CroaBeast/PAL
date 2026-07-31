package com.bitaspire.pal;

import com.bitaspire.pal.auth.AuthResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

final class AuthThrottle {

    private final Map<String, AttemptState> names = new HashMap<>();
    private final Map<String, AttemptState> addresses = new HashMap<>();

    synchronized AuthResult.Type check(@NotNull String name, @Nullable InetAddress address, @NotNull AuthOptions options) {
        if (!options.isBruteForce()) return null;

        long now = System.currentTimeMillis();
        purge(names, now);
        purge(addresses, now);

        AttemptState nameState = names.get(key(name));
        if (nameState != null && nameState.isLocked(now)) return AuthResult.Type.BLOCKED;
        if (nameState != null && nameState.nextAllowedAt > now) return AuthResult.Type.RATE_LIMITED;

        AttemptState addressState = address == null ? null : addresses.get(address.getHostAddress());
        if (addressState != null && addressState.isLocked(now)) return AuthResult.Type.BLOCKED;
        if (addressState != null && addressState.nextAllowedAt > now) return AuthResult.Type.RATE_LIMITED;

        return null;
    }

    synchronized void success(@NotNull String name, @Nullable InetAddress address) {
        names.remove(key(name));
        if (address != null) addresses.remove(address.getHostAddress());
    }

    synchronized void failure(@NotNull String name, @Nullable InetAddress address, @NotNull AuthOptions options) {
        if (!options.isBruteForce()) return;

        long now = System.currentTimeMillis();
        record(names, key(name), now, options.getMaxAttempts(), options.getWindowSeconds(),
                options.getLockoutSeconds(), options.getUsernameThrottleMillis());

        if (address != null) {
            record(addresses, address.getHostAddress(), now, options.getMaxAttempts(), options.getWindowSeconds(),
                    options.getLockoutSeconds(), options.getIpThrottleMillis());
        }
    }

    private void record(Map<String, AttemptState> states, String key, long now, int maxAttempts,
                        int windowSeconds, int lockoutSeconds, long throttleMillis) {
        AttemptState state = states.computeIfAbsent(key, ignored -> new AttemptState());
        if (state.windowStartedAt <= 0L || state.windowStartedAt + windowSeconds * 1000L < now) {
            state.windowStartedAt = now;
            state.failures = 0;
        }

        state.failures++;
        state.nextAllowedAt = now + throttleMillis;
        state.expiresAt = now + windowSeconds * 1000L;

        if (state.failures >= maxAttempts) {
            state.lockedUntil = now + lockoutSeconds * 1000L;
            state.expiresAt = state.lockedUntil;
        }
    }

    private void purge(Map<String, AttemptState> states, long now) {
        Iterator<AttemptState> iterator = states.values().iterator();
        while (iterator.hasNext()) {
            AttemptState state = iterator.next();
            if (state.expiresAt > 0L && state.expiresAt <= now) iterator.remove();
        }
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class AttemptState {
        private int failures;
        private long windowStartedAt;
        private long lockedUntil;
        private long nextAllowedAt;
        private long expiresAt;

        private boolean isLocked(long now) {
            return lockedUntil > now;
        }
    }
}
