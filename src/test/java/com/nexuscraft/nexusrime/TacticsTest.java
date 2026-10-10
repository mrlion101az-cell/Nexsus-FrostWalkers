package com.nexuscraft.nexusrime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** The Frost Knight's mind: block classification, the route planner and the two job timers. */
public final class TacticsTest {

    /** A world of flat ground (solid at y <= 0, air above) that tests carve things into. */
    static final class Grid implements Terrain {
        final Map<String, Cell> cells = new HashMap<>();

        void set(int x, int y, int z, Cell c) {
            cells.put(x + "," + y + "," + z, c);
        }

        void box(int x0, int x1, int y0, int y1, int z0, int z1, Cell c) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, c);
                    }
                }
            }
        }

        @Override
        public Cell at(int x, int y, int z) {
            Cell c = cells.get(x + "," + y + "," + z);
            if (c != null) {
                return c;
            }
            return y <= 0 ? Cell.SOLID : Cell.AIR;
        }
    }

    static PlanRules rules(int crew) {
        return PlanRules.from(new RimeConfig(), crew, true);
    }

    static Route plan(Terrain t, PlanRules r, int gx, int gy, int gz) {
        return RoutePlanner.plan(t, r, 0, 1, 0, gx, gy, gz);
    }

    static int count(Route r, MoveKind kind) {
        int n = 0;
        for (Move m : r.moves()) {
            if (m.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    public static void main(String[] args) {
        cells();
        stackPlan();
        stackJob();
        breachJob();
        rulesFactory();
        openGround();
        doors();
        lava();
        water();
        cliffs();
        limits();
        prices();
        shapes();
        Kit.finish();
    }

    // ------------------------------------------------------------------ what a block means

    private static void cells() {
        expect(CellRules.classify("AIR", false, false, false, false) == Cell.AIR, "cell: air");
        expect(CellRules.classify("CAVE_AIR", false, false, false, false) == Cell.AIR, "cell: cave air");
        expect(CellRules.classify("STONE", true, false, false, false) == Cell.SOLID, "cell: stone is solid");
        expect(CellRules.classify("TALL_GRASS", false, false, false, false) == Cell.AIR, "cell: grass is walked through");
        expect(CellRules.classify("WATER", false, true, false, false) == Cell.WATER, "cell: a water source");
        expect(CellRules.classify("WATER", false, false, false, false) == Cell.FLOWING_WATER, "cell: moving water");
        expect(CellRules.classify("KELP", false, false, false, false) == Cell.FLOWING_WATER, "cell: kelp is swum through, never frozen");
        expect(CellRules.classify("LAVA", false, true, false, false) == Cell.LAVA, "cell: a lava source");
        expect(CellRules.classify("LAVA", false, false, false, false) == Cell.HAZARD, "cell: moving lava is never entered");
        expect(CellRules.classify("VOID_AIR", false, false, false, false) == Cell.AIR, "cell: void air");
        for (String bad : new String[] {"FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS", "COBWEB", "MAGMA_BLOCK", "SWEET_BERRY_BUSH",
                "WITHER_ROSE", "POWDER_SNOW", "POINTED_DRIPSTONE"}) {
            expect(CellRules.classify(bad, true, false, false, false) == Cell.HAZARD, "cell: " + bad + " is a hazard");
        }
        expect(CellRules.classify("OAK_DOOR", true, false, false, false) == Cell.DOOR, "cell: a closed door");
        expect(CellRules.classify("IRON_DOOR", true, false, false, false) == Cell.DOOR, "cell: an iron door");
        expect(CellRules.classify("SPRUCE_FENCE_GATE", true, false, false, false) == Cell.DOOR, "cell: a fence gate");
        expect(CellRules.classify("OAK_TRAPDOOR", true, false, false, false) == Cell.DOOR, "cell: a trapdoor");
        expect(CellRules.classify("OAK_DOOR", true, false, true, false) == Cell.AIR, "cell: an open door is just a gap");
        expect(CellRules.classify("OAK_DOOR", true, false, false, true) == Cell.SOLID, "cell: a door on safe ground is a wall");
        expect(CellRules.classify("OAK_FENCE", true, false, false, false) == Cell.SOLID, "cell: a fence is not a gate");
        expect(Cell.AIR.passable() && Cell.WATER.passable() && Cell.FLOWING_WATER.passable(), "cell: bodies fit in air and water");
        expect(!Cell.SOLID.passable() && !Cell.DOOR.passable() && !Cell.LAVA.passable() && !Cell.HAZARD.passable(), "cell: and not in the rest");
        expect(Cell.SOLID.floor() && Cell.DOOR.floor() && !Cell.WATER.floor() && !Cell.LAVA.floor() && !Cell.AIR.floor(), "cell: what can be stood on");
        expect(CellRules.ironLike("IRON_DOOR") && CellRules.ironLike("IRON_TRAPDOOR") && !CellRules.ironLike("OAK_DOOR") && !CellRules.ironLike(null), "cell: iron doors");
    }

    // ------------------------------------------------------------------ living ladders

    private static void stackPlan() {
        expect(StackPlan.crewFor(2) == 3, "stack: even a short wall needs a handful");
        expect(StackPlan.crewFor(4) == 3, "stack: four blocks, three bodies");
        expect(StackPlan.crewFor(5) == 4, "stack: five blocks, four bodies");
        expect(StackPlan.crewFor(8) == 5, "stack: eight blocks, five bodies");
        expect(StackPlan.crewFor(16) == 9, "stack: sixteen blocks, nine bodies");
        expect(near(StackPlan.heightOf(0), 0.0) && near(StackPlan.heightOf(3), 6.0), "stack: each body adds two blocks");
        int need = StackPlan.crewFor(8);
        expect(StackPlan.heightOf(need - 1) >= 8.0, "stack: the top body reaches the top of the wall");
    }

    private static void stackJob() {
        expect(StackPlan.stepsBelow(1) == 0 && StackPlan.stepsBelow(2) == 0 && StackPlan.stepsBelow(3) == 0, "stack: a short wall is climbed in one hop from the pile");
        expect(StackPlan.stepsBelow(4) == 1 && StackPlan.stepsBelow(5) == 1 && StackPlan.stepsBelow(6) == 2 && StackPlan.stepsBelow(8) == 3, "stack: taller walls take a step for every two blocks below the last one");
        expect(StackPlan.stagger(0) > 0 && StackPlan.stagger(1) < 0 && StackPlan.stagger(2) > 0 && near(Math.abs(StackPlan.stagger(3)), 0.2), "stack: the pile leans a little left and right in turn, never out of its column");
        expect(near(StackPlan.yawToward(0, 1), 0.0) && near(StackPlan.yawToward(-1, 0), 90.0) && near(StackPlan.yawToward(1, 0), -90.0) && near(Math.abs(StackPlan.yawToward(0, -1)), 180.0), "stack: bodies face the wall (minecraft yaw)");
        expect(near(StackPlan.yawToward(0, 0), 0.0) && near(StackPlan.yawToward(1e-9, 1e-9), 0.0), "stack: with no wall direction they face south");
        StackJob grow = new StackJob(4, 5, 4);
        grow.tick(4, 0);
        expect(grow.placedFor(4) == 1, "stackjob: one body is in the pile on the first build second");
        grow.tick(4, 0);
        expect(grow.placedFor(4) == 2, "stackjob: two on the second");
        grow.tick(4, 0);
        expect(grow.placedFor(4) == 3, "stackjob: three on the third");
        grow.tick(4, 0);
        expect(grow.placedFor(4) == 4 && grow.placedFor(2) == 2 && grow.placedFor(0) == 0, "stackjob: all of them on the last build second, however many are left");
        grow.tick(4, 0);
        expect(grow.phase() == StackJob.Phase.ASCEND && grow.placedFor(4) == 4, "stackjob: once climbing, the whole crew counts as placed");
        expect(new StackJob(4, 5, 4).placedFor(4) == 0, "stackjob: nobody is placed while still gathering");
        StackJob j = new StackJob(4, 5, 2);
        expect(j.phase() == StackJob.Phase.GATHER && !j.finished(), "stackjob: starts by gathering");
        j.tick(3, 0);
        expect(j.phase() == StackJob.Phase.GATHER, "stackjob: too few at the wall, keeps waiting");
        j.tick(4, 0);
        expect(j.phase() == StackJob.Phase.BUILD, "stackjob: enough crew starts the pile");
        j.tick(4, 9);
        expect(j.phase() == StackJob.Phase.BUILD, "stackjob: the pile is held for its build time");
        j.tick(4, 9);
        expect(j.phase() == StackJob.Phase.ASCEND, "stackjob: then everyone goes up");
        j.tick(4, 5);
        j.tick(4, 0);
        expect(j.phase() == StackJob.Phase.ASCEND, "stackjob: one quiet second is not the end");
        j.tick(4, 3);
        j.tick(4, 0);
        expect(j.phase() == StackJob.Phase.ASCEND, "stackjob: a straggler resets the quiet count");
        j.tick(4, 0);
        expect(j.phase() == StackJob.Phase.DONE && j.finished(), "stackjob: two quiet seconds and it is done");
        j.tick(0, 0);
        expect(j.phase() == StackJob.Phase.DONE, "stackjob: done stays done");

        StackJob slow = new StackJob(4, 3, 2);
        slow.tick(1, 0);
        slow.tick(2, 0);
        expect(!slow.finished(), "stackjob: still waiting inside the limit");
        slow.tick(2, 0);
        expect(slow.phase() == StackJob.Phase.FAILED && slow.finished(), "stackjob: gives up if the crew never gathers");

        StackJob endless = new StackJob(1, 3, 1);
        endless.tick(1, 5);
        endless.tick(1, 5);
        for (int i = 0; i < 45; i++) {
            endless.tick(1, 5);
        }
        expect(endless.phase() == StackJob.Phase.DONE, "stackjob: a job that never empties still ends");
    }

    private static void breachJob() {
        RimeConfig c = new RimeConfig();
        BreachJob j = new BreachJob(60.0, 90, 12, 1.0, 3.0);
        int ticks = 0;
        BreachJob.State s = BreachJob.State.WORKING;
        while (s == BreachJob.State.WORKING && ticks < 100) {
            s = j.tick(6, true);
            ticks++;
        }
        expect(s == BreachJob.State.BROKEN && ticks == 7, "breach: six bodies and the Knight take seven seconds (63 of 60), took " + ticks);
        expect(near(j.fraction(), 1.0), "breach: a broken door is fully broken");

        BreachJob alone = new BreachJob(60.0, 90, 12, 1.0, 3.0);
        for (int i = 0; i < 19; i++) {
            alone.tick(0, true);
        }
        expect(alone.fraction() < 1.0 && near(alone.fraction(), 57.0 / 60.0), "breach: the Knight alone is slow");
        expect(alone.tick(0, true) == BreachJob.State.BROKEN, "breach: but he gets through in twenty seconds");

        BreachJob away = new BreachJob(100.0, 90, 4, 2.0, 3.0);
        away.tick(1, false);
        expect(near(away.fraction(), 0.02), "breach: the Knight does nothing to a door while he is elsewhere (one body, 2 damage)");
        away.tick(1, true);
        expect(near(away.fraction(), 0.07), "breach: and adds his power once he is there");

        BreachJob crowd = new BreachJob(100.0, 90, 4, 1.0, 0.0);
        crowd.tick(50, false);
        expect(near(crowd.fraction(), 0.04), "breach: a crowd is capped by what fits at a door");
        BreachJob none = new BreachJob(10.0, 3, 4, 1.0, 0.0);
        none.tick(0, false);
        none.tick(-5, false);
        expect(near(none.fraction(), 0.0), "breach: nobody there, no damage (and never negative)");
        expect(none.tick(0, false) == BreachJob.State.FAILED, "breach: a siege nobody can reach gives up");
        BreachJob last = new BreachJob(10.0, 2, 4, 5.0, 0.0);
        last.tick(1, false);
        expect(last.tick(1, false) == BreachJob.State.BROKEN, "breach: breaking wins over timing out on the same second");

        expect(near(BreachJob.strengthOf(c, "OAK_DOOR"), c.doorStrength), "breach: a wooden door has the base strength");
        expect(near(BreachJob.strengthOf(c, "IRON_DOOR"), c.doorStrength * c.ironDoorFactor), "breach: iron is tougher");
    }

    private static void rulesFactory() {
        RimeConfig c = new RimeConfig();
        PlanRules r = PlanRules.from(c, 7, true);
        expect(r.crew() == 7 && r.climb() && r.breach() && r.bridge(), "rules: everything on by default");
        expect(!PlanRules.from(c, 7, false).climb(), "rules: no climbing while a climb has recently failed");
        c.stackEnabled = false;
        expect(!PlanRules.from(c, 7, true).climb(), "rules: stacking can be switched off");
        c.breachEnabled = false;
        c.bridgeEnabled = false;
        PlanRules off = PlanRules.from(c, 7, true);
        expect(!off.breach() && !off.bridge(), "rules: doors and bridges can be switched off");
        expect(PlanRules.from(new RimeConfig(), 3, true).withoutBridge().bridge() == false, "rules: withoutBridge turns bridges off");
        expect(PlanRules.from(new RimeConfig(), 3, true).withoutBridge().crew() == 3, "rules: and changes nothing else");
    }

    // ------------------------------------------------------------------ the planner

    private static void openGround() {
        Grid g = new Grid();
        Route r = plan(g, rules(0), 10, 1, 0);
        expect(r.reached() && r.moves().size() >= 9 && r.moves().size() <= 10, "plan: across open ground it just walks");
        expect(count(r, MoveKind.WALK) == r.moves().size(), "plan: every step is a walk");
        expect(r.cost() >= 9.0 && r.cost() <= 10.5, "plan: a step costs one");
        Move last = r.moves().get(r.moves().size() - 1);
        expect(Math.abs(last.x() - 10) <= 1 && last.y() == 1, "plan: and ends beside the goal");
        Route here = plan(g, rules(0), 1, 1, 0);
        expect(here.reached() && here.moves().size() <= 1, "plan: a goal next door needs at most one step");

        Grid hill = new Grid();
        hill.box(3, 3, 1, 1, -5, 5, Cell.SOLID);
        Route up = plan(hill, rules(0), 6, 1, 0);
        expect(up.reached() && count(up, MoveKind.WALK) == up.moves().size(), "plan: a one-block ledge is stepped over");

        Grid ditch = new Grid();
        ditch.box(3, 3, -2, 0, -30, 30, Cell.AIR);
        Route down = plan(ditch, rules(0), 6, 1, 0);
        expect(down.reached(), "plan: a hole is climbed into and out of");

        Grid shelf = new Grid();
        shelf.box(-60, 2, 1, 3, -60, 60, Cell.SOLID);
        Route drop = RoutePlanner.plan(shelf, rules(0), 0, 4, 0, 6, 1, 0);
        expect(drop.reached() && count(drop, MoveKind.WALK) == drop.moves().size(), "plan: a three-block drop is simply jumped");
        boolean fell = false;
        for (Move mv : drop.moves()) {
            fell |= mv.y() == 1;
        }
        expect(fell, "plan: and the route comes down to the ground");
        Grid cliffDown = new Grid();
        cliffDown.box(-60, 2, 1, 4, -60, 60, Cell.SOLID);
        expect(!RoutePlanner.plan(cliffDown, rules(0), 0, 5, 0, 6, 1, 0).reached(), "plan: a four-block drop is too far to jump");

        Route airborne = plan(g, rules(0), 10, 3, 0);
        expect(airborne.reached(), "plan: a target a couple of blocks up in the air (mid-jump) still counts as reached");
        expect(!plan(g, rules(0), 10, 6, 0).reached(), "plan: but one far above the ground is out of reach");

        Grid fire = new Grid();
        fire.set(2, 1, 2, Cell.HAZARD);
        Route careful = RoutePlanner.plan(fire, rules(0), 0, 1, 0, 4, 1, 4);
        boolean clear = careful.reached();
        for (Move mv : careful.moves()) {
            clear &= Math.abs(mv.x() - 2) + Math.abs(mv.z() - 2) > 1;
        }
        expect(clear, "plan: with several routes of equal length he takes one that does not brush past the fire");

        Grid maze = new Grid();
        maze.box(4, 4, 1, 3, -4, 4, Cell.SOLID);
        Route around = plan(maze, rules(0), 8, 1, 0);
        expect(around.reached() && count(around, MoveKind.WALK) == around.moves().size(), "plan: a short wall is walked round");
        expect(around.cost() > 8.0 && around.cost() < 22.0, "plan: at the price of the detour");
    }

    private static void doors() {
        Grid g = new Grid();
        g.box(5, 5, 1, 3, -10, 10, Cell.SOLID);
        g.set(5, 1, 0, Cell.DOOR);
        g.set(5, 2, 0, Cell.DOOR);
        Route r = plan(g, rules(0), 10, 1, 0);
        expect(r.reached() && count(r, MoveKind.BREACH) == 1, "doors: a long wall with a door is a siege");
        Move door = null;
        for (Move m : r.moves()) {
            if (m.kind() == MoveKind.BREACH) {
                door = m;
            }
        }
        expect(door != null && door.x() == 5 && door.y() == 1 && door.z() == 0, "doors: aimed at the door");
        expect(count(r, MoveKind.WALK) >= 8, "doors: and walked to either side of it");

        Grid short_ = new Grid();
        short_.box(5, 5, 1, 3, -3, 3, Cell.SOLID);
        short_.set(5, 1, 0, Cell.DOOR);
        short_.set(5, 2, 0, Cell.DOOR);
        Route detour = plan(short_, rules(0), 10, 1, 0);
        expect(detour.reached() && count(detour, MoveKind.BREACH) == 0, "doors: a short wall is walked round instead of broken");

        PlanRules noBreach = new PlanRules(5000, 56, 4, 10, 2.5, 3.5, 8, 3, 8, false, true, 32, 0, true);
        Route blocked = plan(g, noBreach, 10, 1, 0);
        expect(blocked.reached() && count(blocked, MoveKind.BREACH) == 0, "doors: with sieges switched off, he goes round and the door stays shut");

        Grid sealedDoor = new Grid();
        sealedDoor.box(5, 5, 1, 3, -60, 60, Cell.SOLID);
        sealedDoor.set(5, 1, 0, Cell.DOOR);
        sealedDoor.set(5, 2, 0, Cell.DOOR);
        expect(!plan(sealedDoor, noBreach, 10, 1, 0).reached(), "doors: with sieges off, a sealed door really does hold");

        Grid sealed = new Grid();
        sealed.box(5, 5, 1, 3, -60, 60, Cell.SOLID);
        Route none = plan(sealed, rules(0), 10, 1, 0);
        expect(!none.reached(), "doors: a wall with no door and no way round is not crossed");
        expect(!none.moves().isEmpty() && none.moves().get(none.moves().size() - 1).x() >= 3, "doors: but the plan leads up to it");

        Grid gate = new Grid();
        gate.box(5, 5, 1, 3, -60, 60, Cell.SOLID);
        gate.set(5, 1, 0, Cell.DOOR);
        gate.set(5, 2, 0, Cell.AIR);
        Route viaGate = plan(gate, rules(0), 10, 1, 0);
        expect(viaGate.reached() && count(viaGate, MoveKind.BREACH) == 1, "doors: a one-block gate with air above is breached too");

        Grid floating = new Grid();
        floating.box(5, 5, 1, 3, -60, 60, Cell.SOLID);
        floating.set(5, 1, 0, Cell.DOOR);
        floating.set(5, 2, 0, Cell.DOOR);
        floating.set(5, 0, 0, Cell.AIR);
        Route hole = plan(floating, rules(0), 10, 1, 0);
        expect(!hole.reached() && count(hole, MoveKind.BREACH) == 0, "doors: a door with no floor behind it is not a way through");
    }

    private static void lava() {
        Grid g = new Grid();
        g.box(3, 8, 0, 0, -25, 25, Cell.LAVA);
        Route r = plan(g, rules(0), 12, 1, 0);
        expect(r.reached(), "lava: a lava lake does not stop him");
        expect(count(r, MoveKind.BRIDGE) == 6, "lava: he freezes a path straight across, " + count(r, MoveKind.BRIDGE) + " bridge blocks");
        expect(r.bridgeCount() == 6, "lava: the route counts its bridge blocks");
        for (Move m : r.moves()) {
            if (m.kind() == MoveKind.BRIDGE) {
                expect(g.at(m.x(), m.y() - 1, m.z()) == Cell.LAVA, "lava: each bridge block is over lava");
            }
        }

        PlanRules few = new PlanRules(5000, 56, 4, 10, 2.5, 3.5, 8, 3, 8, true, true, 3, 0, true);
        Route round = plan(g, few, 12, 1, 0);
        expect(round.reached() && round.bridgeCount() == 0, "lava: too much ice for one plan, so he walks round");
        expect(round.cost() > r.cost(), "lava: which costs more");

        Grid puddle = new Grid();
        puddle.box(3, 8, 0, 0, -1, 1, Cell.LAVA);
        Route walked = plan(puddle, rules(0), 12, 1, 0);
        expect(walked.reached() && walked.bridgeCount() == 0, "lava: a small pool is walked round, not bridged");

        Grid pond = new Grid();
        pond.box(3, 3, 0, 0, -3, 3, Cell.LAVA);
        Route skirt = plan(pond, rules(0), 7, 1, 0);
        expect(skirt.reached() && skirt.bridgeCount() <= 1, "lava: a puddle is bridged or skirted, never walked through");
        for (Move m : skirt.moves()) {
            expect(pond.at(m.x(), m.y(), m.z()) != Cell.LAVA, "lava: nobody steps in it");
        }

        Grid fire = new Grid();
        fire.box(3, 3, 1, 1, -60, 60, Cell.HAZARD);
        Route burnt = plan(fire, rules(0), 7, 1, 0);
        expect(!burnt.reached(), "hazard: a line of fire is never crossed");
    }

    private static void water() {
        Grid g = new Grid();
        g.box(3, 8, 0, 0, -25, 25, Cell.WATER);
        Route r = plan(g, rules(0), 12, 1, 0);
        expect(r.reached() && count(r, MoveKind.BRIDGE) == 6, "water: a lake is bridged rather than swum, " + count(r, MoveKind.BRIDGE) + " bridge blocks");

        PlanRules noIce = rules(0).withoutBridge();
        Route swim = plan(g, noIce, 12, 1, 0);
        expect(swim.reached() && count(swim, MoveKind.SWIM) > 0, "water: without bridges he swims or goes round");

        Grid moving = new Grid();
        moving.box(3, 8, 0, 0, -25, 25, Cell.FLOWING_WATER);
        Route river = plan(moving, rules(0), 12, 1, 0);
        expect(river.reached() && river.bridgeCount() == 0, "water: a river cannot be frozen, only swum");
        expect(count(river, MoveKind.SWIM) >= 5, "water: the swim steps are marked");
    }

    private static void cliffs() {
        Grid g = new Grid();
        g.box(5, 40, 1, 5, -60, 60, Cell.SOLID);
        Route up = plan(g, rules(6), 8, 6, 0);
        expect(up.reached() && count(up, MoveKind.CLIMB) == 1, "cliff: a wall too tall to step is climbed by a pile of bodies");
        Move climb = null;
        for (Move m : up.moves()) {
            if (m.kind() == MoveKind.CLIMB) {
                climb = m;
            }
        }
        expect(climb != null && climb.extra() == 5 && climb.y() == 6 && climb.x() == 5, "cliff: five blocks, landing on top");

        Route few = plan(g, rules(3), 8, 6, 0);
        expect(!few.reached() && count(few, MoveKind.CLIMB) == 0, "cliff: without enough bodies he cannot");
        Route enough = plan(g, rules(StackPlan.crewFor(5)), 8, 6, 0);
        expect(enough.reached() && count(enough, MoveKind.CLIMB) == 1, "cliff: exactly enough is enough");
        Route barely = plan(g, rules(StackPlan.crewFor(5) - 1), 8, 6, 0);
        expect(!barely.reached(), "cliff: one body short is not");

        PlanRules noStack = PlanRules.from(new RimeConfig(), 20, false);
        expect(!plan(g, noStack, 8, 6, 0).reached(), "cliff: and not while stacking is off");

        Grid tall = new Grid();
        tall.box(5, 40, 1, 12, -60, 60, Cell.SOLID);
        expect(!plan(tall, rules(30), 8, 13, 0).reached(), "cliff: too tall even for the biggest pile");

        Grid low = new Grid();
        low.box(5, 40, 1, 2, -60, 60, Cell.SOLID);
        Route two = plan(low, rules(6), 8, 3, 0);
        expect(two.reached() && count(two, MoveKind.CLIMB) == 1 && two.moves().get(two.moves().size() - 1).y() == 3, "cliff: a two-block wall is already too high to step");

        Grid roofed = new Grid();
        roofed.box(5, 40, 1, 5, -60, 60, Cell.SOLID);
        roofed.box(0, 4, 3, 3, -60, 60, Cell.SOLID);
        expect(!plan(roofed, rules(10), 8, 6, 0).reached(), "cliff: no room to build the pile under a low roof");

        Grid cheap = new Grid();
        cheap.box(5, 5, 1, 5, -4, 4, Cell.SOLID);
        Route walkRound = plan(cheap, rules(10), 8, 1, 0);
        expect(walkRound.reached() && count(walkRound, MoveKind.CLIMB) == 0, "cliff: a short wall is walked round, not climbed");
    }

    private static void limits() {
        Grid g = new Grid();
        PlanRules tiny = new PlanRules(200, 56, 4, 10, 2.5, 3.5, 8, 3, 8, true, true, 32, 0, true);
        Route far = plan(g, tiny, 50, 1, 50);
        expect(!far.reached() && far.expanded() <= 200, "limits: a search stops at its node budget");
        expect(!far.moves().isEmpty(), "limits: and still leads toward the goal");
        Move end = far.moves().get(far.moves().size() - 1);
        expect(end.x() + end.z() >= 8, "limits: it got somewhere useful");

        PlanRules small = new PlanRules(5000, 16, 4, 10, 2.5, 3.5, 8, 3, 8, true, true, 32, 0, true);
        Route out = plan(g, small, 100, 1, 0);
        expect(!out.reached(), "limits: a goal outside the search area is not 'reached'");
        for (Move m : out.moves()) {
            expect(Math.abs(m.x()) <= 16 && Math.abs(m.z()) <= 16, "limits: the plan stays inside the search area");
        }

        Route zero = plan(g, rules(0), 0, 1, 0);
        expect(zero.reached() && zero.moves().isEmpty(), "limits: already there means no steps");
        List<Move> none = Route.NONE.moves();
        expect(none.isEmpty() && !Route.NONE.reached(), "limits: the empty route");
    }

    // ------------------------------------------------------------------ what each trick costs

    private static PlanRules plain(int maxBridge) {
        return new PlanRules(5000, 56, 4, 10, 2.5, 3.5, 8, 3, 8, true, true, maxBridge, 0, true);
    }

    private static void prices() {
        // five plain steps and six blocks of ice over water: 5 * 1.0 + 6 * 2.5
        Grid lake = new Grid();
        lake.box(3, 8, 0, 0, -25, 25, Cell.WATER);
        Route water = plan(lake, rules(0), 12, 1, 0);
        expect(water.reached() && near(water.cost(), 20.0), "price: ice over water is 2.5 a block, total " + water.cost());
        Move last = water.moves().get(water.moves().size() - 1);
        expect(last.x() == 11 && last.z() == 0, "price: he stops beside his target, not on it (" + last.x() + "," + last.z() + ")");

        Grid pit = new Grid();
        pit.box(3, 8, 0, 0, -25, 25, Cell.LAVA);
        Route lava = plan(pit, rules(0), 12, 1, 0);
        expect(lava.reached() && near(lava.cost(), 5.0 + 6 * 3.5), "price: ice over lava is 3.5 a block, total " + lava.cost());

        // a one-block step is 1.5
        Grid step = new Grid();
        step.box(3, 40, 1, 1, -60, 60, Cell.SOLID);
        Route up = plan(step, rules(0), 8, 2, 0);
        expect(up.reached() && near(up.cost(), 2 + 1.5 + 4), "price: a one-block step up costs 1.5, total " + up.cost());

        // a two-block drop is 1.0 + 0.5 per block
        Grid down = new Grid();
        down.box(3, 40, -1, 0, -60, 60, Cell.AIR);
        Route drop = plan(down, rules(0), 8, -1, 0);
        expect(drop.reached() && near(drop.cost(), 2 + 2.0 + 4), "price: a drop of two costs 2.0, total " + drop.cost());

        // a five-block wall: walk four, climb (8 + 3 * 5), walk two
        Grid wall = new Grid();
        wall.box(5, 40, 1, 5, -60, 60, Cell.SOLID);
        Route climb = plan(wall, rules(6), 8, 6, 0);
        expect(climb.reached() && near(climb.cost(), 4 + 23.0 + 2), "price: climbing five blocks costs 23, total " + climb.cost());

        // a deep pool he must swim: 4 a block, and he cannot swim upwards cheaply
        Grid pool = new Grid();
        pool.box(3, 8, -3, 3, -25, 25, Cell.WATER);
        pool.box(3, 8, -4, -4, -25, 25, Cell.SOLID);
        Route swim = plan(pool, rules(0).withoutBridge(), 12, 1, 0);
        int swims = count(swim, MoveKind.SWIM);
        expect(swim.reached() && swims == 6 && near(swim.cost(), 2 + 6 * 4.0 + 3), "price: a deep pool is swum at 4 a block, " + swims + " swims, total " + swim.cost());

        // a lava shore is feared: the same walk beside lava costs 1.5 more a step
        Grid shore = new Grid();
        shore.box(0, 10, 1, 1, 1, 1, Cell.LAVA);
        Route along = plan(shore, rules(0), 10, 1, 0);
        expect(along.reached() && near(along.cost(), 10.0), "price: he steps one block clear of a lava wall rather than hug it (" + along.cost() + ")");
        Grid hug = new Grid();
        hug.box(-30, 40, 1, 1, 1, 1, Cell.LAVA);
        hug.box(-30, 40, 1, 3, -1, -1, Cell.SOLID);
        Route tight = plan(hug, rules(0), 10, 1, 0);
        expect(tight.reached() && near(tight.cost(), 9 * 2.5), "price: and where he cannot, each step beside it costs an extra 1.5 (" + tight.cost() + ")");
        Grid fire = new Grid();
        fire.box(-30, 40, 1, 1, 1, 1, Cell.HAZARD);
        fire.box(-30, 40, 1, 3, -1, -1, Cell.SOLID);
        Route burn = plan(fire, rules(0), 10, 1, 0);
        expect(burn.reached() && near(burn.cost(), 9 * 2.5), "price: fire and the like are feared as much as lava (" + burn.cost() + ")");
    }

    private static void shapes() {
        // under a low roof there is no room to stand on a bridge, so he walks round
        Grid roof = new Grid();
        roof.box(3, 8, 0, 0, -25, 25, Cell.WATER);
        roof.box(3, 8, 2, 2, -25, 25, Cell.SOLID);
        Route under = plan(roof, rules(0), 12, 1, 0);
        expect(under.reached() && under.bridgeCount() == 0, "shape: no ice where there is no headroom for it, he goes round");

        // under a low ceiling he cannot jump a one-block step
        Grid low = new Grid();
        low.box(3, 40, 1, 1, -60, 60, Cell.SOLID);
        low.box(0, 2, 3, 3, -60, 60, Cell.SOLID);
        expect(!plan(low, rules(0), 8, 2, 0).reached(), "shape: a step up needs headroom to jump");

        // exactly the most ice he will make is allowed; one block more and he goes round instead
        Grid lake = new Grid();
        lake.box(3, 8, 0, 0, -25, 25, Cell.WATER);
        Route exact = plan(lake, plain(6), 12, 1, 0);
        expect(exact.reached() && exact.bridgeCount() == 6, "shape: a bridge of exactly the limit is built");
        Route over = plan(lake, plain(5), 12, 1, 0);
        expect(over.reached() && over.bridgeCount() == 0, "shape: one block over the limit and he plans without ice");

        // the edge of the search area is inside it
        Grid g = new Grid();
        PlanRules small = new PlanRules(5000, 16, 4, 10, 2.5, 3.5, 8, 3, 8, true, true, 32, 0, true);
        expect(plan(g, small, 17, 1, 0).reached(), "shape: a goal on the edge of the search area (east) is reached");
        expect(plan(g, small, 0, 1, 17).reached(), "shape: and on the edge (south)");
        expect(plan(g, small, -17, 1, 0).reached() && plan(g, small, 0, 1, -17).reached(), "shape: and west and north");
        expect(!plan(g, small, 19, 1, 0).reached(), "shape: but not beyond it");

        // a wall exactly as tall as the most they will climb
        Grid wall = new Grid();
        wall.box(5, 40, 1, 8, -60, 60, Cell.SOLID);
        Route top = plan(wall, rules(30), 8, 9, 0);
        expect(top.reached() && count(top, MoveKind.CLIMB) == 1, "shape: a wall of exactly the greatest height is climbed");
        Grid wall9 = new Grid();
        wall9.box(5, 40, 1, 9, -60, 60, Cell.SOLID);
        expect(!plan(wall9, rules(30), 8, 10, 0).reached(), "shape: and one block more is not");
    }
}
