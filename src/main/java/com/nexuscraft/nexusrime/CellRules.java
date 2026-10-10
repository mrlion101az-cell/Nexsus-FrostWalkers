package com.nexuscraft.nexusrime;

/** Turns a block's facts into a {@link Cell}. Pure, so the whole table can be tested. */
final class CellRules {

    private CellRules() {
    }

    /**
     * @param name       the material name, e.g. {@code "OAK_DOOR"}
     * @param solid      whether the material is a solid block
     * @param stillLiquid for water and lava: true if this is a source block (level 0)
     * @param open       for doors, trapdoors and gates: true if currently open
     * @param safeGround true if a door here is on protected ground (never smashed)
     */
    static Cell classify(String name, boolean solid, boolean stillLiquid, boolean open, boolean safeGround) {
        switch (name) {
            case "WATER":
                return stillLiquid ? Cell.WATER : Cell.FLOWING_WATER;
            case "KELP", "KELP_PLANT", "SEAGRASS", "TALL_SEAGRASS", "BUBBLE_COLUMN":
                return Cell.FLOWING_WATER;
            case "LAVA":
                return stillLiquid ? Cell.LAVA : Cell.HAZARD;
            case "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "MAGMA_BLOCK", "CACTUS", "SWEET_BERRY_BUSH",
                 "COBWEB", "WITHER_ROSE", "POWDER_SNOW", "POINTED_DRIPSTONE":
                return Cell.HAZARD;
            default:
                break;
        }
        if (name.endsWith("_DOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("_TRAPDOOR")) {
            if (open) {
                return Cell.AIR;
            }
            return safeGround ? Cell.SOLID : Cell.DOOR;
        }
        return solid ? Cell.SOLID : Cell.AIR;
    }

    /** True for the stronger kinds of door. */
    static boolean ironLike(String name) {
        return name != null && name.startsWith("IRON_");
    }
}
