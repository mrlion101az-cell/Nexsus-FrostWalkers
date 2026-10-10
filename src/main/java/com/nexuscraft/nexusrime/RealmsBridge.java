package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Asks NexusRealms whether a spot is claimed, by reflection (no compile-time dependency).
 *
 * <p>Two questions can be put, and {@code zones.realms-check} picks which count:
 * <ul>
 *   <li><b>territory</b>: {@code teamOwning(location)} -- does a team own the chunk?</li>
 *   <li><b>build</b>: {@code canBuild(stranger, location)} -- a made-up id that belongs to no team
 *   can build only in open, unclaimed terrain, so "cannot build" means a claim covers the spot
 *   (this also catches personal claims).</li>
 *   <li><b>both</b> (default): either one says claimed.</li>
 * </ul>
 */
class RealmsBridge {

    private static final String API = "com.nexusuniverse.realms.api.NexusRealmsApi";
    /** Belongs to no one, so it is trusted nowhere. */
    static final UUID STRANGER = new UUID(0L, 0L);

    private final RimeConfig cfg;
    private Method canBuild;
    private Method teamOwning;
    private Object provider;

    RealmsBridge(RimeConfig cfg) {
        this.cfg = cfg;
    }

    /** True when NexusRealms is present and answering. */
    boolean available() {
        resolve();
        return provider != null;
    }

    /** True when a claim covers the spot; false when it is open ground or Realms is missing. */
    boolean isClaimed(Location location) {
        resolve();
        if (provider == null || location == null) {
            return false;
        }
        String mode = cfg.realmsCheck;
        if (!"build".equals(mode) && teamOwning != null) {
            try {
                if (teamOwning.invoke(provider, location) != null) {
                    return true;
                }
            } catch (Throwable broken) {
                // treat as "no answer" and fall through
            }
        }
        if (!"territory".equals(mode) && canBuild != null) {
            try {
                Object open = canBuild.invoke(provider, STRANGER, location);
                return open instanceof Boolean b && !b;
            } catch (Throwable broken) {
                return false;
            }
        }
        return false;
    }

    /** Looked up lazily and again after a miss, so load order never matters. */
    private void resolve() {
        if (provider != null) {
            return;
        }
        try {
            Class<?> api = Class.forName(API);
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(api);
            if (reg == null) {
                return;
            }
            Object found = reg.getProvider();
            Method build = api.getMethod("canBuild", UUID.class, Location.class);
            Method owner = null;
            try {
                owner = api.getMethod("teamOwning", Location.class);
            } catch (NoSuchMethodException older) {
                // an older NexusRealms: the build check alone will do
            }
            canBuild = build;
            teamOwning = owner;
            provider = found;
        } catch (Throwable unavailable) {
            provider = null;
        }
    }
}
