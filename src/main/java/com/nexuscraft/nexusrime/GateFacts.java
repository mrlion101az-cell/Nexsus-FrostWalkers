package com.nexuscraft.nexusrime;

/** Everything the "may a horde begin?" rules need, as plain values so they can be tested alone. */
record GateFacts(
        boolean forced,
        SeasonStatus season,
        String worldName,
        int activeHordes,
        long nowMillis,
        long lastEventMillis,
        int eligiblePlayers,
        boolean hasTarget,
        double rollPercent) {
}
