package com.nexuscraft.nexusrime;

/** The arithmetic of a living ladder: how many bodies a wall needs, and where each one stands. */
final class StackPlan {

    /** Each body adds this much height to the pile. */
    static final double SPACING = 2.0;
    static final int MIN_CREW = 3;

    private StackPlan() {
    }

    /** Bodies needed to reach the top of a wall {@code height} blocks tall. */
    static int crewFor(int height) {
        return Math.max(MIN_CREW, (int) Math.ceil(height / SPACING) + 1);
    }

    /** How far above the floor body number {@code index} (0 = bottom) is held. */
    static double heightOf(int index) {
        return index * SPACING;
    }

    /**
     * How many hand-over-hand steps a climber takes up the side of the pile before the last hop onto
     * the wall: one for every pile height that is still more than a block below the top.
     */
    static int stepsBelow(double rise) {
        int s = 0;
        while (heightOf(s + 1) < rise - 1.0) {
            s++;
        }
        return s;
    }

    /** Which side of the pile body {@code salt} leans to: they alternate, so a pile looks ragged, not ruled. */
    static double stagger(int salt) {
        return (salt & 1) == 0 ? 0.2 : -0.2;
    }

    /** Minecraft yaw (degrees) that faces a body toward the wall, which lies {@code dx}, {@code dz} away. */
    static float yawToward(double dx, double dz) {
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
