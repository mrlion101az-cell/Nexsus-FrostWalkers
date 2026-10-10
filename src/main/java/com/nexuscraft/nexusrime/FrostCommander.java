package com.nexuscraft.nexusrime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Frost Knight's will, applied to the world. Once a second the director hands over the horde and
 * this class plans a route with {@link RoutePlanner}, walks the Knight along it, and tells the army
 * what to do at each obstacle: smash the door, pile up against the wall, or wait while the Knight
 * freezes a bridge over water or lava. The thinking is in the pure classes; this is the hands.
 */
final class FrostCommander {

    private static final int SECOND = 20;
    private static final double NEAR = 2.6;
    private static final long SHOUT_GAP = 45 * SECOND;
    private static final double SHOUT_RANGE = 64.0;
    private static final double RAISE_RADIUS = 14.0;

    private final RimeConfig cfg;
    private final ZoneGuard zones;
    private final BridgeLedger ledger;
    private final Random random;

    FrostCommander(RimeConfig cfg, ZoneGuard zones, BridgeLedger ledger, Random random) {
        this.cfg = cfg;
        this.zones = zones;
        this.ledger = ledger;
        this.random = random;
    }

    // ------------------------------------------------------------------ the one-second tick

    /**
     * Commands the army for one second.
     *
     * @return false if the Knight could not make sense of the ground, so the director should fall
     *         back to the plain march for now
     */
    boolean lead(Horde h, World world, Mob knight, Player target, List<Mob> wights) {
        if (h.mind == null) {
            h.mind = new KnightMind();
        }
        final KnightMind m = h.mind;
        long now = h.ticksLived;
        Location kl = knight.getLocation();
        Location tl = target.getLocation();
        if (kl == null || tl == null || kl.getWorld() == null || tl.getWorld() == null
                || !tl.getWorld().getName().equals(kl.getWorld().getName())) {
            return false;
        }
        if (now < m.fallbackUntil) {
            return false;
        }

        Terrain terrain = new Overlay(new WorldTerrain(world, zones), m.failedDoors);
        int[] goal = feet(terrain, tl);
        if (!m.jobActive() && needsPlan(m, now, goal)) {
            replan(h, m, terrain, kl, goal, wights.size(), now);
        }

        List<Move> moves = m.route.moves();
        if (!m.jobActive()) {
            advance(m, terrain, kl);
            placeIce(h, m, world, knight, moves);
        }
        Move cur = m.cursor < moves.size() ? moves.get(m.cursor) : null;

        boolean waiting = false;
        if (m.breach != null) {
            runBreach(h, m, world, terrain, knight, wights, now);
        } else if (m.stack != null) {
            runStack(h, m, world, terrain, knight, wights, now);
        } else if (cur == null) {
            arrived(m, knight, target, now);
        } else {
            switch (cur.kind()) {
                case WALK, SWIM -> walkAhead(m, knight, moves);
                case BRIDGE -> waiting = crossIce(m, world, knight, cur, moves);
                case BREACH -> approach(m, knight, originOf(m, moves), () -> startBreach(h, m, world, knight, cur, now));
                case CLIMB -> approach(m, knight, originOf(m, moves), () -> startStack(h, m, knight, cur, wights.size(), now));
                default -> {
                    // no other kinds exist
                }
            }
        }
        orders(m, terrain, knight, target, wights);
        engage(m, knight, target);
        checkStuck(m, knight, target, waiting, now);
        return true;
    }

    /** Puts back everything the Knight froze when his horde ends. (A pile's crew is not freed: every Rimebound crumbles then.) */
    void release(Horde h) {
        ledger.restoreHorde(h.id.toString());
    }

    // ------------------------------------------------------------------ planning

    private boolean needsPlan(KnightMind m, long now, int[] goal) {
        if (m.route == Route.NONE || m.goalX == Integer.MIN_VALUE) {
            return true;
        }
        if (now >= m.nextPlanTick) {
            return true;
        }
        int moved = Math.abs(goal[0] - m.goalX) + Math.abs(goal[2] - m.goalZ);
        return moved > 6;
    }

    private void replan(Horde h, KnightMind m, Terrain terrain, Location kl, int[] goal, int army, long now) {
        int[] start = feet(terrain, kl);
        PlanRules rules = PlanRules.from(cfg, army, now >= m.noClimbUntil);
        m.route = RoutePlanner.plan(terrain, rules, start[0], start[1], start[2], goal[0], goal[1], goal[2]);
        m.cursor = 0;
        m.startX = start[0];
        m.startY = start[1];
        m.startZ = start[2];
        m.goalX = goal[0];
        m.goalY = goal[1];
        m.goalZ = goal[2];
        m.nextPlanTick = now + (long) cfg.knightPlanSeconds * SECOND;
    }

    /** The block a body's feet are in: if the position is inside a solid block (a slab), one up. */
    private static int[] feet(Terrain t, Location l) {
        int x = (int) Math.floor(l.getX());
        int y = (int) Math.floor(l.getY());
        int z = (int) Math.floor(l.getZ());
        if (!t.at(x, y, z).passable() && t.at(x, y + 1, z).passable()) {
            y++;
        }
        return new int[] {x, y, z};
    }

    /** Moves the cursor past every plain step the Knight has already reached. */
    private void advance(KnightMind m, Terrain terrain, Location kl) {
        List<Move> moves = m.route.moves();
        int limit = Math.min(moves.size() - 1, m.cursor + 5);
        int furthest = -1;
        for (int j = m.cursor; j <= limit; j++) {
            Move mv = moves.get(j);
            if (mv.kind() == MoveKind.BREACH || mv.kind() == MoveKind.CLIMB) {
                break; // only the job that clears it can move the cursor past one of these
            }
            if (mv.kind() == MoveKind.BRIDGE && !terrain.at(mv.x(), mv.y() - 1, mv.z()).floor()) {
                break; // there is no ice there yet, so he cannot have crossed it
            }
            if (reached(kl, mv)) {
                furthest = j;
            }
        }
        if (furthest >= 0) {
            m.cursor = furthest + 1;
        }
    }

    static boolean reached(Location at, Move mv) {
        return Math.abs(at.getX() - (mv.x() + 0.5)) <= 1.1 && Math.abs(at.getZ() - (mv.z() + 0.5)) <= 1.1
                && Math.abs(at.getY() - mv.y()) <= 1.6;
    }

    /** The cell the army stands in just before the move at the cursor. */
    private static int[] originOf(KnightMind m, List<Move> moves) {
        if (m.cursor > 0) {
            Move p = moves.get(m.cursor - 1);
            return new int[] {p.x(), p.y(), p.z()};
        }
        return new int[] {m.startX, m.startY, m.startZ};
    }

    private static Location centre(World w, int[] c) {
        return new Location(w, c[0] + 0.5, c[1], c[2] + 0.5);
    }

    // ------------------------------------------------------------------ the Knight's own steps

    private void walkAhead(KnightMind m, Mob knight, List<Move> moves) {
        int j = m.cursor;
        while (j + 1 < moves.size() && j - m.cursor < 3) {
            Move next = moves.get(j + 1);
            if (next.kind() != MoveKind.WALK && next.kind() != MoveKind.SWIM) {
                break;
            }
            j++;
        }
        Move mv = moves.get(j);
        knight.getPathfinder().moveTo(new Location(knight.getLocation().getWorld(), mv.x() + 0.5, mv.y(), mv.z() + 0.5), 1.0);
    }

    private void approach(KnightMind m, Mob knight, int[] origin, Runnable atOrigin) {
        Location kl = knight.getLocation();
        if (nearOrigin(kl, origin)) {
            atOrigin.run();
        } else {
            knight.getPathfinder().moveTo(centre(kl.getWorld(), origin), 1.0);
        }
    }

    /** Is the Knight close enough to the cell where the army gathers for a job to begin? */
    static boolean nearOrigin(Location kl, int[] origin) {
        double flat = Math.hypot(kl.getX() - (origin[0] + 0.5), kl.getZ() - (origin[2] + 0.5));
        return flat <= NEAR && Math.abs(kl.getY() - origin[1]) <= 2.0;
    }

    private void arrived(KnightMind m, Mob knight, Player target, long now) {
        if (!m.route.reached()) {
            // the plan only got him part of the way: look again soon, from where he stands now
            m.nextPlanTick = Math.min(m.nextPlanTick, now + 2L * SECOND);
        }
        knight.getPathfinder().stopPathfinding();
    }

    // ------------------------------------------------------------------ frost bridges

    /** Freezes the liquid under the next few bridge steps that are within reach. */
    private void placeIce(Horde h, KnightMind m, World world, Mob knight, List<Move> moves) {
        if (!cfg.bridgeEnabled) {
            return;
        }
        Material ice = Material.matchMaterial(cfg.bridgeMaterial);
        if (ice == null) {
            ice = Material.PACKED_ICE;
        }
        Location kl = knight.getLocation();
        int placed = 0;
        String liquid = null;
        for (int j = m.cursor; j < moves.size() && j - m.cursor < 48 && placed < cfg.bridgeBlocksPerSecond; j++) {
            Move mv = moves.get(j);
            if (mv.kind() == MoveKind.BREACH || mv.kind() == MoveKind.CLIMB) {
                break;
            }
            if (mv.kind() != MoveKind.BRIDGE) {
                continue;
            }
            double reach = Math.hypot(kl.getX() - (mv.x() + 0.5), kl.getZ() - (mv.z() + 0.5));
            if (reach > cfg.bridgeReach || m.bridged >= cfg.bridgeMaxBlocks * 3) {
                break;
            }
            Material before = world.getBlockAt(mv.x(), mv.y() - 1, mv.z()).getType();
            if (ledger.place(world, mv.x(), mv.y() - 1, mv.z(), ice, (long) cfg.bridgeLifetimeSeconds * 1000L, h.id.toString())) {
                placed++;
                m.bridged++;
                liquid = before == Material.LAVA ? "lava" : "water";
                world.spawnParticle(Particle.SNOWFLAKE, new Location(world, mv.x() + 0.5, mv.y(), mv.z() + 0.5), 12, 0.3, 0.2, 0.3);
            }
        }
        if (placed > 0) {
            world.playSound(kl, Sound.BLOCK_GLASS_PLACE, 1.0f, 0.7f);
            shout(h, m, world, knight, "bridge", cfg.msgKnightBridge.replace("{liquid}", liquid == null ? "water" : liquid));
        }
    }

    /** The next step is onto ice: walk on once it is there, wait for it if it is not. */
    private boolean crossIce(KnightMind m, World world, Mob knight, Move cur, List<Move> moves) {
        boolean solid = !world.getBlockAt(cur.x(), cur.y() - 1, cur.z()).isLiquid(); // placed ice is not liquid
        if (solid) {
            walkAhead(m, knight, moves);
            return false;
        }
        knight.getPathfinder().stopPathfinding();
        return true;
    }

    // ------------------------------------------------------------------ sieges

    private void startBreach(Horde h, KnightMind m, World world, Mob knight, Move door, long now) {
        Block b = world.getBlockAt(door.x(), door.y(), door.z());
        String name = b.getType() == null ? "" : b.getType().name();
        m.breachMove = door;
        m.breach = new BreachJob(BreachJob.strengthOf(cfg, name), cfg.breachTimeoutSeconds, cfg.breachMaxContributors,
                cfg.breachPerWight, cfg.breachKnightPower);
        m.crew.clear();
        shout(h, m, world, knight, "breach", cfg.msgKnightBreach);
    }

    private void runBreach(Horde h, KnightMind m, World world, Terrain terrain, Mob knight, List<Mob> wights, long now) {
        Move door = m.breachMove;
        Location doorAt = new Location(world, door.x() + 0.5, door.y() + 0.5, door.z() + 0.5);
        int[] origin = originOf(m, m.route.moves());
        Location front = centre(world, origin);

        // the nearest bodies are the crew
        List<Mob> sorted = new ArrayList<>(wights);
        sorted.sort(Comparator.comparingDouble(w -> HordeDirector.distance(w.getLocation(), doorAt)));
        m.crew.clear();
        int atDoor = 0;
        for (int i = 0; i < sorted.size() && i < cfg.breachCrew; i++) {
            Mob w = sorted.get(i);
            m.crew.add(w.getUniqueId());
            w.setTarget(null);
            w.getPathfinder().moveTo(spot(terrain, front, 0.9), 1.0);
            Location wl = w.getLocation();
            if (Math.hypot(wl.getX() - doorAt.getX(), wl.getZ() - doorAt.getZ()) <= 3.0 && Math.abs(wl.getY() - doorAt.getY()) <= 3.0) {
                atDoor++;
            }
        }
        Location kl = knight.getLocation();
        boolean knightAt = Math.hypot(kl.getX() - doorAt.getX(), kl.getZ() - doorAt.getZ()) <= 3.5;
        BreachJob.State s = m.breach.tick(atDoor, knightAt);
        world.playSound(doorAt, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
        world.spawnParticle(Particle.SNOWFLAKE, doorAt, 8, 0.4, 0.5, 0.4);
        if (s == BreachJob.State.BROKEN) {
            if (smash(world, door)) {
                m.cursor++;
            } else {
                m.failedDoors.add(doorKey(door));
            }
            m.breach = null;
            m.breachMove = null;
            m.crew.clear();
            m.nextPlanTick = now;
        } else if (s == BreachJob.State.FAILED) {
            m.failedDoors.add(doorKey(door));
            m.breach = null;
            m.breachMove = null;
            m.crew.clear();
            m.nextPlanTick = now;
        }
    }

    private static long doorKey(Move door) {
        return ((long) (door.x() & 0x3FFFFFF) << 38) | ((long) (door.z() & 0x3FFFFFF) << 12) | (long) ((door.y() + 64) & 0xFFF);
    }

    /** Breaks the door (both halves) and drops it. Never on safe ground. */
    private boolean smash(World world, Move door) {
        Block feet = world.getBlockAt(door.x(), door.y(), door.z());
        if (zones.isProtected(feet.getLocation())) {
            return false;
        }
        String name = feet.getType() == null ? "" : feet.getType().name();
        if (!(name.endsWith("_DOOR") || name.endsWith("_FENCE_GATE") || name.endsWith("_TRAPDOOR"))) {
            return true; // someone already took it away
        }
        Block head = feet.getRelative(0, 1, 0);
        feet.breakNaturally();
        if (head.getType() != null && head.getType().name().equals(name)) {
            head.breakNaturally();
        }
        world.playSound(feet.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.2f, 0.9f);
        world.spawnParticle(Particle.SNOWFLAKE, feet.getLocation().add(0.5, 1.0, 0.5), 30, 0.5, 0.8, 0.5);
        return true;
    }

    // ------------------------------------------------------------------ living ladders

    private void startStack(Horde h, KnightMind m, Mob knight, Move climb, int army, long now) {
        int needed = StackPlan.crewFor(climb.extra());
        if (army < needed) {
            m.noClimbUntil = now + 60L * SECOND;
            m.nextPlanTick = now;
            return;
        }
        m.stackMove = climb;
        m.stack = new StackJob(needed, cfg.stackGatherSeconds, cfg.stackBuildSeconds);
        m.crew.clear();
        shout(h, m, knight.getLocation().getWorld(), knight, "stack", cfg.msgKnightStack);
    }

    private void runStack(Horde h, KnightMind m, World world, Terrain terrain, Mob knight, List<Mob> wights, long now) {
        Move climb = m.stackMove;
        int[] base = originOf(m, m.route.moves());
        Location foot = centre(world, base);
        Location top = new Location(world, climb.x() + 0.5, climb.y(), climb.z() + 0.5);
        StackJob job = m.stack;
        int below = StackPlan.stepsBelow(top.getY() - foot.getY());

        // keep a crew: the nearest bodies to the foot of the wall
        List<Mob> sorted = new ArrayList<>(wights);
        sorted.sort(Comparator.comparingDouble(w -> HordeDirector.distance(w.getLocation(), foot)));
        Map<UUID, Mob> byId = new HashMap<>();
        for (Mob w : sorted) {
            byId.put(w.getUniqueId(), w);
        }
        byId.put(knight.getUniqueId(), knight);
        if (job.phase() == StackJob.Phase.GATHER) {
            m.crew.clear();
            for (int i = 0; i < sorted.size() && i < job.needed; i++) {
                m.crew.add(sorted.get(i).getUniqueId());
            }
        }
        // a climber who died on the way up is simply gone
        m.steps.keySet().removeIf(id -> !byId.containsKey(id));
        m.held.removeIf(id -> !byId.containsKey(id));

        int ready = 0;
        List<Mob> crewMobs = new ArrayList<>();
        for (UUID id : m.crew) {
            Mob w = byId.get(id);
            if (w == null) {
                continue;
            }
            crewMobs.add(w);
            Location wl = w.getLocation();
            if (Math.hypot(wl.getX() - foot.getX(), wl.getZ() - foot.getZ()) <= 3.0 && Math.abs(wl.getY() - foot.getY()) <= 2.5) {
                ready++;
            }
        }
        List<Mob> ground = new ArrayList<>();
        for (Mob w : sorted) {
            UUID id = w.getUniqueId();
            if (!m.crew.contains(id) && !m.steps.containsKey(id)
                    && HordeDirector.distance(w.getLocation(), foot) <= RAISE_RADIUS && w.getLocation().getY() < top.getY() - 1.0) {
                ground.add(w);
            }
        }

        int waiting = ground.size() + m.steps.size() + (m.crewGo ? 0 : crewMobs.size());
        StackJob.Phase p = job.tick(ready, waiting);
        switch (p) {
            case GATHER -> {
                for (Mob w : crewMobs) {
                    w.setTarget(null);
                    w.getPathfinder().moveTo(spot(terrain, foot, 0.8), 1.0);
                }
            }
            case BUILD -> {
                // the pile grows a body at a time; the rest are still shuffling in
                int want = job.placedFor(crewMobs.size());
                for (int i = 0; i < crewMobs.size(); i++) {
                    Mob w = crewMobs.get(i);
                    if (i < want) {
                        boolean fresh = !m.held.contains(w.getUniqueId());
                        Location at = slot(world, foot, top, i, i);
                        hold(m, w, at);
                        if (fresh) {
                            world.playSound(at, Sound.BLOCK_SNOW_PLACE, 1.0f, 0.6f + 0.08f * i);
                            world.playSound(at, Sound.ENTITY_ZOMBIE_AMBIENT, 0.8f, 0.7f);
                            world.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3);
                        }
                    } else {
                        w.setTarget(null);
                        w.getPathfinder().moveTo(spot(terrain, foot, 0.8), 1.0);
                    }
                }
                world.spawnParticle(Particle.SNOWFLAKE, foot.clone().add(0, 1.0 + StackPlan.heightOf(Math.max(0, want - 1)) / 2, 0), 20, 0.5, 1.5, 0.5);
            }
            case ASCEND -> {
                boolean moved = false;
                // 1. everyone already on the way up takes the next step
                for (UUID id : new ArrayList<>(m.steps.keySet())) {
                    moved |= hop(m, world, foot, top, below, byId.get(id), m.steps.get(id) + 1);
                }
                // 2. the Knight goes first, then the army, a few a second
                int budget = cfg.stackPerSecond;
                if (budget > 0 && !m.steps.containsKey(knight.getUniqueId()) && knight.getLocation().getY() < top.getY() - 1.0) {
                    moved |= hop(m, world, foot, top, below, knight, 1);
                    budget--;
                }
                int startedGround = 0;
                for (Mob w : ground) {
                    if (budget <= 0) {
                        break;
                    }
                    budget--;
                    startedGround++;
                    moved |= hop(m, world, foot, top, below, w, 1);
                }
                // 3. the pile itself climbs last, once nobody else is left below it
                boolean othersDone = startedGround == ground.size();
                for (UUID id : m.steps.keySet()) {
                    othersDone &= m.crew.contains(id);
                }
                if (!m.crewGo && othersDone) {
                    m.crewGo = true;
                    for (int i = crewMobs.size() - 1; i >= 0; i--) {
                        moved |= hop(m, world, foot, top, below, crewMobs.get(i), i + 1);
                    }
                }
                if (moved) {
                    world.playSound(foot, Sound.ENTITY_ZOMBIE_STEP, 1.0f, 0.8f);
                    world.spawnParticle(Particle.SNOWFLAKE, foot.clone().add(0, 1.0 + (top.getY() - foot.getY()) / 2, 0), 24, 0.5, 1.5, 0.5);
                }
            }
            case DONE -> {
                // whoever has not been passed up yet goes now, so nobody is left frozen in the air
                List<Mob> left = new ArrayList<>();
                for (UUID id : m.steps.keySet()) {
                    Mob w = byId.get(id);
                    if (w != null) {
                        left.add(w);
                    }
                }
                for (Mob w : crewMobs) {
                    if (!m.steps.containsKey(w.getUniqueId()) && !atTop(w.getLocation(), top)
                            && HordeDirector.distance(w.getLocation(), foot) <= RAISE_RADIUS) {
                        left.add(w);
                    }
                }
                thaw(m);
                for (Mob w : left) {
                    lift(w, top);
                }
                clearStack(m);
                m.nextPlanTick = now;
            }
            case FAILED -> {
                // nobody is frozen yet: a pile only fails while it is still gathering
                m.noClimbUntil = now + 60L * SECOND;
                clearStack(m);
                m.nextPlanTick = now;
            }
            default -> {
                // nothing
            }
        }
    }

    private static void clearStack(KnightMind m) {
        m.stack = null;
        m.stackMove = null;
        m.crew.clear();
        m.steps.clear();
        m.held.clear();
        m.crewGo = false;
    }

    /** Already standing where the pile delivers its climbers (they land within a third of a block of it). */
    static boolean atTop(Location at, Location top) {
        return Math.hypot(at.getX() - top.getX(), at.getZ() - top.getZ()) <= 0.75 && Math.abs(at.getY() - top.getY()) <= 1.0;
    }

    /** Where a body stands in the pile at {@code step}: in the column at the foot of the wall, leaning a little, looking up the wall. */
    private static Location slot(World world, Location foot, Location top, int step, int salt) {
        double dx = top.getX() - foot.getX();
        double dz = top.getZ() - foot.getZ();
        double len = Math.hypot(dx, dz);
        double side = StackPlan.stagger(salt);
        double px = len < 1e-6 ? 0 : -dz / len * side;
        double pz = len < 1e-6 ? 0 : dx / len * side;
        return new Location(world, foot.getX() + px, foot.getY() + StackPlan.heightOf(step), foot.getZ() + pz,
                StackPlan.yawToward(dx, dz), -25f);
    }

    /**
     * Moves one climber a step up the side of the pile, or onto the wall if that was the last step.
     *
     * @return whether anybody moved
     */
    private boolean hop(KnightMind m, World world, Location foot, Location top, int below, Mob w, int next) {
        if (w == null) {
            return false;
        }
        UUID id = w.getUniqueId();
        if (next > below) {
            m.steps.remove(id);
            thawOne(m, w);
            lift(w, top);
            world.spawnParticle(Particle.CLOUD, top.clone().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3);
            return true;
        }
        m.steps.put(id, next);
        // the climber takes the side the pile is not leaning to, so the two columns do not merge
        hold(m, w, slot(world, foot, top, next, m.crew.contains(id) ? next : next + 1));
        return true;
    }

    /** Holds a body still in the pile (and lights it up so the stack shows through the wall). */
    private void hold(KnightMind m, Mob w, Location at) {
        m.held.add(w.getUniqueId());
        w.setAI(false);
        w.setGravity(false);
        w.setVelocity(new Vector(0, 0, 0));
        w.getPathfinder().stopPathfinding();
        w.teleport(at);
        if (cfg.stackGlow) {
            w.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
        }
    }

    /** Passes a body up to the top of the wall. */
    private void lift(Mob w, Location top) {
        w.setFallDistance(0f);
        w.getPathfinder().stopPathfinding();
        w.teleport(jitter(top, 0.35));
    }

    /** Gives one body its legs back. */
    private static void thawOne(KnightMind m, Mob w) {
        if (m.held.remove(w.getUniqueId())) {
            w.setAI(true);
            w.setGravity(true);
        }
    }

    /** Gives every held body its legs back (does nothing if none are held). */
    private static void thaw(KnightMind m) {
        for (UUID id : m.held) {
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(id);
            if (e instanceof Mob w) {
                w.setAI(true);
                w.setGravity(true);
            }
        }
        m.held.clear();
    }

    /** The director calls this when the Knight is not leading (no target, or a stuck fallback): nobody stays frozen or half-piled. */
    void thaw(Horde h) {
        if (h.mind == null || (h.mind.held.isEmpty() && h.mind.stack == null)) {
            return;
        }
        thaw(h.mind);
        if (h.mind.stack != null) {
            clearStack(h.mind);
            h.mind.nextPlanTick = h.ticksLived;
        }
    }

    // ------------------------------------------------------------------ the rest of the army

    /** Everyone not on a job follows the Knight, and fights when the target is close. */
    private void orders(KnightMind m, Terrain terrain, Mob knight, Player target, List<Mob> wights) {
        Location kl = knight.getLocation();
        for (Mob w : wights) {
            if (m.crew.contains(w.getUniqueId()) || m.steps.containsKey(w.getUniqueId())) {
                continue;
            }
            Location wl = w.getLocation();
            double toTarget = HordeDirector.distance(wl, target.getLocation());
            if (toTarget <= cfg.knightEngageDistance) {
                w.setTarget(target);
                continue;
            }
            LivingEntity current = w.getTarget();
            if (current != null && HordeDirector.distance(wl, current.getLocation()) > 10.0) {
                w.setTarget(null);
            }
            if (HordeDirector.distance(wl, kl) > 5.0) {
                w.getPathfinder().moveTo(spot(terrain, kl, 2.5), 1.0);
            }
        }
    }

    /** The Knight only fights the target himself once he has a clear way to it. */
    private void engage(KnightMind m, Mob knight, Player target) {
        boolean close = HordeDirector.distance(knight.getLocation(), target.getLocation()) <= cfg.knightEngageDistance;
        boolean clear = !hasSpecialAhead(m);
        if (close && clear) {
            knight.setTarget(target);
        } else {
            knight.setTarget(null);
        }
    }

    private boolean hasSpecialAhead(KnightMind m) {
        List<Move> moves = m.route.moves();
        for (int j = m.cursor; j < moves.size(); j++) {
            if (moves.get(j).special()) {
                return true;
            }
        }
        return false;
    }

    private void checkStuck(KnightMind m, Mob knight, Player target, boolean waiting, long now) {
        Location kl = knight.getLocation();
        boolean moved = Double.isNaN(m.lastX) || Math.hypot(kl.getX() - m.lastX, kl.getZ() - m.lastZ) >= 0.5;
        m.lastX = kl.getX();
        m.lastZ = kl.getZ();
        boolean excused = m.jobActive() || waiting || knight.getTarget() != null
                || HordeDirector.distance(kl, target.getLocation()) <= cfg.knightEngageDistance;
        if (moved) {
            m.movingSeconds++;
            if (m.movingSeconds >= 8) {
                m.stuckReplans = 0; // he has been getting somewhere: forgive the earlier trouble
            }
        } else {
            m.movingSeconds = 0;
        }
        if (moved || excused) {
            m.stuckSeconds = 0;
            return;
        }
        m.stuckSeconds++;
        if (m.stuckSeconds >= cfg.knightMaxStuckSeconds) {
            m.stuckSeconds = 0;
            m.stuckReplans++;
            m.nextPlanTick = now;
            if (m.stuckReplans >= 3) {
                m.stuckReplans = 0;
                m.fallbackUntil = now + 15L * SECOND;
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    /**
     * A place near {@code at} where a body can really stand (dry, open, with ground under it), so
     * the army is never told to gather over lava, water or inside a wall. Falls back to {@code at}.
     */
    private Location spot(Terrain t, Location at, double spread) {
        int y = (int) Math.floor(at.getY());
        for (int i = 0; i < 6; i++) {
            Location pick = jitter(at, spread);
            int x = (int) Math.floor(pick.getX());
            int z = (int) Math.floor(pick.getZ());
            if (t.at(x, y, z) == Cell.AIR && t.at(x, y + 1, z) == Cell.AIR && t.at(x, y - 1, z).floor()) {
                return pick;
            }
        }
        return at;
    }

    private Location jitter(Location at, double spread) {
        return new Location(at.getWorld(), at.getX() + (random.nextDouble() - 0.5) * 2 * spread, at.getY(),
                at.getZ() + (random.nextDouble() - 0.5) * 2 * spread);
    }

    private void shout(Horde h, KnightMind m, World world, Mob knight, String kind, String template) {
        long now = h.ticksLived;
        Long last = m.lastShout.get(kind);
        if (last != null && now - last < SHOUT_GAP) {
            return;
        }
        m.lastShout.put(kind, now);
        String line = cfg.fill(template, h.targetName, h.worldName, cfg.knightName);
        for (Player p : world.getPlayers()) {
            if (HordeDirector.distance(p.getLocation(), knight.getLocation()) <= SHOUT_RANGE) {
                p.sendMessage(line);
            }
        }
    }

    /** The real terrain, except that doors which held out against a siege count as walls. */
    private static final class Overlay implements Terrain {
        private final Terrain base;
        private final java.util.Set<Long> walls;

        Overlay(Terrain base, java.util.Set<Long> walls) {
            this.base = base;
            this.walls = walls;
        }

        @Override
        public Cell at(int x, int y, int z) {
            Cell c = base.at(x, y, z);
            if (c == Cell.DOOR && !walls.isEmpty()) {
                long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 64) & 0xFFF);
                long lower = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y - 1 + 64) & 0xFFF);
                if (walls.contains(key) || walls.contains(lower)) {
                    return Cell.SOLID;
                }
            }
            return c;
        }
    }
}
