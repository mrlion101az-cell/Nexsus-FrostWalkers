package com.nexuscraft.nexusrime;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired just before a horde appears. Cancel it to stop that horde: any other plugin (an arena, a
 * quest, a safe zone of its own) can listen and veto.
 */
public final class HordeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final Player target;
    private boolean cancelled;

    HordeEvent(World world, Player target) {
        this.world = world;
        this.target = target;
    }

    public World getWorld() {
        return world;
    }

    /** The player the horde is marching on. */
    public Player getTarget() {
        return target;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
