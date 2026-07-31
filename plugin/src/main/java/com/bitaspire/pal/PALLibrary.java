package com.bitaspire.pal;

import me.croabeast.takion.TakionLib;
import me.croabeast.takion.channel.Channel;
import me.croabeast.takion.logger.TakionLogger;
import me.croabeast.takion.message.MessageSender;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

final class PALLibrary extends TakionLib {

    private final PALPlugin plugin;

    PALLibrary(PALPlugin plugin) {
        super(plugin);
        this.plugin = plugin;

        getPlaceholderManager().edit("{playerDisplayName}", "{displayName}");
        getPlaceholderManager().edit("{playerUUID}", "{uuid}");
        getPlaceholderManager().edit("{playerAddress}", "{address}");

        Channel channel = getChannelManager().identify("action_bar");
        channel.addPrefix("actionbar");
        channel.addPrefix("action-bar");
    }

    void reload() {
        super.setServerLogger(new TakionLogger(this, false) {
            public boolean isColored() {
                return plugin.getConfiguration().isColoredConsole();
            }

            public boolean isStripPrefix() {
                return !plugin.getConfiguration().isShowPrefix();
            }
        });

        super.setLogger(new TakionLogger(this) {
            public boolean isColored() {
                return plugin.getConfiguration().isColoredConsole();
            }

            public boolean isStripPrefix() {
                return !plugin.getConfiguration().isShowPrefix();
            }
        });

        super.setLoadedSender(new MessageSender(this) {{
            setSensitive(false);
            setErrorPrefix("&c[X]&7 ");
        }});
    }

    @Override
    public void setServerLogger(TakionLogger logger) {
        throw new IllegalStateException("Server TakionLogger can not be set");
    }

    @Override
    public void setLogger(TakionLogger logger) {
        throw new IllegalStateException("TakionLogger can not be set");
    }

    @Override
    public void setLoadedSender(MessageSender loadedSender) {
        throw new IllegalStateException("MessageSender can not be set");
    }

    @NotNull
    public String getLangPrefixKey() {
        return plugin.getConfiguration().getPrefixKey();
    }

    @NotNull
    public String getLangPrefix() {
        return plugin.getConfiguration().getPrefix();
    }

    @NotNull
    public String getCenterPrefix() {
        return plugin.getConfiguration().getCenterPrefix();
    }

    @NotNull
    public String getLineSeparator() {
        return Pattern.quote(plugin.getConfiguration().getLineSeparator());
    }
}
