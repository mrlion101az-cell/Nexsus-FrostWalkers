package com.nexuscraft.nexusrime;

import java.util.Random;
import java.util.function.BiPredicate;

/** Picks where the horde appears: a ring around the target, at the configured distance, skipping
 *  any spot the caller says is not allowed (protected ground, unloaded chunks, no ground). */
final class SpawnPlanner {

    private static final int ATTEMPTS = 16;

    private SpawnPlanner() {
    }

    /** Returns {dx, dz} offsets from the target, or null if every try was refused. */
    static double[] pick(RimeConfig cfg, Random random, BiPredicate<Double, Double> allowed) {
        double start = random.nextDouble() * Math.PI * 2.0;
        double step = Math.PI * 2.0 / ATTEMPTS;
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = start + step * i;
            double dx = Math.cos(angle) * cfg.spawnDistance;
            double dz = Math.sin(angle) * cfg.spawnDistance;
            if (allowed.test(dx, dz)) {
                return new double[] {dx, dz};
            }
        }
        return null;
    }
}
