package com.nexusuniverse.realms.api;

import org.bukkit.Location;

import java.util.UUID;

/** Test copy of the NexusRealms methods NexusRime reads. */
public interface NexusRealmsApi {
    boolean canBuild(UUID playerId, Location location);

    UUID teamOwning(Location location);
}
