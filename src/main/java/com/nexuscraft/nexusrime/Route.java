package com.nexuscraft.nexusrime;

import java.util.List;

/**
 * A plan from where the Knight stands to where his target is.
 *
 * @param reached whether the plan really gets there; if false it leads as close as it could
 * @param moves   the steps, in order, not including the start
 * @param cost    the planner's cost for the whole thing
 * @param expanded how many positions the search looked at
 */
record Route(boolean reached, List<Move> moves, double cost, int expanded) {

    static final Route NONE = new Route(false, List.of(), 0.0, 0);

    /** How many of the steps are BRIDGE steps. */
    int bridgeCount() {
        int n = 0;
        for (Move m : moves) {
            if (m.kind() == MoveKind.BRIDGE) {
                n++;
            }
        }
        return n;
    }
}
