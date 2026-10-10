package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Shared test doubles: a world with positions, players and a map-backed config section. */
final class Kit {

    static int checks;
    static int failures;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static void finish() {
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            System.exit(1);
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    static final class TestPlugin extends JavaPlugin {
    }

    static final World WORLD = new World("world");
    static final World NETHER = new World("world_nether");

    static final class TestPlayer extends Player {
        final String name;
        final List<String> messages = new ArrayList<>();
        final Set<String> perms = new HashSet<>();
        Location loc;
        GameMode mode = GameMode.SURVIVAL;

        TestPlayer(String name, double x, double z) {
            this.name = name;
            this.loc = new Location(WORLD, x, 64, z);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Location getLocation() {
            return loc;
        }

        @Override
        public boolean teleport(Location l) {
            loc = l;
            return true;
        }

        @Override
        public void sendMessage(String m) {
            messages.add(m);
        }

        @Override
        public boolean hasPermission(String node) {
            return perms.contains(node);
        }

        @Override
        public GameMode getGameMode() {
            return mode;
        }

        boolean heard(String fragment) {
            for (String m : messages) {
                if (m.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        String lastMessage() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
        }
    }

    static TestPlayer online(TestPlayer p) {
        Bukkit.PLAYERS.put(p.getUniqueId(), p);
        Bukkit.ENTITIES.put(p.getUniqueId(), p);
        return p;
    }

    static void reset() {
        Bukkit.PLAYERS.clear();
        Bukkit.ENTITIES.clear();
        Bukkit.DISPATCHED.clear();
        Bukkit.WORLDS.clear();
        Bukkit.WORLDS.put("world", WORLD);
        Bukkit.WORLDS.put("world_nether", NETHER);
        org.bukkit.plugin.PluginManager.HOOK = null;
        org.bukkit.plugin.PluginManager.ENABLED.clear();
        WORLD.heightMap = null;
        WORLD.chunkLoaded = null;
        WORLD.setStorm(false);
        WORLD.stubClearBlocks();
    }

    /** A map-backed ConfigurationSection so config parsing can be tested with real values. */
    static final class MapSection implements ConfigurationSection {
        final Map<String, Object> values = new HashMap<>();

        MapSection put(String path, Object v) {
            values.put(path, v);
            return this;
        }

        @Override public boolean getBoolean(String path) { return getBoolean(path, false); }
        @Override public boolean getBoolean(String path, boolean def) { return values.get(path) instanceof Boolean b ? b : def; }
        @Override public int getInt(String path) { return getInt(path, 0); }
        @Override public int getInt(String path, int def) { return values.get(path) instanceof Number n ? n.intValue() : def; }
        @Override public long getLong(String path) { return getLong(path, 0L); }
        @Override public long getLong(String path, long def) { return values.get(path) instanceof Number n ? n.longValue() : def; }
        @Override public double getDouble(String path) { return getDouble(path, 0.0); }
        @Override public double getDouble(String path, double def) { return values.get(path) instanceof Number n ? n.doubleValue() : def; }
        @Override public String getString(String path) { return getString(path, null); }
        @Override public String getString(String path, String def) { return values.get(path) instanceof String s ? s : def; }
        @Override @SuppressWarnings("unchecked") public List<String> getStringList(String path) {
            return values.get(path) instanceof List<?> l ? (List<String>) l : new ArrayList<>();
        }
        @Override @SuppressWarnings("unchecked") public List<Integer> getIntegerList(String path) {
            return values.get(path) instanceof List<?> l ? (List<Integer>) l : new ArrayList<>();
        }
        @Override public List<Double> getDoubleList(String path) { return new ArrayList<>(); }
        @Override public List<?> getList(String path) { return new ArrayList<>(); }
        @Override public List<Map<?, ?>> getMapList(String path) { return new ArrayList<>(); }
        @Override public ItemStack getItemStack(String path) { return null; }
        @Override public ItemStack getItemStack(String path, ItemStack def) { return def; }
        @Override public ConfigurationSection getConfigurationSection(String path) {
            MapSection sub = new MapSection();
            String prefix = path + ".";
            boolean any = false;
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getKey().startsWith(prefix)) {
                    sub.values.put(e.getKey().substring(prefix.length()), e.getValue());
                    any = true;
                }
            }
            return any ? sub : null;
        }
        @Override public ConfigurationSection createSection(String path) { return new MapSection(); }
        @Override public void set(String path, Object value) { values.put(path, value); }
        @Override public Set<String> getKeys(boolean deep) {
            Set<String> keys = new LinkedHashSet<>();
            for (String k : values.keySet()) {
                keys.add(deep || !k.contains(".") ? k : k.substring(0, k.indexOf('.')));
            }
            return keys;
        }
        @Override public boolean isSet(String path) { return values.containsKey(path); }
        @Override public boolean contains(String path) { return values.containsKey(path); }
    }
}
