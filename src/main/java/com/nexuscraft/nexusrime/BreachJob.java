package com.nexuscraft.nexusrime;

/** A siege on one door, ticked once a second: every body at the door adds to the damage. */
final class BreachJob {

    enum State { WORKING, BROKEN, FAILED }

    private final double strength;
    private final int timeoutSeconds;
    private final int maxContributors;
    private final double perWight;
    private final double knightPower;
    private double progress;
    private int seconds;

    BreachJob(double strength, int timeoutSeconds, int maxContributors, double perWight, double knightPower) {
        this.strength = strength;
        this.timeoutSeconds = timeoutSeconds;
        this.maxContributors = maxContributors;
        this.perWight = perWight;
        this.knightPower = knightPower;
    }

    State tick(int wightsAtDoor, boolean knightAtDoor) {
        seconds++;
        int pushing = Math.max(0, Math.min(maxContributors, wightsAtDoor));
        progress += pushing * perWight + (knightAtDoor ? knightPower : 0.0);
        if (progress >= strength) {
            return State.BROKEN;
        }
        return seconds >= timeoutSeconds ? State.FAILED : State.WORKING;
    }

    /** 0.0 untouched, 1.0 gone. */
    double fraction() {
        return Math.min(1.0, progress / strength);
    }

    /** Door strength for a given kind of door. */
    static double strengthOf(RimeConfig cfg, String materialName) {
        return CellRules.ironLike(materialName) ? cfg.doorStrength * cfg.ironDoorFactor : cfg.doorStrength;
    }
}
