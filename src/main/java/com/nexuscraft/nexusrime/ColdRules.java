package com.nexuscraft.nexusrime;

/**
 * The creeping frost. Frost grows with closeness to the walker, shrinks for each piece of leather
 * armour worn, and stops entirely beside a fire. All pure numbers so it can be tested alone.
 */
final class ColdRules {

    private ColdRules() {
    }

    /** Frost ticks to add this cycle (0 when out of reach, protected, or warm). */
    static int frostGain(RimeConfig cfg, ColdFacts f) {
        if (f.nearWarmth() || cfg.frostPerCycle <= 0 || f.distanceToWalker() >= cfg.coldRadius) {
            return 0;
        }
        double closeness = 1.0 - Math.max(0.0, f.distanceToWalker()) / cfg.coldRadius;
        double protection = Math.min(0.95, Math.max(0, f.leatherPieces()) * cfg.leatherProtectionPerPiece);
        return (int) Math.round(cfg.frostPerCycle * closeness * (1.0 - protection));
    }

    /** Whether the slowness of the whiteout applies: only inside the aura, and never when warm. */
    static boolean slowed(RimeConfig cfg, ColdFacts f) {
        return cfg.slownessAmplifier >= 0 && !f.nearWarmth() && f.distanceToWalker() < cfg.coldRadius;
    }
}
