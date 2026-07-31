package com.bitaspire.pal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.croabeast.file.ConfigurableFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class PreAuthOptions {

    private final int timeoutSeconds;
    private final boolean kickOnTimeout;
    @NotNull
    private final PreAuthRealm defaultRealm;
    @NotNull
    private final Map<PreAuthRealm, RealmOptions> realms;
    @NotNull
    private final Set<PotionEffectType> managedEffects;
    private final boolean roleMessages;
    @NotNull
    private final String roleSecret;
    private final int roleSkewSeconds;

    @NotNull
    static PreAuthOptions load(@NotNull PALPlugin plugin) {
        try {
            ConfigurableFile file = new ConfigurableFile(plugin, "security");
            file.saveDefaults();

            ConfigurableFile bridge = new ConfigurableFile(plugin, "bridge");
            bridge.saveDefaults();

            RestrictionOptions legacy = restrictions(file, "auth.pre-auth-restrictions", defaultCommands(), true);
            Map<PreAuthRealm, RealmOptions> realms = new EnumMap<>(PreAuthRealm.class);
            realms.put(PreAuthRealm.AUTH, realm(file, "auth", legacy, true));
            realms.put(PreAuthRealm.LOBBY, realm(file, "lobby", RestrictionOptions.open(defaultCommands()), false));
            realms.put(PreAuthRealm.LIMBO, realm(file, "limbo", RestrictionOptions.limbo(defaultCommands()), false));

            Set<PotionEffectType> managedEffects = new HashSet<>();
            for (RealmOptions options : realms.values()) {
                for (PotionEffect effect : options.getEffects()) managedEffects.add(effect.getType());
            }

            return new PreAuthOptions(
                    integer(file, "auth.login.timeout-seconds", 90),
                    bool(file, "auth.login.kick-on-timeout", true),
                    PreAuthRealm.from(string(file, "auth.realms.default", "AUTO"), PreAuthRealm.AUTO),
                    realms,
                    managedEffects,
                    bool(file, "auth.realms.proxy-message.enabled", true),
                    string(bridge, "bridge.sec.secret", "change-me"),
                    integer(bridge, "bridge.sec.skew", 15)
            );
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load pre-auth options: " + exception.getMessage());
            return defaults();
        }
    }

    @NotNull
    static PreAuthOptions defaults() {
        Map<PreAuthRealm, RealmOptions> realms = new EnumMap<>(PreAuthRealm.class);
        Set<String> allowed = defaultCommands();
        realms.put(PreAuthRealm.AUTH, new RealmOptions(SpawnOptions.disabled(), RestrictionOptions.auth(allowed), Collections.emptySet()));
        realms.put(PreAuthRealm.LOBBY, new RealmOptions(SpawnOptions.disabled(), RestrictionOptions.open(allowed), Collections.emptySet()));
        realms.put(PreAuthRealm.LIMBO, new RealmOptions(SpawnOptions.disabled(), RestrictionOptions.limbo(allowed), Collections.emptySet()));

        return new PreAuthOptions(
                90, true,
                PreAuthRealm.AUTO,
                realms,
                Collections.emptySet(),
                true,
                "change-me",
                15
        );
    }

    @NotNull
    RealmOptions realm(@NotNull PreAuthRealm realm) {
        RealmOptions options = realms.get(realm);
        return options == null ? realms.get(PreAuthRealm.AUTH) : options;
    }

    @NotNull
    private static Set<String> defaultCommands() {
        return new HashSet<>(Arrays.asList("login", "l", "register", "reg", "pal2fa", "2fa"));
    }

    @NotNull
    private static String clean(@NotNull String command) {
        String value = command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) value = value.substring(1);

        int space = value.indexOf(' ');
        if (space >= 0) value = value.substring(0, space);

        int namespace = value.indexOf(':');
        return namespace >= 0 ? value.substring(namespace + 1) : value;
    }

    private static boolean bool(ConfigurableFile file, String path, boolean defaultValue) {
        Object value = file.get(path, defaultValue);
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }

    @NotNull
    private static String string(ConfigurableFile file, String path, String defaultValue) {
        Object value = file.get(path, defaultValue);
        return value == null || String.valueOf(value).trim().isEmpty() ? defaultValue : String.valueOf(value);
    }

    private static int integer(ConfigurableFile file, String path, int defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return Math.max(0, ((Number) value).intValue());

        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @NotNull
    private static Set<String> stringSet(Object value) {
        Set<String> result = new HashSet<>();

        if (value instanceof Iterable) {
            for (Object entry : (Iterable<?>) value) {
                if (entry != null) result.add(String.valueOf(entry));
            }
            return result;
        }

        if (value instanceof String && !((String) value).trim().isEmpty()) {
            result.add((String) value);
        }

        return result;
    }

    @NotNull
    private static RealmOptions realm(
            @NotNull ConfigurableFile file,
            @NotNull String name,
            @NotNull RestrictionOptions fallbackRestrictions,
            boolean legacyAuth
    ) {
        String root = "auth.realms." + name;
        SpawnOptions spawn = spawn(file, root + ".spawn");
        RestrictionOptions restrictions = restrictions(file, root + ".restrictions", fallbackRestrictions);
        Set<PotionEffect> effects = effects(file.get(root + ".effects", Collections.emptyList()));

        if (legacyAuth && !hasAny(file, root + ".restrictions")) restrictions = fallbackRestrictions;
        return new RealmOptions(spawn, restrictions, effects);
    }

    @NotNull
    private static RestrictionOptions restrictions(
            @NotNull ConfigurableFile file,
            @NotNull String path,
            @NotNull Set<String> defaultAllowed,
            boolean defaultEnabled
    ) {
        return new RestrictionOptions(
                bool(file, path + ".movement", defaultEnabled),
                bool(file, path + ".chat", defaultEnabled),
                bool(file, path + ".commands", defaultEnabled),
                bool(file, path + ".inventory-click", defaultEnabled),
                bool(file, path + ".item-drop", defaultEnabled),
                bool(file, path + ".item-pickup", defaultEnabled),
                bool(file, path + ".block-place", defaultEnabled),
                bool(file, path + ".block-break", defaultEnabled),
                bool(file, path + ".damage", defaultEnabled),
                bool(file, path + ".teleport", defaultEnabled),
                allowedCommands(file.get(path + ".allowed-commands", defaultAllowed), defaultAllowed)
        );
    }

    @NotNull
    private static RestrictionOptions restrictions(
            @NotNull ConfigurableFile file,
            @NotNull String path,
            @NotNull RestrictionOptions fallback
    ) {
        return new RestrictionOptions(
                bool(file, path + ".movement", fallback.isMovement()),
                bool(file, path + ".chat", fallback.isChat()),
                bool(file, path + ".commands", fallback.isCommands()),
                bool(file, path + ".inventory-click", fallback.isInventory()),
                bool(file, path + ".item-drop", fallback.isItemDrop()),
                bool(file, path + ".item-pickup", fallback.isItemPickup()),
                bool(file, path + ".block-place", fallback.isBlockPlace()),
                bool(file, path + ".block-break", fallback.isBlockBreak()),
                bool(file, path + ".damage", fallback.isDamage()),
                bool(file, path + ".teleport", fallback.isTeleport()),
                allowedCommands(file.get(path + ".allowed-commands", fallback.getAllowedCommands()), fallback.getAllowedCommands())
        );
    }

    @NotNull
    private static Set<String> allowedCommands(Object value, @NotNull Set<String> fallback) {
        Set<String> allowed = new HashSet<>();
        for (String command : stringSet(value)) {
            if (command == null || command.trim().isEmpty()) continue;
            allowed.add(clean(command));
        }

        return allowed.isEmpty() ? new HashSet<>(fallback) : allowed;
    }

    @NotNull
    private static SpawnOptions spawn(@NotNull ConfigurableFile file, @NotNull String path) {
        return new SpawnOptions(
                bool(file, path + ".enabled", false),
                string(file, path + ".world", ""),
                decimal(file, path + ".x", 0.0D),
                decimal(file, path + ".y", 64.0D),
                decimal(file, path + ".z", 0.0D),
                (float) decimal(file, path + ".yaw", 0.0D),
                (float) decimal(file, path + ".pitch", 0.0D)
        );
    }

    private static double decimal(ConfigurableFile file, String path, double defaultValue) {
        Object value = file.get(path, defaultValue);
        if (value instanceof Number) return ((Number) value).doubleValue();

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @NotNull
    private static Set<PotionEffect> effects(Object value) {
        Set<PotionEffect> result = new HashSet<>();
        for (String entry : stringSet(value)) {
            PotionEffect effect = effect(entry);
            if (effect != null) result.add(effect);
        }
        return result;
    }

    @Nullable
    private static PotionEffect effect(@NotNull String entry) {
        String[] parts = entry.trim().split("[:;,]", -1);
        if (parts.length == 0 || parts[0].trim().isEmpty()) return null;

        PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
        if (type == null) return null;

        int amplifier = parts.length > 1 ? Math.max(0, integer(parts[1], 1) - 1) : 0;
        return new PotionEffect(type, Integer.MAX_VALUE, amplifier, true, false);
    }

    private static int integer(@NotNull String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static boolean hasAny(@NotNull ConfigurableFile file, @NotNull String path) {
        return file.get(path, null) != null;
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class RealmOptions {

        @NotNull
        private final SpawnOptions spawn;
        @NotNull
        private final RestrictionOptions restrictions;
        @NotNull
        private final Set<PotionEffect> effects;
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class SpawnOptions {

        private final boolean enabled;
        @NotNull
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        @NotNull
        static SpawnOptions disabled() {
            return new SpawnOptions(false, "", 0.0D, 64.0D, 0.0D, 0.0F, 0.0F);
        }

        @Nullable
        Location location(@NotNull PlayerHolder holder) {
            if (!enabled) return null;
            World targetWorld = world.trim().isEmpty() ? holder.world() : Bukkit.getWorld(world);
            return targetWorld == null ? null : new Location(targetWorld, x, y, z, yaw, pitch);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class RestrictionOptions {

        private final boolean movement;
        private final boolean chat;
        private final boolean commands;
        private final boolean inventory;
        private final boolean itemDrop;
        private final boolean itemPickup;
        private final boolean blockPlace;
        private final boolean blockBreak;
        private final boolean damage;
        private final boolean teleport;
        @NotNull
        private final Set<String> allowedCommands;

        @NotNull
        static RestrictionOptions auth(@NotNull Set<String> allowedCommands) {
            return new RestrictionOptions(true, true, true, true, true, true, true, true, true, true, allowedCommands);
        }

        @NotNull
        static RestrictionOptions open(@NotNull Set<String> allowedCommands) {
            return new RestrictionOptions(false, false, false, false, false, false, false, false, false, false, allowedCommands);
        }

        @NotNull
        static RestrictionOptions limbo(@NotNull Set<String> allowedCommands) {
            return new RestrictionOptions(true, false, false, true, true, true, true, true, true, true, allowedCommands);
        }

        boolean isCommandAllowed(@NotNull String command) {
            return allowedCommands.contains(clean(command));
        }
    }

    interface PlayerHolder {
        @NotNull
        World world();
    }
}
