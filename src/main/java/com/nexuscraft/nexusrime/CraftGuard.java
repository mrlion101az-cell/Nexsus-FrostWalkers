package com.nexuscraft.nexusrime;

/**
 * Shards may only ever go into our own gear recipes. Their base material is an ordinary item that
 * vanilla recipes also use, so without this a shard could be spent as plain flint (or whatever it
 * is based on), and a shard fed into the shard recipe would make more shards.
 */
final class CraftGuard {

    private CraftGuard() {
    }

    /** True when the craft must be refused: a shard is in the grid and the recipe is not a gear
     *  recipe of ours (or it is the one that makes shards). */
    static boolean blocked(boolean gridHasShard, boolean recipeIsOurs, boolean recipeMakesShard) {
        return gridHasShard && (!recipeIsOurs || recipeMakesShard);
    }
}
