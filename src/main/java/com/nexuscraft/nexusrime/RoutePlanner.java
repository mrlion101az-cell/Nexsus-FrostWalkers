package com.nexuscraft.nexusrime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The Frost Knight's mind: a search over the world that knows more tricks than walking. Besides
 * steps, it may plan to smash a door, freeze liquid into a bridge, or have the army pile up to climb
 * a wall, and it weighs each trick's cost against simply going round. Pure logic over a {@link Terrain}.
 */
final class RoutePlanner {

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int DOWN = 24;
    private static final int UP = 32;
    private static final int MAX_DROP = 3;
    private static final double LAVA_FEAR = 1.5;

    private RoutePlanner() {
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        double g;
        final double h;
        Node parent;
        Move via;

        Node(int x, int y, int z, double g, double h, Node parent, Move via) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.h = h;
            this.parent = parent;
            this.via = via;
        }
    }

    /** Plans from the start block to the goal block (both are the block a body's feet are in). */
    static Route plan(Terrain t, PlanRules rules, int sx, int sy, int sz, int gx, int gy, int gz) {
        Route route = search(t, rules, sx, sy, sz, gx, gy, gz);
        if (rules.bridge() && route.bridgeCount() > rules.maxBridge()) {
            // Too much ice for one plan: think again without it.
            route = search(t, rules.withoutBridge(), sx, sy, sz, gx, gy, gz);
        }
        return route;
    }

    private static Route search(Terrain t, PlanRules rules, int sx, int sy, int sz, int gx, int gy, int gz) {
        Map<Long, Node> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.g + a.h, b.g + b.h));
        Node start = new Node(sx, sy, sz, 0.0, heuristic(sx, sz, gx, gz), null, null);
        best.put(key(rules, sx, sy, sz, sx, sy, sz), start);
        open.add(start);
        Node closest = start;
        int expanded = 0;
        while (!open.isEmpty() && expanded < rules.maxNodes()) {
            Node n = open.poll();
            Node known = best.get(key(rules, n.x, n.y, n.z, sx, sy, sz));
            if (known != n) {
                continue; // a cheaper way here was found after this entry was queued
            }
            expanded++;
            if (atGoal(n, gx, gy, gz)) {
                return build(n, true, expanded);
            }
            if (n.h < closest.h || (n.h == closest.h && n.g < closest.g)) {
                closest = n;
            }
            for (int[] d : DIRS) {
                expandDir(t, rules, n, d[0], d[1], sx, sy, sz, gx, gz, best, open);
            }
        }
        return build(closest, false, expanded);
    }

    private static boolean atGoal(Node n, int gx, int gy, int gz) {
        return Math.abs(n.x - gx) <= 1 && Math.abs(n.z - gz) <= 1 && Math.abs(n.y - gy) <= 2;
    }

    private static double heuristic(int x, int z, int gx, int gz) {
        return Math.abs(x - gx) + Math.abs(z - gz);
    }

    private static Route build(Node end, boolean reached, int expanded) {
        List<Move> moves = new ArrayList<>();
        for (Node n = end; n != null && n.via != null; n = n.parent) {
            moves.add(n.via);
        }
        Collections.reverse(moves);
        return new Route(reached, moves, end.g, expanded);
    }

    private static long key(PlanRules r, int x, int y, int z, int sx, int sy, int sz) {
        long side = 2L * r.radius() + 1;
        long dx = x - sx + r.radius();
        long dz = z - sz + r.radius();
        long dy = y - sy + DOWN;
        return (dx * side + dz) * (DOWN + UP + 1) + dy;
    }

    private static boolean inBounds(PlanRules r, int x, int y, int z, int sx, int sy, int sz) {
        return Math.abs(x - sx) <= r.radius() && Math.abs(z - sz) <= r.radius() && y >= sy - DOWN && y <= sy + UP;
    }

    // ------------------------------------------------------------------ the moves

    private static boolean standable(Terrain t, int x, int y, int z) {
        Cell feet = t.at(x, y, z);
        Cell head = t.at(x, y + 1, z);
        if (!feet.passable() || !head.passable()) {
            return false;
        }
        return t.at(x, y - 1, z).floor() || feet.swimmable();
    }

    private static void expandDir(Terrain t, PlanRules r, Node n, int dx, int dz, int sx, int sy, int sz, int gx, int gz,
                                  Map<Long, Node> best, PriorityQueue<Node> open) {
        int x = n.x;
        int y = n.y;
        int z = n.z;
        int nx = x + dx;
        int nz = z + dz;

        // flat step, or into water
        if (standable(t, nx, y, nz)) {
            boolean swim = t.at(nx, y, nz).swimmable();
            offer(t, r, n, new Move(swim ? MoveKind.SWIM : MoveKind.WALK, nx, y, nz, 0), swim ? r.swimCost() : 1.0,
                    sx, sy, sz, gx, gz, best, open);
        } else {
            // a drop of up to three blocks
            if (t.at(nx, y, nz).passable() && t.at(nx, y + 1, nz).passable()) {
                for (int k = 1; k <= MAX_DROP; k++) {
                    if (standable(t, nx, y - k, nz)) {
                        boolean swim = t.at(nx, y - k, nz).swimmable();
                        offer(t, r, n, new Move(swim ? MoveKind.SWIM : MoveKind.WALK, nx, y - k, nz, 0),
                                (swim ? r.swimCost() : 1.0) + 0.5 * k, sx, sy, sz, gx, gz, best, open);
                        break;
                    }
                    if (!t.at(nx, y - k, nz).passable()) {
                        break;
                    }
                }
            }
        }

        // step up one block
        if (t.at(x, y + 2, z).passable() && standable(t, nx, y + 1, nz) && !t.at(nx, y, nz).passable()) {
            offer(t, r, n, new Move(MoveKind.WALK, nx, y + 1, nz, 0), 1.5, sx, sy, sz, gx, gz, best, open);
        }

        // a closed door in the way
        if (r.breach() && t.at(nx, y, nz) == Cell.DOOR && t.at(nx, y - 1, nz).floor()) {
            Cell head = t.at(nx, y + 1, nz);
            if (head == Cell.DOOR || head.passable()) {
                offer(t, r, n, new Move(MoveKind.BREACH, nx, y, nz, 0), 1.0 + r.doorCost(), sx, sy, sz, gx, gz, best, open);
            }
        }

        // liquid underfoot: freeze it
        if (r.bridge() && t.at(nx, y, nz) == Cell.AIR && t.at(nx, y + 1, nz) == Cell.AIR) {
            Cell below = t.at(nx, y - 1, nz);
            if (below == Cell.WATER || below == Cell.LAVA) {
                offer(t, r, n, new Move(MoveKind.BRIDGE, nx, y, nz, 0), below == Cell.LAVA ? r.bridgeLavaCost() : r.bridgeWaterCost(),
                        sx, sy, sz, gx, gz, best, open);
            }
        }

        // a wall too tall to step: pile up
        if (r.climb() && !t.at(nx, y + 1, nz).passable()) {
            for (int h = 2; h <= r.maxClimb(); h++) {
                if (r.crew() < StackPlan.crewFor(h)) {
                    break;
                }
                if (!t.at(x, y + h, z).passable() || !t.at(x, y + h + 1, z).passable()) {
                    break; // no room above the foot of the wall to build the pile
                }
                if (standable(t, nx, y + h, nz)) {
                    offer(t, r, n, new Move(MoveKind.CLIMB, nx, y + h, nz, h), r.climbBase() + r.climbPerBlock() * h,
                            sx, sy, sz, gx, gz, best, open);
                    break;
                }
            }
        }
    }

    private static void offer(Terrain t, PlanRules r, Node from, Move move, double cost, int sx, int sy, int sz, int gx, int gz,
                              Map<Long, Node> best, PriorityQueue<Node> open) {
        if (!inBounds(r, move.x(), move.y(), move.z(), sx, sy, sz)) {
            return;
        }
        double total = from.g + cost + (nearLava(t, move) ? LAVA_FEAR : 0.0);
        long k = key(r, move.x(), move.y(), move.z(), sx, sy, sz);
        Node old = best.get(k);
        if (old != null && old.g <= total) {
            return;
        }
        Node node = new Node(move.x(), move.y(), move.z(), total, heuristic(move.x(), move.z(), gx, gz), from, move);
        best.put(k, node);
        open.add(node);
    }

    private static boolean nearLava(Terrain t, Move m) {
        for (int[] d : DIRS) {
            Cell c = t.at(m.x() + d[0], m.y(), m.z() + d[1]);
            if (c == Cell.LAVA || c == Cell.HAZARD) {
                return true;
            }
        }
        return false;
    }
}
