package com.nexuscraft.nexusrime;

/** Everything the route planner needs to know about costs and what is allowed. */
record PlanRules(int maxNodes, int radius, double swimCost, double doorCost, double bridgeWaterCost,
                 double bridgeLavaCost, double climbBase, double climbPerBlock, int maxClimb, boolean breach,
                 boolean bridge, int maxBridge, int crew, boolean climb) {

    static PlanRules from(RimeConfig cfg, int crew, boolean allowClimb) {
        return new PlanRules(cfg.knightPlanNodes, cfg.knightPlanRadius, 4.0, cfg.breachCost, 2.5, 3.5, 8.0, 3.0,
                cfg.stackMaxHeight, cfg.breachEnabled, cfg.bridgeEnabled, cfg.bridgeMaxBlocks, crew,
                cfg.stackEnabled && allowClimb);
    }

    PlanRules withoutBridge() {
        return new PlanRules(maxNodes, radius, swimCost, doorCost, bridgeWaterCost, bridgeLavaCost, climbBase,
                climbPerBlock, maxClimb, breach, false, maxBridge, crew, climb);
    }
}
