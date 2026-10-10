package com.nexuscraft.nexusrime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Openable;

import java.util.HashMap;
import java.util.Map;

/** The real world as a {@link Terrain}: blocks are read once per plan and remembered. */
final class WorldTerrain implements Terrain {

    private final World world;
    private final ZoneGuard zones;
    private final Map<Long, Cell> cache = new HashMap<>();

    WorldTerrain(World world, ZoneGuard zones) {
        this.world = world;
        this.zones = zones;
    }

    @Override
    public Cell at(int x, int y, int z) {
        long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 64) & 0xFFF);
        Cell known = cache.get(key);
        if (known != null) {
            return known;
        }
        Cell c = read(x, y, z);
        cache.put(key, c);
        return c;
    }

    private Cell read(int x, int y, int z) {
        if (y < world.getMinHeight()) {
            return Cell.SOLID;
        }
        if (y >= world.getMaxHeight()) {
            return Cell.AIR;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Cell.SOLID; // never plan into the unknown
        }
        Block b = world.getBlockAt(x, y, z);
        Material m = b.getType();
        if (m == null) {
            return Cell.SOLID;
        }
        String name = m.name();
        BlockData data = b.getBlockData();
        boolean still = !(data instanceof Levelled level) || level.getLevel() == 0;
        boolean open = data instanceof Openable o && o.isOpen();
        boolean doorLike = name.endsWith("_DOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("_TRAPDOOR");
        boolean safe = doorLike && !open && zones.isProtected(new Location(world, x + 0.5, y + 0.5, z + 0.5));
        return CellRules.classify(name, m.isSolid(), still, open, safe);
    }
}
