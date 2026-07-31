package com.bitaspire.pal.proxy.identity;

import com.bitaspire.pal.proxy.connection.ConnectionRequest;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

final class FloodgateIdentitySupport {

    private FloodgateIdentitySupport() {}

    @NotNull
    static ConnectionRequest apply(@NotNull ConnectionRequest request) {
        if (request.getUniqueId() == null) return request;

        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null || !api.isFloodgatePlayer(request.getUniqueId())) return request;

            FloodgatePlayer player = api.getPlayer(request.getUniqueId());
            UUID uniqueId = request.getUniqueId();
            String name = request.getName();
            boolean linkedJava = false;

            if (player != null) {
                uniqueId = firstUniqueId(player.getCorrectUniqueId(), uniqueId);
                name = firstNonBlank(player.getCorrectUsername(), name);

                if (player.isLinked()) {
                    uniqueId = firstUniqueId(player.getJavaUniqueId(), uniqueId);
                    name = firstNonBlank(player.getJavaUsername(), name);
                    linkedJava = true;
                }
            }

            return request.toBuilder()
                    .uniqueId(uniqueId)
                    .name(name)
                    .bedrockVerified(true)
                    .linkedJava(linkedJava)
                    .build();
        } catch (NoClassDefFoundError ignored) {
            return request;
        } catch (Exception ignored) {
            return request;
        }
    }

    @NotNull
    private static UUID firstUniqueId(@Nullable UUID first, @NotNull UUID second) {
        return first == null ? second : first;
    }

    @NotNull
    private static String firstNonBlank(@Nullable String first, @NotNull String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }
}
