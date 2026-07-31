package com.bitaspire.pal.event;

import com.bitaspire.pal.account.PALAccount;
import com.bitaspire.pal.session.AuthSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public final class PALSessionCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @NotNull
    private final PALAccount account;

    @NotNull
    private final AuthSession session;

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
