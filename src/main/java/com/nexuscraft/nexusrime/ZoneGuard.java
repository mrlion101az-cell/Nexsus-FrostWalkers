package com.nexuscraft.nexusrime;

import org.bukkit.Location;
import org.bukkit.World;

/** "Is this spot safe ground?" -- built-in protected worlds and the spawn radius, plus any land
 *  claim NexusRealms reports. One place so the horde, the targeting and the damage guard agree. */
final class ZoneGuard {

    private final RimeConfig cfg;
    private final RealmsBridge realms;

    ZoneGuard(RimeConfig cfg, RealmsBridge realms) {
        this.cfg = cfg;
        this.realms = realms;
    }

    boolean isProtected(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        World world = at.getWorld();
        Location spawn = world.getSpawnLocation();
        double sx = spawn == null ? 0.0 : spawn.getX();
        double sz = spawn == null ? 0.0 : spawn.getZ();
        if (ProtectedZones.isProtected(cfg, world.getName(), at.getX(), at.getZ(), sx, sz)) {
            return true;
        }
        return cfg.useRealms && realms.isClaimed(at);
    }
}
