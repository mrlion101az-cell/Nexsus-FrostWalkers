package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public final class NexusRimePlugin extends JavaPlugin {

    private RimeConfig cfg;
    private Keys keys;
    private RimeItems items;
    private HordeDirector director;
    private SeasonBridge seasons;
    private RealmsBridge realms;
    private BukkitTask stepTask;
    private BukkitTask rollTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.cfg = new RimeConfig();
        cfg.load(getConfig());
        this.keys = new Keys(this);
        this.items = new RimeItems(keys, cfg);
        this.seasons = new SeasonBridge();
        this.realms = new RealmsBridge(cfg);
        ZoneGuard zones = new ZoneGuard(cfg, realms);
        getDataFolder().mkdirs();
        BridgeLedger ledger = new BridgeLedger(new File(getDataFolder(), "bridges.yml"), getLogger()::warning, System::currentTimeMillis);
        ledger.load();
        int thawed = ledger.restoreAll(); // a bridge left by a crash: nothing is walking on it now
        ledger.flush();
        if (thawed > 0) {
            getLogger().info("Thawed " + thawed + " frost bridge block(s) left from before the restart.");
        }
        Random random = new Random();
        this.director = new HordeDirector(this, cfg, keys, items, zones, seasons, System::currentTimeMillis, random, this::saveState, ledger);
        director.setLastEventMillis(loadState());

        getServer().getPluginManager().registerEvents(new RimeListener(cfg, director, items, zones, keys), this);
        getServer().getPluginManager().registerEvents(new ObsidianListener(cfg, items, random), this);
        RimeCommand command = new RimeCommand(cfg, director, items, seasons, realms, System::currentTimeMillis, this::reload);
        getCommand("rime").setExecutor(command);
        getCommand("rime").setTabCompleter(command);

        registerRecipe();
        schedule();
        sweepLoadedWorlds();
        getLogger().info("NexusRime enabled -- " + cfg.walkerName + " walks only in winter (season plugin: "
                + seasons.status().name().toLowerCase() + ", claims: " + (realms.available() ? "NexusRealms" : "spawn only") + ").");
    }

    @Override
    public void onDisable() {
        if (stepTask != null) {
            stepTask.cancel();
        }
        if (rollTask != null) {
            rollTask.cancel();
        }
        if (director != null) {
            director.shutdown();
            director.ledger().restoreAll();
            director.ledger().flush();
        }
        if (keys != null) {
            removeRecipes();
        }
    }

    private void schedule() {
        if (stepTask != null) {
            stepTask.cancel();
        }
        if (rollTask != null) {
            rollTask.cancel();
        }
        stepTask = getServer().getScheduler().runTaskTimer(this, director, 20L, 20L);
        long period = cfg.checkIntervalMinutes * 1200L;
        rollTask = getServer().getScheduler().runTaskTimer(this, director::roll, Math.min(period, 1200L), period);
    }

    private void reload() {
        reloadConfig();
        cfg.load(getConfig());
        registerRecipe();
        schedule();
    }

    /** Clears any horde creature left over from a crash or restart. */
    private void sweepLoadedWorlds() {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            removed += director.sweepOrphans(w.getEntities());
        }
        if (removed > 0) {
            getLogger().info("Cleared " + removed + " left-over horde creature(s).");
        }
    }

    private void removeRecipes() {
        for (String id : RimeConfig.GEAR_IDS) {
            Bukkit.removeRecipe(keys.recipe(id));
        }
    }

    private void registerRecipe() {
        removeRecipes();
        for (Recipe recipe : RecipeBook.build(cfg, items, keys, getLogger()::warning)) {
            try {
                Bukkit.addRecipe(recipe);
            } catch (IllegalArgumentException bad) {
                getLogger().warning("Could not register a dragonglass recipe (" + bad.getMessage() + ") -- check config.yml.");
            }
        }
    }

    // ------------------------------------------------------------------ the one remembered number

    private long loadState() {
        File file = new File(getDataFolder(), "state.yml");
        if (!file.exists()) {
            return 0L;
        }
        return YamlConfiguration.loadConfiguration(file).getLong("last-event-millis", 0L);
    }

    private void saveState() {
        getDataFolder().mkdirs();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("last-event-millis", director.lastEventMillis());
        try {
            yml.save(new File(getDataFolder(), "state.yml"));
        } catch (IOException ex) {
            getLogger().warning("Failed to save state.yml: " + ex.getMessage());
        }
    }
}
