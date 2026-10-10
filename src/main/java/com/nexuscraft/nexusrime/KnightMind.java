package com.nexuscraft.nexusrime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything one Frost Knight is currently thinking: his route, his jobs, and how it is going. */
final class KnightMind {
    Route route = Route.NONE;
    int cursor;
    long nextPlanTick;
    int startX;
    int startY;
    int startZ;
    int goalX = Integer.MIN_VALUE;
    int goalY;
    int goalZ;

    BreachJob breach;
    Move breachMove;
    StackJob stack;
    Move stackMove;
    /** Bodies held still in the pile or on its side (AI and gravity off), so they can always be set free. */
    final Set<UUID> held = new HashSet<>();
    /** Climbers on their way up the side of the pile: body to the step it stands on (the crew start at their own slot). */
    final Map<UUID, Integer> steps = new java.util.LinkedHashMap<>();
    /** The crew start climbing only once everyone else is over. */
    boolean crewGo;
    /** The bodies working on the current job. */
    final Set<UUID> crew = new LinkedHashSet<>();
    /** Doors that held out against a siege: treated as walls from now on. */
    final Set<Long> failedDoors = new HashSet<>();

    double lastX = Double.NaN;
    double lastZ = Double.NaN;
    int stuckSeconds;
    int stuckReplans;
    int movingSeconds;
    long fallbackUntil;
    long noClimbUntil;
    int bridged;
    final Map<String, Long> lastShout = new HashMap<>();

    boolean jobActive() {
        return breach != null || stack != null;
    }
}
