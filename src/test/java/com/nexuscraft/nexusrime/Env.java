package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;

import java.util.Random;
import java.util.function.Predicate;

/** One fresh, fully wired horde director per call, with the season, claims and clock under test control. */
final class Env {

    static final long MIN = 60_000L;

    static final class FakeSeason extends SeasonBridge {
        SeasonStatus value = SeasonStatus.WINTER;

        @Override
        SeasonStatus status() {
            return value;
        }
    }

    static final class FakeRealms extends RealmsBridge {
        Predicate<Location> claimed = l -> false;
        boolean present = true;

        FakeRealms(RimeConfig cfg) {
            super(cfg);
        }

        @Override
        boolean available() {
            return present;
        }

        @Override
        boolean isClaimed(Location location) {
            return present && claimed.test(location);
        }
    }

    final Kit.TestPlugin plugin = new Kit.TestPlugin();
    final RimeConfig cfg = new RimeConfig();
    final Keys keys = new Keys(plugin);
    final FakeSeason season = new FakeSeason();
    final FakeRealms realms = new FakeRealms(cfg);
    final ZoneGuard zones = new ZoneGuard(cfg, realms);
    final RimeItems items = new RimeItems(keys, cfg);
    final long[] now = {10_000 * MIN};
    final int[] saves = {0};
    final HordeDirector director;
    final RimeListener listener;
    /** Where the director's ice ledger is saved; it does not exist until something is written. */
    final java.io.File ledgerFile;
    final BridgeLedger ledger;

    Env() {
        Kit.reset();
        cfg.knightChance = 0.0; // the older tests are about plain walkers; knight tests ask for one

        PluginManager.ENABLED.add("NexusHeartbeat");
        java.io.File file;
        try {
            file = java.io.File.createTempFile("rime-bridges", ".yml");
            file.deleteOnExit();
            file.delete();
        } catch (java.io.IOException cannot) {
            throw new IllegalStateException(cannot);
        }
        ledgerFile = file;
        ledger = new BridgeLedger(ledgerFile, w -> { }, () -> now[0]);
        director = new HordeDirector(plugin, cfg, keys, items, zones, season, () -> now[0], new Random(5), () -> saves[0]++, ledger);
        listener = new RimeListener(cfg, director, items, zones, keys);
    }

    Kit.TestPlayer player(String name, double x, double z) {
        return Kit.online(new Kit.TestPlayer(name, x, z));
    }

    /** Number of non-player entities currently alive in the stub world. */
    static int creatures() {
        int n = 0;
        for (Entity e : Bukkit.ENTITIES.values()) {
            if (!(e instanceof org.bukkit.entity.Player)) {
                n++;
            }
        }
        return n;
    }

    static boolean dispatched(String fragment) {
        for (String c : Bukkit.DISPATCHED) {
            if (c.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /** Runs the one-second loop {@code n} times. */
    void tick(int n) {
        for (int i = 0; i < n; i++) {
            director.run();
        }
    }

    Entity walkerOf(Horde h) {
        return Bukkit.getEntity(h.walker);
    }
}
