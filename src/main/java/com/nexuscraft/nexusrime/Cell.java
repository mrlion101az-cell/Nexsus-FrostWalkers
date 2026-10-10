package com.nexuscraft.nexusrime;

/** What one block of the world means to a marching army. */
enum Cell {
    /** Empty or soft enough to walk through (air, grass, flowers, snow...). */
    AIR,
    /** A wall or floor. */
    SOLID,
    /** A closed door, trapdoor or fence gate that a crowd can smash open. */
    DOOR,
    /** A still (source) block of water: can be swum, or frozen into a bridge. */
    WATER,
    /** Moving water, kelp or seagrass: can be swum but not bridged. */
    FLOWING_WATER,
    /** A still (source) block of lava: deadly, but can be frozen into a bridge. */
    LAVA,
    /** Fire, moving lava, cacti, cobwebs, magma: never entered. */
    HAZARD;

    /** Can a body occupy this block? */
    boolean passable() {
        return this == AIR || this == WATER || this == FLOWING_WATER;
    }

    boolean swimmable() {
        return this == WATER || this == FLOWING_WATER;
    }

    /** Can something stand on top of this block? */
    boolean floor() {
        return this == SOLID || this == DOOR;
    }
}
