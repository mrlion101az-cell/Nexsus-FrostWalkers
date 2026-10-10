package com.nexuscraft.nexusrime;

/**
 * One step of a route. {@code x,y,z} is the block the army ends up standing in (feet).
 * {@code extra} is the wall height for a CLIMB, and 0 otherwise.
 */
record Move(MoveKind kind, int x, int y, int z, int extra) {

    /** True for the steps that need the army to do something other than walk. */
    boolean special() {
        return kind == MoveKind.BREACH || kind == MoveKind.CLIMB || kind == MoveKind.BRIDGE;
    }
}
