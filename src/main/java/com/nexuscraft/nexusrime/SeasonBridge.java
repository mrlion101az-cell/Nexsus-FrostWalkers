package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Asks NexusSeasons what season it is, by reflection so there is no compile-time dependency:
 * resolve {@code com.nexusuniverse.seasons.NexusSeasonsAPI}, take its service registration, call
 * {@code getCurrentSeason()} and read the enum's name. Any failure at all is UNKNOWN.
 */
class SeasonBridge {

    private static final String API = "com.nexusuniverse.seasons.NexusSeasonsAPI";

    SeasonStatus status() {
        try {
            Class<?> api = Class.forName(API);
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(api);
            if (reg == null) {
                return SeasonStatus.UNKNOWN;
            }
            Object season = api.getMethod("getCurrentSeason").invoke(reg.getProvider());
            if (season instanceof Enum<?> e) {
                return "WINTER".equals(e.name()) ? SeasonStatus.WINTER : SeasonStatus.NOT_WINTER;
            }
            return SeasonStatus.UNKNOWN;
        } catch (Throwable unavailable) {
            return SeasonStatus.UNKNOWN;
        }
    }
}
