package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Zero-dependency bridge to NexusHeartbeat, same convention used across this ecosystem: dispatches
 * NexusHeartbeat's public console command if it's installed, a harmless no-op otherwise. No
 * compile-time dependency either way.
 */
public final class HeartbeatBridge {
    private HeartbeatBridge() {
    }

    public static void report(JavaPlugin plugin, String category, int weight, String summary) {
        if (plugin == null || summary == null || summary.isBlank()) {
            return;
        }
        String safeCategory = (category == null || category.isBlank()) ? "MISC" : category;
        String safeSummary = summary.replace("\n", " ").replace("\r", " ");
        Runnable dispatch = () -> {
            try {
                if (!Bukkit.getPluginManager().isPluginEnabled("NexusHeartbeat")) {
                    return;
                }
                String cmd = "heartbeat report " + safeCategory + " " + weight + " " + safeSummary;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Throwable ignored) {
                // Never let a missing/misbehaving NexusHeartbeat break the calling plugin.
            }
        };
        if (Bukkit.isPrimaryThread()) {
            dispatch.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, dispatch);
        }
    }
}
