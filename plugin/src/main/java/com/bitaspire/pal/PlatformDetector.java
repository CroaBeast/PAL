package com.bitaspire.pal;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;

import java.util.Locale;

@UtilityClass
class PlatformDetector {

    static Platform detect() {
        String name = Bukkit.getName().toLowerCase(Locale.ROOT);
        String serverClass = Bukkit.getServer().getClass().getName().toLowerCase(Locale.ROOT);

        if (name.contains("folia") || serverClass.contains("folia") || serverClass.contains("threadedregions"))
            return Platform.FOLIA;
        if (name.contains("paper") || name.contains("purpur")) return Platform.PAPER;
        if (name.contains("spigot")) return Platform.SPIGOT;
        if (name.contains("bukkit")) return Platform.BUKKIT;
        return Platform.UNKNOWN;
    }
}
