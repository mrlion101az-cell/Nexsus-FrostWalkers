package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Remembers every block of liquid the Frost Knight froze into a bridge, so each one can be turned
 * back: when its time runs out, when its horde ends, and after a crash or restart (the list is kept
 * in bridges.yml). A block that someone has since broken or built over is left alone.
 */
final class BridgeLedger {

    /** How long past its expiry a bridge is held while someone is standing on it. */
    static final long GRACE_MILLIS = 120_000L;

    /** One frozen block. */
    record Entry(String world, int x, int y, int z, String original, String placed, long expiresMillis, String horde) {

        String encode() {
            return world + ";" + x + ";" + y + ";" + z + ";" + original + ";" + placed + ";" + expiresMillis + ";" + horde;
        }

        static Entry decode(String raw) {
            if (raw == null) {
                return null;
            }
            String[] p = raw.split(";");
            if (p.length != 8) {
                return null;
            }
            try {
                return new Entry(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4], p[5],
                        Long.parseLong(p[6]), p[7]);
            } catch (NumberFormatException bad) {
                return null;
            }
        }
    }

    private final File file;
    private final Consumer<String> warn;
    private final LongSupplier clock;
    private final List<Entry> entries = new ArrayList<>();
    private boolean dirty;

    BridgeLedger(File file, Consumer<String> warn, LongSupplier clock) {
        this.file = file;
        this.warn = warn;
        this.clock = clock;
    }

    int size() {
        return entries.size();
    }

    List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    boolean dirty() {
        return dirty;
    }

    /** Reads bridges.yml (anything unreadable is skipped with a warning). */
    void load() {
        entries.clear();
        dirty = false;
        if (file == null || !file.exists()) {
            return;
        }
        for (String raw : YamlConfiguration.loadConfiguration(file).getStringList("bridges")) {
            Entry e = Entry.decode(raw);
            if (e == null) {
                warn.accept("bridges.yml: could not read '" + raw + "' -- skipped.");
            } else {
                entries.add(e);
            }
        }
    }

    void flush() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(e.encode());
        }
        yml.set("bridges", out);
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            warn.accept("Could not save bridges.yml: " + ex.getMessage());
        }
    }

    /** Is there a bridge block here right now? */
    boolean isBridge(World world, int x, int y, int z) {
        return find(world.getName(), x, y, z) != null;
    }

    private Entry find(String world, int x, int y, int z) {
        for (Entry e : entries) {
            if (e.x() == x && e.y() == y && e.z() == z && e.world().equals(world)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Freezes the block at x,y,z into {@code ice} if it is still water or lava. Asking again for a
     * block already frozen just extends its life.
     */
    boolean place(World world, int x, int y, int z, Material ice, long lifetimeMillis, String horde) {
        long expires = clock.getAsLong() + lifetimeMillis;
        Entry old = find(world.getName(), x, y, z);
        Block block = world.getBlockAt(x, y, z);
        if (old != null) {
            if (block.getType() != ice) {
                return false;
            }
            entries.remove(old);
            entries.add(new Entry(old.world(), x, y, z, old.original(), old.placed(), expires, old.horde()));
            dirty = true;
            return true;
        }
        Material was = block.getType();
        if (was != Material.WATER && was != Material.LAVA) {
            return false;
        }
        block.setType(ice);
        entries.add(new Entry(world.getName(), x, y, z, was.name(), ice.name(), expires, horde));
        dirty = true;
        return true;
    }

    /** Turns back every bridge block whose time has run out; those with someone on them wait a while. */
    int restoreDue(Predicate<Block> occupied) {
        long now = clock.getAsLong();
        return restoreWhere(e -> e.expiresMillis() <= now && !(now - e.expiresMillis() < GRACE_MILLIS && occupied(e, occupied)));
    }

    private boolean occupied(Entry e, Predicate<Block> occupied) {
        World w = Bukkit.getWorld(e.world());
        return w != null && occupied != null && occupied.test(w.getBlockAt(e.x(), e.y(), e.z()));
    }

    /** Turns back everything one horde froze. */
    int restoreHorde(String horde) {
        return restoreWhere(e -> e.horde().equals(horde));
    }

    /** Turns everything back (plugin shutdown, or start-up after a crash). */
    int restoreAll() {
        return restoreWhere(e -> true);
    }

    private int restoreWhere(Predicate<Entry> which) {
        int n = 0;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!which.test(e)) {
                continue;
            }
            it.remove();
            dirty = true;
            World w = Bukkit.getWorld(e.world());
            if (w == null) {
                continue; // that world is gone; nothing to put back
            }
            Block b = w.getBlockAt(e.x(), e.y(), e.z());
            if (!b.getType().name().equals(e.placed())) {
                continue; // someone broke it or built over it: it is theirs now
            }
            Material back = Material.matchMaterial(e.original());
            if (back != null) {
                b.setType(back);
                n++;
            }
        }
        return n;
    }
}
