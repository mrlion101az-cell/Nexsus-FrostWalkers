package com.nexuscraft.nexusrime;

/** Breaking obsidian can shake loose dragonglass shards. */
final class ObsidianDrop {

    private ObsidianDrop() {
    }

    /**
     * How many shards one broken block of obsidian yields.
     *
     * @param percentRoll a roll in [0, 100): the drop happens when it is below the configured chance
     * @param spreadRoll  a roll in [0, 1) picking the amount between the configured minimum and maximum
     */
    static int shards(RimeConfig cfg, double percentRoll, double spreadRoll) {
        if (!cfg.obsidianDrop || percentRoll >= cfg.obsidianChance) {
            return 0;
        }
        int span = cfg.obsidianMax - cfg.obsidianMin + 1;
        int extra = (int) Math.floor(Math.max(0.0, Math.min(0.999999, spreadRoll)) * span);
        return cfg.obsidianMin + extra;
    }
}
