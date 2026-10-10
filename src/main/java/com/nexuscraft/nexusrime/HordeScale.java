package com.nexuscraft.nexusrime;

/** How big the opening army is. */
final class HordeScale {

    private HordeScale() {
    }

    static int openingWights(RimeConfig cfg, int playersInWorld) {
        int n = cfg.wightsBase + cfg.wightsPerPlayer * Math.max(0, playersInWorld);
        return Math.max(0, Math.min(Math.min(cfg.wightsStartMax, cfg.wightsMaxAlive), n));
    }

    /** How many wights to raise now: the batch size, never past the living limit. */
    static int reinforcements(RimeConfig cfg, int alive) {
        return reinforcements(cfg.reinforceCount, alive, cfg.wightsMaxAlive);
    }

    /** The same for any batch size and living limit (a Frost Knight raises more, and more often). */
    static int reinforcements(int batch, int alive, int cap) {
        return Math.max(0, Math.min(batch, cap - alive));
    }
}
