package com.nexuscraft.nexusrime;

/** A read-only view of the blocks an army has to cross. */
interface Terrain {
    Cell at(int x, int y, int z);
}
