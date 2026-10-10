package com.nexuscraft.nexusrime;

/**
 * A living ladder, as a little state machine ticked once a second. The crew gathers at the foot of
 * the wall, holds still in a pile for a moment, and then everyone is passed up to the top.
 */
final class StackJob {

    enum Phase { GATHER, BUILD, ASCEND, DONE, FAILED }

    private static final int ASCEND_LIMIT = 40;
    private static final int QUIET_SECONDS = 2;

    final int needed;
    private final int gatherLimit;
    private final int buildSeconds;
    private Phase phase = Phase.GATHER;
    private int seconds;
    private int quiet;

    StackJob(int needed, int gatherLimit, int buildSeconds) {
        this.needed = needed;
        this.gatherLimit = gatherLimit;
        this.buildSeconds = buildSeconds;
    }

    Phase phase() {
        return phase;
    }

    boolean finished() {
        return phase == Phase.DONE || phase == Phase.FAILED;
    }

    /**
     * How many of {@code total} crew bodies should be standing in the pile by now. The pile is built a
     * body at a time across the build phase, so the last one joins on the last build second.
     */
    int placedFor(int total) {
        if (phase != Phase.BUILD) {
            return phase == Phase.GATHER ? 0 : total;
        }
        return Math.min(total, (int) Math.ceil(total * (seconds + 1.0) / buildSeconds));
    }

    /**
     * @param crewReady how many of the crew are standing at the foot of the wall
     * @param waiting   how many of the other army are still waiting at the foot to be passed up
     */
    Phase tick(int crewReady, int waiting) {
        switch (phase) {
            case GATHER -> {
                seconds++;
                if (crewReady >= needed) {
                    phase = Phase.BUILD;
                    seconds = 0;
                } else if (seconds >= gatherLimit) {
                    phase = Phase.FAILED;
                }
            }
            case BUILD -> {
                seconds++;
                if (seconds >= buildSeconds) {
                    phase = Phase.ASCEND;
                    seconds = 0;
                }
            }
            case ASCEND -> {
                seconds++;
                quiet = waiting == 0 ? quiet + 1 : 0;
                if (quiet >= QUIET_SECONDS || seconds >= ASCEND_LIMIT) {
                    phase = Phase.DONE;
                }
            }
            default -> {
                // finished
            }
        }
        return phase;
    }
}
