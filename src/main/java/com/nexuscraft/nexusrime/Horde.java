package com.nexuscraft.nexusrime;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One horde in progress: the walker, the wights it raised, who it hunts, and its timers. */
final class Horde {
    final UUID id = UUID.randomUUID();
    final String worldName;
    final long startedMillis;
    UUID walker;
    UUID target;
    /** Where the walker was last seen, to tell "gone" from "in an unloaded chunk". */
    Location walkerLastSeen;
    String targetName;
    final Set<UUID> wights = new LinkedHashSet<>();
    /** Where each wight was last seen, to tell "gone" from "in an unloaded chunk". */
    final Map<UUID, Location> lastSeen = new HashMap<>();
    long ticksLived;
    long nextRaiseTick;
    long nextNovaTick;
    long nextColdTick;
    int ticksWithoutTarget;
    int ticksWalkerMissing;
    int playersLost;
    /** Started by an admin: exempt from the season check so it can be tested out of season. */
    boolean forced;
    /** Led by a Frost Knight rather than a plain walker. */
    boolean knight;
    /** The Knight's thinking, once he has started to lead. */
    KnightMind mind;
    BossBar bar;

    Horde(String worldName, long startedMillis) {
        this.worldName = worldName;
        this.startedMillis = startedMillis;
    }
}
