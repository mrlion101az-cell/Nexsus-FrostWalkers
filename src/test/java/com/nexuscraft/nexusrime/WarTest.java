package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Stray;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrime.Kit.WORLD;
import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/**
 * The Frost Knight at war in a stub world. Creatures here move only as far as their last order says
 * (a few blocks a second), so these tests check what he decides and how the army carries it out:
 * the door that gets smashed, the pile that forms, the ice that appears.
 */
public final class WarTest {

    static final int OX = 600;
    static final int OZ = 600;
    /** The ground is stone up to and including this level, so feet stand at GROUND + 1. */
    static final int GROUND = 63;
    static final int FEET = GROUND + 1;

    public static void main(String[] args) {
        openGround();
        doorSiege();
        ironDoor();
        claimedDoor();
        hopelessDoor();
        lava();
        water();
        swimming();
        cliff();
        smallArmy();
        stuck();
        ending();
        followers();
        sieges();
        patience();
        thaw();
        ladder();
        patientSiege();
        shortSieges();
        crewChoice();
        gatherFails();
        strictLadder(12, "pile", 5, 5);
        strictLadder(4, "exact crew", 5, 5);
        strictLadder(12, "slow lifts", 1, 5);
        strictLadder(12, "low wall", 1, 3);
        strictLadder(12, "tall wall", 3, 8);
        twoClimbs();
        pileTimeout();
        pileDark();
        pileCasualty();
        pileAbandoned();
        manners();
        nerves();
        ice();
        Kit.finish();
    }

    // ------------------------------------------------------------------ the stub world

    /** Flat stone ground, with the horde's world height set so creatures stand on it. */
    static Env arena() {
        Env env = new Env();
        WORLD.stubProvider = (x, y, z) -> y <= GROUND ? Material.STONE : Material.AIR;
        WORLD.heightMap = (x, z) -> GROUND;
        env.cfg.knightChance = 100.0;
        env.cfg.wightsBase = 12;
        env.cfg.wightsPerPlayer = 0;
        return env;
    }

    /** Starts a Knight horde, then puts the Knight and his army where the test wants them. */
    static Horde muster(Env env, Kit.TestPlayer target, double kx, double ky, double kz) {
        Horde h = KnightTest.start(env, target, Boolean.TRUE).horde();
        Mob knight = (Mob) env.walkerOf(h);
        knight.teleport(new Location(WORLD, kx, ky, kz));
        for (UUID id : h.wights) {
            Entity w = Bukkit.getEntity(id);
            w.teleport(new Location(WORLD, kx + (Math.abs(id.hashCode()) % 7) - 3, ky, kz + (Math.abs(id.hashCode() / 7) % 7) - 3));
        }
        return h;
    }

    /** One second of the director, then every creature walks a few blocks toward where it was last told to go. */
    static void second(Env env, double speed) {
        env.tick(1);
        for (Entity e : new ArrayList<>(Bukkit.ENTITIES.values())) {
            if (!(e instanceof Mob mob) || !env.director.isHordeCreature(mob) || !mob.hasAI()) {
                continue;
            }
            com.destroystokyo.paper.entity.Pathfinder pf = mob.getPathfinder();
            if (!pf.hasPath() || pf.lastLocation == null) {
                continue;
            }
            Location at = mob.getLocation();
            Location to = pf.lastLocation;
            double dx = to.getX() - at.getX();
            double dz = to.getZ() - at.getZ();
            double d = Math.hypot(dx, dz);
            double f = d <= speed ? 1.0 : speed / d;
            mob.teleport(new Location(WORLD, at.getX() + dx * f, to.getY(), at.getZ() + dz * f));
        }
    }

    static void seconds(Env env, int n, double speed) {
        for (int i = 0; i < n; i++) {
            second(env, speed);
        }
    }

    static double flat(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    static Material block(int x, int y, int z) {
        return WORLD.getBlockAt(x, y, z).getType();
    }

    static int drops(Material m) {
        int n = 0;
        for (ItemStack s : WORLD.stubDrops) {
            if (s.getType() == m) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** A hollow square of wall around (cx, cz), {@code half} blocks each way, three high. */
    static void house(int cx, int cz, int half, int height) {
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                boolean edge = Math.abs(x - cx) == half || Math.abs(z - cz) == half;
                if (edge) {
                    for (int y = FEET; y < FEET + height; y++) {
                        WORLD.stubSetBlock(x, y, z, Material.STONE_BRICKS);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ plain marching

    private static void openGround() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        Horde h = muster(env, alex, OX + 40.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        KnightMind m = h.mind;
        expect(m != null && m.route.reached() && m.route.moves().size() >= 30, "open: he plans a route to the target (" + (m == null ? -1 : m.route.moves().size()) + " steps)");
        boolean plain = true;
        for (Move mv : m.route.moves()) {
            plain &= mv.kind() == MoveKind.WALK;
        }
        expect(plain, "open: over open ground every step is a walk");
        seconds(env, 11, 3.5);
        expect(flat(knight.getLocation(), alex.getLocation()) < 40.0 - 15.0, "open: the Knight marches toward his target, now " + (int) flat(knight.getLocation(), alex.getLocation()) + " blocks off");
        seconds(env, 8, 3.5);
        expect(flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0, "open: he arrives");
        expect(knight.getTarget() == alex, "open: and fights once he is close with a clear way");
        boolean attacking = false;
        for (UUID id : h.wights) {
            Entity w = Bukkit.getEntity(id);
            attacking |= w instanceof Mob wm && wm.getTarget() == alex;
        }
        expect(attacking, "open: the Rimebound near the target attack it");
        expect(!alex.heard("throw themselves") && !alex.heard("pile upon") && !alex.heard("Ice spreads"), "open: no tricks when none are needed");
    }

    // ------------------------------------------------------------------ doors

    private static void doorSiege() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        house(OX, OZ, 5, 4);
        WORLD.stubSetBlock(OX + 5, FEET, OZ, Material.OAK_DOOR);
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, Material.OAK_DOOR);
        // the house is a long wall on the Knight's side too, so going round costs more than the door
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                if (Math.abs(z - OZ) > 5) {
                    WORLD.stubSetBlock(OX + 5, y, z, Material.STONE_BRICKS);
                }
            }
        }
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        KnightMind m = h.mind;
        int breaches = 0;
        for (Move mv : m.route.moves()) {
            breaches += mv.kind() == MoveKind.BREACH ? 1 : 0;
        }
        expect(breaches == 1, "door: he plans exactly one siege (" + breaches + ")");
        expect(block(OX + 5, FEET, OZ) == Material.OAK_DOOR, "door: the door still stands while the army marches up to it");
        int began = -1;
        int broke = -1;
        int crew = 0;
        for (int t = 0; t < 120 && broke < 0; t++) {
            second(env, 3.5);
            if (m.breach != null) {
                if (began < 0) {
                    began = t;
                }
                crew = Math.max(crew, m.crew.size());
            }
            if (block(OX + 5, FEET, OZ) != Material.OAK_DOOR) {
                broke = t;
            }
        }
        expect(began >= 0, "door: the siege begins once the Knight is at the door");
        expect(alex.heard("throw themselves at a door"), "door: the people nearby are told");
        expect(crew >= 1 && crew <= env.cfg.breachCrew, "door: a crew of at most " + env.cfg.breachCrew + " goes at it (" + crew + ")");
        expect(block(OX + 5, FEET, OZ) == Material.AIR && block(OX + 5, FEET + 1, OZ) == Material.AIR, "door: it breaks, both halves");
        expect(began >= 0 && broke - began >= 3 && broke - began < 40, "door: a siege takes a few seconds, not none and not forever (" + (broke - began) + ")");
        expect(drops(Material.OAK_DOOR) >= 1, "door: the door drops as an item (broken for good)");
        expect(m.breach == null && m.crew.isEmpty(), "door: the siege is over and the crew released");
        seconds(env, 15, 3.5);
        expect(flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0, "door: and he walks in");
        expect(block(OX + 5, FEET, OZ) == Material.AIR, "door: it stays broken");
        expect(env.director.hordes().size() == 1, "door: the horde is still going");
    }

    private static int siegeSeconds(Env env, Material door, Kit.TestPlayer alex) {
        house(OX, OZ, 5, 4);
        WORLD.stubSetBlock(OX + 5, FEET, OZ, door);
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, door);
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                if (Math.abs(z - OZ) > 5) {
                    WORLD.stubSetBlock(OX + 5, y, z, Material.STONE_BRICKS);
                }
            }
        }
        muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        int n = 0;
        while (block(OX + 5, FEET, OZ) == door && n < 200) {
            second(env, 3.5);
            n++;
        }
        return n;
    }

    private static void ironDoor() {
        Env wood = arena();
        int w = siegeSeconds(wood, Material.OAK_DOOR, wood.player("Alex", OX, OZ));
        Env iron = arena();
        int i = siegeSeconds(iron, Material.IRON_DOOR, iron.player("Alex", OX, OZ));
        expect(w < 200 && i < 200, "iron door: both give way in the end");
        expect(i > w + 5, "iron door: iron holds out longer than wood (" + i + " vs " + w + " seconds)");
        Env trap = arena();
        trap.cfg.doorStrength = 20.0;
        int quick = siegeSeconds(trap, Material.OAK_DOOR, trap.player("Alex", OX, OZ));
        expect(quick < w, "iron door: a weaker door falls sooner (" + quick + " vs " + w + ")");
    }

    private static void claimedDoor() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        house(OX, OZ, 5, 4);
        WORLD.stubSetBlock(OX + 5, FEET, OZ, Material.OAK_DOOR);
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, Material.OAK_DOOR);
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                if (Math.abs(z - OZ) > 5) {
                    WORLD.stubSetBlock(OX + 5, y, z, Material.STONE_BRICKS);
                }
            }
        }
        env.realms.claimed = l -> l.getX() >= OX + 4.9 && l.getX() <= OX + 6.1 && Math.abs(l.getZ() - OZ) < 1.5;
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        seconds(env, 40, 3.5);
        expect(block(OX + 5, FEET, OZ) == Material.OAK_DOOR && block(OX + 5, FEET + 1, OZ) == Material.OAK_DOOR, "claimed door: a door on safe ground is never smashed");
        expect(h.mind.breach == null, "claimed door: no siege is even begun");
        boolean planned = false;
        for (Move mv : h.mind.route.moves()) {
            planned |= mv.kind() == MoveKind.BREACH;
        }
        expect(!planned, "claimed door: it is not in his plan");
        expect(drops(Material.OAK_DOOR) == 0, "claimed door: nothing dropped");
    }

    private static void hopelessDoor() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        env.cfg.doorStrength = 1_000_000.0;
        env.cfg.breachTimeoutSeconds = 10;
        house(OX, OZ, 5, 4);
        WORLD.stubSetBlock(OX + 5, FEET, OZ, Material.OAK_DOOR);
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, Material.OAK_DOOR);
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                if (Math.abs(z - OZ) > 5) {
                    WORLD.stubSetBlock(OX + 5, y, z, Material.STONE_BRICKS);
                }
            }
        }
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        seconds(env, 45, 3.5);
        expect(block(OX + 5, FEET, OZ) == Material.OAK_DOOR, "hopeless door: a door that will not give still stands");
        expect(h.mind.failedDoors.size() == 1, "hopeless door: the Knight gives up on it and remembers (" + h.mind.failedDoors.size() + ")");
        expect(h.mind.breach == null, "hopeless door: and is not stuck sieging it forever");
        boolean planned = false;
        for (Move mv : h.mind.route.moves()) {
            planned |= mv.kind() == MoveKind.BREACH;
        }
        expect(!planned, "hopeless door: his new plan does not include it");
    }

    // ------------------------------------------------------------------ liquid

    /** Every place an order sent a creature must have something solid under it. */
    private static boolean safeOrders(Env env, List<String> problems) {
        boolean ok = true;
        for (Entity e : Bukkit.ENTITIES.values()) {
            if (e instanceof Mob mob && env.director.isHordeCreature(mob) && mob.getPathfinder().lastLocation != null) {
                Location to = mob.getPathfinder().lastLocation;
                Material under = block((int) Math.floor(to.getX()), (int) Math.floor(to.getY()) - 1, (int) Math.floor(to.getZ()));
                if (under == Material.LAVA || under == Material.WATER || under == Material.AIR) {
                    ok = false;
                    problems.add(under + " at " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                }
            }
        }
        return ok;
    }

    private static void lake(Material liquid) {
        for (int x = OX + 5; x <= OX + 10; x++) {
            for (int z = OZ - 30; z <= OZ + 30; z++) {
                WORLD.stubSetBlock(x, GROUND, z, liquid);
            }
        }
    }

    private static void crossing(Material liquid, String word, String message) {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(liquid);
        Horde h = muster(env, alex, OX + 25.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        List<String> problems = new ArrayList<>();
        Kit.TestPlayer far = env.player("Far", OX - 300, OZ);
        boolean safe = true;
        boolean dry = true;
        int mostIce = 0;
        boolean crossed = false;
        for (int t = 0; t < 40; t++) {
            second(env, 3.5);
            Material under = block((int) Math.floor(knight.getLocation().getX()), GROUND, (int) Math.floor(knight.getLocation().getZ()));
            dry &= under != Material.WATER && under != Material.LAVA;
            safe &= safeOrders(env, problems);
            mostIce = Math.max(mostIce, env.director.ledger().size());
            crossed |= knight.getLocation().getX() < OX + 5;
        }
        expect(safe, word + ": nobody is ever sent to stand over the " + word + " " + problems);
        expect(mostIce >= 6, word + ": he freezes a path across (" + mostIce + " blocks of ice)");
        expect(mostIce <= 8, word + ": and only the width of the lake, not a causeway (" + mostIce + ")");
        expect(crossed, word + ": the Knight gets to the far side");
        expect(flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0, word + ": and reaches his target");
        expect(alex.heard(message), word + ": the people nearby are told");
        expect(dry, word + ": the Knight never stands over it, only on ice");
        int told = 0;
        for (String m : alex.messages) {
            told += m.contains("Ice spreads") ? 1 : 0;
        }
        expect(told == 1, word + ": and only once, not every time more ice goes down (" + told + ")");
        expect(!far.heard("Ice spreads"), word + ": while people far away are not");
        int ice = 0;
        for (int x = OX + 5; x <= OX + 10; x++) {
            ice += block(x, GROUND, OZ) == Material.PACKED_ICE ? 1 : 0;
        }
        expect(ice == 6, word + ": a straight run of ice crosses the lake (" + ice + ")");
        int kept = 0;
        for (int z = OZ - 30; z <= OZ + 30; z++) {
            for (int x = OX + 5; x <= OX + 10; x++) {
                kept += block(x, GROUND, z) == liquid ? 1 : 0;
            }
        }
        expect(kept == 6 * 61 - 6, word + ": the rest of the lake is untouched");
        env.director.stopAll();
        int back = 0;
        for (int x = OX + 5; x <= OX + 10; x++) {
            back += block(x, GROUND, OZ) == liquid ? 1 : 0;
        }
        expect(back == 6 && env.director.ledger().size() == 0, word + ": when the horde ends the " + word + " comes back");
    }

    private static void lava() {
        crossing(Material.LAVA, "lava", "Ice spreads over the lava");
        // bridges thaw by themselves too
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(Material.LAVA);
        env.cfg.bridgeLifetimeSeconds = 10;
        muster(env, alex, OX + 25.5, FEET, OZ + 0.5);
        int most = 0;
        for (int t = 0; t < 30; t++) {
            second(env, 3.5);
            most = Math.max(most, env.director.ledger().size());
        }
        expect(most >= 1, "lava: (the bridge was built)");
        env.now[0] += 11_000L;
        env.tick(1);
        int left = 0;
        for (int x = OX + 5; x <= OX + 10; x++) {
            left += block(x, GROUND, OZ) == Material.PACKED_ICE ? 1 : 0;
        }
        expect(left == 0, "lava: a bridge thaws once its time is up, even while the horde walks");
    }

    private static void water() {
        crossing(Material.WATER, "water", "Ice spreads over the water");
    }

    private static void swimming() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        env.cfg.bridgeEnabled = false;
        lake(Material.WATER);
        Horde h = muster(env, alex, OX + 25.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        int swims = 0;
        int bridges = 0;
        for (Move mv : h.mind.route.moves()) {
            swims += mv.kind() == MoveKind.SWIM ? 1 : 0;
            bridges += mv.kind() == MoveKind.BRIDGE ? 1 : 0;
        }
        expect(bridges == 0 && swims >= 4, "swim: with bridges off he swims across (" + swims + " swim steps)");
        seconds(env, 30, 3.5);
        expect(env.director.ledger().size() == 0, "swim: and freezes nothing");
        expect(flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0, "swim: but still gets there");
    }

    // ------------------------------------------------------------------ cliffs

    private static void plateau(int height) {
        for (int x = OX - 60; x <= OX + 10; x++) {
            for (int z = OZ - 60; z <= OZ + 60; z++) {
                for (int y = FEET; y < FEET + height; y++) {
                    WORLD.stubSetBlock(x, y, z, Material.STONE);
                }
            }
        }
    }

    private static Kit.TestPlayer onTop(Env env, int height) {
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        alex.loc = new Location(WORLD, OX + 0.5, FEET + height, OZ + 0.5);
        return alex;
    }

    private static void cliff() {
        Env env = arena();
        plateau(5);
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        int climbs = 0;
        int height = 0;
        for (Move mv : h.mind.route.moves()) {
            if (mv.kind() == MoveKind.CLIMB) {
                climbs++;
                height = mv.extra();
            }
        }
        expect(climbs == 1 && height == 5, "cliff: he plans one climb of five blocks (" + climbs + " of " + height + ")");
        List<Mob> everyone = new ArrayList<>();
        for (UUID id : h.wights) {
            everyone.add((Mob) Bukkit.getEntity(id));
        }
        List<Double> pile = null;
        List<Integer> growth = new ArrayList<>();
        boolean sawConveyor = false;
        boolean topped = false;
        for (int t = 0; t < 60 && !topped; t++) {
            second(env, 3.5);
            List<Double> frozen = new ArrayList<>();
            for (Mob w : everyone) {
                if (!w.hasAI() && !w.hasGravity()) {
                    frozen.add(w.getLocation().getY());
                }
            }
            if (h.mind.stack != null && h.mind.stack.phase() == StackJob.Phase.BUILD && !frozen.isEmpty()) {
                java.util.Collections.sort(frozen);
                pile = frozen;
                growth.add(frozen.size());
            }
            if (h.mind.stack != null && h.mind.stack.phase() == StackJob.Phase.ASCEND) {
                for (Mob w : everyone) {
                    if (!w.hasAI() && w.getLocation().getY() > FEET + 1.0 && w.getLocation().getY() < FEET + 4.0 && !h.mind.crew.contains(w.getUniqueId())) {
                        sawConveyor = true;
                    }
                }
            }
            topped = knight.getLocation().getY() >= FEET + 5 - 0.01;
        }
        expect(pile != null, "cliff: the crew freezes into a pile");
        expect(pile != null && pile.size() == StackPlan.crewFor(5) && near(pile.get(0), FEET) && near(pile.get(pile.size() - 1), FEET + 2.0 * (pile.size() - 1)),
                "cliff: " + StackPlan.crewFor(5) + " bodies, two blocks apart, reaching the top: " + pile);
        expect(growth.size() >= 3 && growth.get(0) < StackPlan.crewFor(5) && growth.get(growth.size() - 1) == StackPlan.crewFor(5), "cliff: the pile is built a body at a time (" + growth + ")");
        expect(sawConveyor, "cliff: and the army climbs up the side of it");
        expect(WORLD.soundLog.contains(org.bukkit.Sound.BLOCK_SNOW_PLACE) && WORLD.particleLog.contains(org.bukkit.Particle.CLOUD) && WORLD.particleLog.contains(org.bukkit.Particle.SNOWFLAKE), "cliff: with sounds and snow");
        expect(topped, "cliff: the Knight is passed up to the top");
        expect(alex.heard("pile upon each other"), "cliff: the people nearby are told");
        seconds(env, 6, 3.5);
        int up = 0;
        boolean awake = true;
        for (Mob w : everyone) {
            up += w.getLocation().getY() >= FEET + 4.99 ? 1 : 0;
            awake &= w.hasAI() && w.hasGravity();
        }
        expect(up >= everyone.size() - 1, "cliff: the army follows him up (" + up + " of " + everyone.size() + ")");
        expect(awake, "cliff: everyone has their legs back");
        expect(h.mind.stack == null && h.mind.crew.isEmpty(), "cliff: the job is done");
        seconds(env, 10, 3.5);
        expect(flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0 && knight.getTarget() == alex, "cliff: and he goes for his target");

        Env off = arena();
        plateau(5);
        off.cfg.stackEnabled = false;
        Kit.TestPlayer bea = onTop(off, 5);
        Horde ho = muster(off, bea, OX + 30.5, FEET, OZ + 0.5);
        Mob ko = (Mob) off.walkerOf(ho);
        seconds(off, 40, 3.5);
        expect(ko.getLocation().getY() < FEET + 1 && ho.mind.stack == null, "cliff: with stacking off the wall holds him");

        Env tall = arena();
        tall.cfg.knightPlanSeconds = 30;
        tall.cfg.knightMaxStuckSeconds = 120;
        plateau(12);
        Kit.TestPlayer cy = onTop(tall, 12);
        Horde ht = muster(tall, cy, OX + 30.5, FEET, OZ + 0.5);
        Mob kt = (Mob) tall.walkerOf(ht);
        seconds(tall, 40, 3.5);
        expect(kt.getLocation().getY() < FEET + 1, "cliff: a wall above the maximum height holds him too");
        expect(!ht.mind.route.reached() && ht.mind.nextPlanTick - ht.ticksLived <= 2 * 20, "cliff: and having got as near as the plan allows he looks again within seconds, not at the next full interval");
    }

    private static void smallArmy() {
        Env env = arena();
        plateau(5);
        env.cfg.wightsBase = 2;
        env.cfg.reinforceCount = 0;
        env.cfg.knightReinforceCount = 0;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        boolean climbs = false;
        for (Move mv : h.mind.route.moves()) {
            climbs |= mv.kind() == MoveKind.CLIMB;
        }
        expect(!climbs, "small army: he does not even plan a climb he has too few bodies for");
        seconds(env, 40, 3.5);
        expect(h.wights.size() < StackPlan.crewFor(5) && knight.getLocation().getY() < FEET + 1, "small army: too few bodies to climb");

        Env late = arena();
        plateau(5);
        Kit.TestPlayer bea = onTop(late, 5);
        late.cfg.stackGatherSeconds = 5;
        Horde hl = muster(late, bea, OX + 30.5, FEET, OZ + 0.5);
        Mob kl = (Mob) late.walkerOf(hl);
        for (UUID id : hl.wights) {
            Mob w = (Mob) Bukkit.getEntity(id);
            w.teleport(new Location(WORLD, OX + 90, FEET, OZ + 90));
            w.setAI(false); // they cannot come
        }
        seconds(late, 25, 3.5);
        expect(hl.mind.noClimbUntil > 0, "small army: a pile that never gathers is given up on");
        expect(hl.mind.stack == null && hl.mind.crew.isEmpty(), "small army: and the job is cleared");
        expect(kl.getLocation().getY() < FEET + 1, "small army: the Knight is still at the foot of the wall");
    }

    // ------------------------------------------------------------------ trouble

    private static void stuck() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        env.cfg.knightMaxStuckSeconds = 3;
        Horde h = muster(env, alex, OX + 40.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        env.tick(14); // nothing moves: the Knight is held in place
        expect(h.mind.fallbackUntil > 0, "stuck: a Knight who goes nowhere eventually falls back to the plain march");
        env.tick(1);
        Location to = knight.getPathfinder().lastLocation;
        expect(to != null && Math.abs(to.getX() - (OX + 40.5 - 24.0)) < 1.0, "stuck: which sends him a long stride toward the target");
        long until = h.mind.fallbackUntil;
        env.tick(20);
        expect(h.ticksLived > until, "stuck: the fallback lasts only a while");
        expect(h.mind.stuckReplans < 3, "stuck: and the count starts again");

        Env mover = arena();
        Kit.TestPlayer bea = mover.player("Bea", OX, OZ);
        mover.cfg.knightMaxStuckSeconds = 3;
        Horde hm = muster(mover, bea, OX + 40.5, FEET, OZ + 0.5);
        seconds(mover, 20, 3.5);
        expect(hm.mind.fallbackUntil == 0, "stuck: a Knight who is moving is never called stuck");
    }

    private static void ending() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(Material.LAVA);
        Horde h = muster(env, alex, OX + 25.5, FEET, OZ + 0.5);
        for (int t = 0; t < 12; t++) {
            second(env, 3.5);
        }
        expect(env.director.ledger().size() > 0, "ending: (ice is up)");
        env.director.shutdown();
        expect(env.director.hordes().isEmpty() && env.director.ledger().size() == 0, "ending: shutting down thaws every bridge");
        int lava = 0;
        for (int x = OX + 5; x <= OX + 10; x++) {
            lava += block(x, GROUND, OZ) == Material.LAVA ? 1 : 0;
        }
        expect(lava == 6, "ending: the lava is lava again");
        expect(h.mind != null, "ending: (the horde had a mind)");
    }

    /** Helper: the standard house-with-a-door-in-a-long-wall scene. */
    private static void wall() {
        house(OX, OZ, 5, 4);
        WORLD.stubSetBlock(OX + 5, FEET, OZ, Material.OAK_DOOR);
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, Material.OAK_DOOR);
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                if (Math.abs(z - OZ) > 5) {
                    WORLD.stubSetBlock(OX + 5, y, z, Material.STONE_BRICKS);
                }
            }
        }
    }

    private static int breakTime(Env env) {
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        wall();
        muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        int n = 0;
        while (block(OX + 5, FEET, OZ) == Material.OAK_DOOR && n < 150) {
            second(env, 3.5);
            n++;
        }
        return n;
    }

    private static void sieges() {
        Env crewOnly = arena();
        crewOnly.cfg.breachKnightPower = 0.0;
        crewOnly.cfg.doorStrength = 30.0;
        int c = breakTime(crewOnly);
        expect(c < 100, "siege: the crew can break a door without the Knight's help (" + c + " s)");

        Env knightOnly = arena();
        knightOnly.cfg.breachPerWight = 0.1;
        knightOnly.cfg.breachKnightPower = 100.0;
        knightOnly.cfg.doorStrength = 200.0;
        int k = breakTime(knightOnly);
        Env nobody = arena();
        nobody.cfg.breachPerWight = 0.1;
        nobody.cfg.breachKnightPower = 0.0;
        nobody.cfg.doorStrength = 200.0;
        int n = breakTime(nobody);
        expect(k < 40 && n > k + 20, "siege: the Knight's own strength counts when he stands at the door (" + k + " s against " + n + " s)");

        Env small = arena();
        small.cfg.breachCrew = 2;
        small.cfg.breachKnightPower = 0.0;
        small.cfg.doorStrength = 120.0;
        int few = breakTime(small);
        Env big = arena();
        big.cfg.breachCrew = 12;
        big.cfg.breachMaxContributors = 12;
        big.cfg.breachKnightPower = 0.0;
        big.cfg.doorStrength = 120.0;
        int many = breakTime(big);
        expect(many < few, "siege: a bigger crew breaks it faster (" + many + " s against " + few + " s)");

        Env quiet = arena();
        Kit.TestPlayer bea = quiet.player("Bea", OX, OZ);
        wall();
        Horde h = muster(quiet, bea, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) quiet.walkerOf(h);
        boolean sawSiege = false;
        boolean calm = true;
        for (int t = 0; t < 60 && block(OX + 5, FEET, OZ) == Material.OAK_DOOR; t++) {
            second(quiet, 3.5);
            if (h.mind != null && h.mind.breach != null) {
                sawSiege = true;
                calm &= knight.getTarget() == null;
                for (UUID id : h.mind.crew) {
                    calm &= ((Mob) Bukkit.getEntity(id)).getTarget() == null;
                }
            }
        }
        expect(sawSiege && calm, "siege: during a siege neither the Knight nor the crew wanders off after the player");

        Env late = arena();
        Kit.TestPlayer cy = late.player("Cy", OX, OZ);
        wall();
        Horde hl = muster(late, cy, OX + 30.5, FEET, OZ + 0.5);
        for (int t = 0; t < 60 && (hl.mind == null || hl.mind.breach == null); t++) {
            second(late, 3.5);
        }
        expect(hl.mind != null && hl.mind.breach != null, "siege: (a siege is under way)");
        late.realms.claimed = l -> Math.abs(l.getX() - (OX + 5.5)) < 0.9 && Math.abs(l.getZ() - (OZ + 0.5)) < 0.9;
        seconds(late, 60, 3.5);
        expect(block(OX + 5, FEET, OZ) == Material.OAK_DOOR && block(OX + 5, FEET + 1, OZ) == Material.OAK_DOOR,
                "siege: if the land is claimed while the siege is on, the door is not broken after all");
        expect(hl.mind.breach == null && drops(Material.OAK_DOOR) == 0 && hl.mind.failedDoors.size() == 1, "siege: and nothing dropped, and the Knight marks that door as a wall");
    }

    private static void patience() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(Material.LAVA);
        Horde h = muster(env, alex, OX + 40.5, FEET, OZ + 0.5);
        second(env, 3.5);
        expect(env.director.ledger().size() == 0, "patience: no ice goes down while the lake is still far off");
        expect(h.mind.route.bridgeCount() == 6, "patience: though he has already planned it");

        Env wide = arena();
        Kit.TestPlayer bea = wide.player("Bea", OX, OZ);
        lake(Material.LAVA);
        wide.cfg.bridgeMaxBlocks = 3;
        Horde hw = muster(wide, bea, OX + 25.5, FEET, OZ + 0.5);
        Mob kw = (Mob) wide.walkerOf(hw);
        int most = 0;
        for (int t = 0; t < 70; t++) {
            second(wide, 3.5);
            most = Math.max(most, wide.director.ledger().size());
        }
        expect(most == 0, "patience: a lake wider than the most ice he will build is walked round (" + most + " ice)");
        expect(hw.mind.route.bridgeCount() == 0, "patience: his plan has no bridge in it");
        expect(kw.getLocation().getZ() > OZ + 20 || kw.getLocation().getZ() < OZ - 20 || flat(kw.getLocation(), bea.getLocation()) < 20, "patience: he is going the long way");

        Env walking = arena();
        Kit.TestPlayer cy = walking.player("Cy", OX, OZ);
        Horde hh = muster(walking, cy, OX + 30.5, FEET, OZ + 0.5);
        second(walking, 3.5);
        int gx = hh.mind.goalX;
        cy.loc = new Location(WORLD, OX + 0.5, FEET, OZ + 25.5);
        second(walking, 3.5);
        expect(hh.mind.goalZ == OZ + 25 && hh.mind.goalX == gx, "patience: when the target moves a long way he plans again at once");
        long next = hh.mind.nextPlanTick;
        cy.loc = new Location(WORLD, OX + 0.5, FEET, OZ + 27.5);
        second(walking, 3.5);
        expect(hh.mind.goalZ == OZ + 25 && hh.mind.nextPlanTick == next, "patience: a small step is not worth a new plan");
        seconds(walking, 6, 3.5);
        expect(hh.mind.goalZ == OZ + 27, "patience: but he does look again after a few seconds");
    }

    private static void thaw() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(Material.LAVA);
        env.cfg.bridgeLifetimeSeconds = 10;
        muster(env, alex, OX + 25.5, FEET, OZ + 0.5);
        for (int t = 0; t < 12; t++) {
            second(env, 3.5);
        }
        expect(block(OX + 7, GROUND, OZ) == Material.PACKED_ICE, "thaw: (there is ice)");
        env.now[0] += 5_000L;
        env.tick(1);
        expect(block(OX + 7, GROUND, OZ) == Material.PACKED_ICE, "thaw: ice lasts as long as it is told to");
        BridgeLedger reread = new BridgeLedger(env.ledgerFile, w -> { }, () -> env.now[0]);
        reread.load();
        expect(env.ledgerFile.isFile() && reread.size() == env.ledger.size() && reread.size() > 0 && reread.entries().equals(env.ledger.entries()),
                "thaw: the director saves its ice as it goes (" + reread.size() + " of " + env.ledger.size() + ")");
        alex.loc = new Location(WORLD, OX + 7.5, FEET, OZ + 0.5);
        env.now[0] += 11_000L;
        env.tick(1);
        expect(block(OX + 7, GROUND, OZ) == Material.PACKED_ICE, "thaw: ice is not melted from under a player");
        expect(block(OX + 8, GROUND, OZ) == Material.LAVA || block(OX + 8, GROUND, OZ) == Material.PACKED_ICE, "thaw: (the rest follows its own timer)");
        alex.loc = new Location(WORLD, OX, FEET, OZ + 20.5);
        env.tick(1);
        expect(block(OX + 7, GROUND, OZ) == Material.LAVA, "thaw: once they step off, it thaws");
        BridgeLedger after = new BridgeLedger(env.ledgerFile, w -> { }, () -> env.now[0]);
        after.load();
        expect(after.size() == env.ledger.size(), "thaw: and the saved file follows (" + after.size() + " of " + env.ledger.size() + ")");

        // only someone really standing on it keeps it: not another world, not far above, not far below
        for (int variant = 0; variant < 3; variant++) {
            Env e = arena();
            Kit.TestPlayer owner = e.player("Alex", OX, OZ);
            lake(Material.LAVA);
            e.cfg.bridgeLifetimeSeconds = 10;
            muster(e, owner, OX + 25.5, FEET, OZ + 0.5);
            for (int t = 0; t < 12; t++) {
                second(e, 3.5);
            }
            Kit.TestPlayer other = e.player("Dee", OX, OZ + 40);
            org.bukkit.World elsewhere = new org.bukkit.World("elsewhere");
            Bukkit.WORLDS.put("elsewhere", elsewhere);
            other.loc = variant == 0 ? new Location(elsewhere, OX + 7.5, FEET, OZ + 0.5)
                    : variant == 1 ? new Location(WORLD, OX + 7.5, FEET + 12, OZ + 0.5)
                    : new Location(WORLD, OX + 7.5, GROUND - 6, OZ + 0.5);
            e.now[0] += 11_000L;
            e.tick(1);
            expect(block(OX + 7, GROUND, OZ) == Material.LAVA, "thaw: " + (variant == 0 ? "someone in another world" : variant == 1 ? "someone far overhead" : "someone far below") + " does not keep it");
            Bukkit.WORLDS.remove("elsewhere");
        }
    }

    private static void ladder() {
        Env env = arena();
        plateau(5);
        env.cfg.stackGatherSeconds = 8;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = KnightTest.start(env, alex, Boolean.TRUE).horde();
        Mob knight = (Mob) env.walkerOf(h);
        knight.teleport(new Location(WORLD, OX + 30.5, FEET, OZ + 0.5));
        int i = 0;
        List<Mob> farGroup = new ArrayList<>();
        for (UUID id : h.wights) {
            Mob w = (Mob) Bukkit.getEntity(id);
            if (i < 8) {
                w.teleport(new Location(WORLD, OX + 30.5 + 40, FEET, OZ + 0.5)); // far behind
                farGroup.add(w);
            } else {
                w.teleport(new Location(WORLD, OX + 30.5, FEET, OZ + 0.5));
            }
            i++;
        }
        boolean topped = false;
        for (int t = 0; t < 45 && !topped; t++) {
            second(env, 3.5);
            topped = knight.getLocation().getY() >= FEET + 4.99;
        }
        expect(topped, "ladder: the pile is made of the bodies nearest the wall, not the ones lost far behind");
    }

    private static void followers() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        List<Mob> far = new ArrayList<>();
        Mob near = null;
        int i = 0;
        for (UUID id : h.wights) {
            Mob w = (Mob) Bukkit.getEntity(id);
            if (i == 0) {
                w.teleport(new Location(WORLD, OX + 10.5, FEET, OZ + 0.5)); // close to the target
                near = w;
            } else if (i < 4) {
                w.teleport(new Location(WORLD, OX + 70.5, FEET, OZ + 0.5)); // left behind
                w.setTarget(alex); // chasing from far away
                far.add(w);
            }
            i++;
        }
        second(env, 0.0);
        expect(near.getTarget() == alex, "followers: a Rimebound near the target attacks it");
        boolean close = true;
        boolean dropped = true;
        for (Mob w : far) {
            Location to = w.getPathfinder().lastLocation;
            close &= to != null && flat(to, knight.getLocation()) <= 3.5;
            dropped &= w.getTarget() == null;
        }
        expect(close, "followers: the ones left behind are called to the Knight");
        expect(dropped, "followers: and stop chasing a target they cannot reach");
        Mob beside = null;
        for (UUID id : h.wights) {
            Mob w = (Mob) Bukkit.getEntity(id);
            if (flat(w.getLocation(), knight.getLocation()) < 5.0) {
                beside = w;
            }
        }
        expect(beside != null && beside.getPathfinder().lastLocation == null, "followers: those already beside him are left where they are");
    }

    // ------------------------------------------------------------------ more about sieges

    /** A long wall with one door in it (two blocks tall), and nothing else. */
    private static void fence() {
        for (int z = OZ - 40; z <= OZ + 40; z++) {
            for (int y = FEET; y < FEET + 4; y++) {
                WORLD.stubSetBlock(OX + 5, y, z, z == OZ && y < FEET + 2 ? Material.OAK_DOOR : Material.STONE_BRICKS);
            }
        }
    }

    private static boolean doorStands() {
        return block(OX + 5, FEET, OZ) == Material.OAK_DOOR && block(OX + 5, FEET + 1, OZ) == Material.OAK_DOOR;
    }

    private static Mob mob(UUID id) {
        return (Mob) Bukkit.getEntity(id);
    }

    /** Nobody can reach the door: the crew is far beneath it and the Knight is far away. */
    private static void patientSiege() {
        Env env = arena();
        env.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer alex = env.player("Alex", OX - 12, OZ);
        fence();
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        for (int t = 0; t < 60 && (h.mind == null || h.mind.breach == null); t++) {
            second(env, 3.5);
        }
        expect(h.mind != null && h.mind.breach != null, "patient siege: (a siege begins)");
        Route plan = h.mind.route;
        for (UUID id : h.wights) {
            Mob w = mob(id);
            w.setAI(false);
            w.teleport(new Location(WORLD, OX + 4.5, FEET - 12, OZ + 0.5));
        }
        knight.teleport(new Location(WORLD, OX + 30.5, FEET, OZ + 0.5));
        knight.setAI(false);
        seconds(env, 40, 3.5);
        expect(doorStands() && h.mind.breach != null, "patient siege: bodies on another level and a Knight far away do no damage");
        expect(h.mind.route == plan, "patient siege: he does not re-plan in the middle of a siege");
        expect(h.mind.fallbackUntil == 0, "patient siege: standing through a siege is not being stuck");
        for (int t = 0; t < 80 && h.mind.breach != null; t++) {
            second(env, 3.5);
        }
        expect(h.mind.breach == null && h.mind.failedDoors.size() == 1 && doorStands(), "patient siege: in the end he gives up on it");
        Route before = h.mind.route;
        second(env, 3.5);
        expect(h.mind.route != before, "patient siege: and plans again at once");
    }

    private static void shortSieges() {
        Env env = arena();
        env.cfg.knightPlanSeconds = 60;
        env.cfg.doorStrength = 3.0;
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        fence();
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        for (int t = 0; t < 80 && doorStands(); t++) {
            second(env, 3.5);
        }
        expect(!doorStands(), "short siege: (the door goes)");
        Route before = h.mind.route;
        second(env, 3.5);
        expect(h.mind.route != before, "short siege: he plans again the moment it is down, not at the next interval");

        Env hopeless = arena();
        hopeless.cfg.knightPlanSeconds = 60;
        hopeless.cfg.doorStrength = 1_000_000.0;
        hopeless.cfg.breachTimeoutSeconds = 10;
        Kit.TestPlayer bea = hopeless.player("Bea", OX, OZ);
        fence();
        Horde hh = muster(hopeless, bea, OX + 30.5, FEET, OZ + 0.5);
        for (int t = 0; t < 80 && (hh.mind == null || hh.mind.breach == null); t++) {
            second(hopeless, 3.5);
        }
        for (int t = 0; t < 30 && hh.mind.breach != null; t++) {
            second(hopeless, 3.5);
        }
        expect(hh.mind.breach == null && hh.mind.failedDoors.size() == 1, "short siege: (he gives up)");
        Route stale = hh.mind.route;
        second(hopeless, 3.5);
        expect(hh.mind.route != stale, "short siege: and plans again the moment he does, not at the next interval");

        Env vanished = arena();
        Kit.TestPlayer cy = vanished.player("Cy", OX, OZ);
        fence();
        Horde hv = muster(vanished, cy, OX + 30.5, FEET, OZ + 0.5);
        for (int t = 0; t < 80 && (hv.mind == null || hv.mind.breach == null); t++) {
            second(vanished, 3.5);
        }
        WORLD.stubSetBlock(OX + 5, FEET, OZ, Material.AIR); // someone opened it up, or took it
        WORLD.stubSetBlock(OX + 5, FEET + 1, OZ, Material.AIR);
        for (int t = 0; t < 30 && hv.mind.breach != null; t++) {
            second(vanished, 3.5);
        }
        expect(hv.mind.breach == null && hv.mind.failedDoors.isEmpty(), "short siege: a door somebody else already removed is not a failure");
        expect(drops(Material.OAK_DOOR) == 0, "short siege: and nothing is dropped for it");
    }

    private static void crewChoice() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        fence();
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        for (int t = 0; t < 60 && (h.mind == null || h.mind.breach == null); t++) {
            second(env, 3.5);
        }
        expect(h.mind != null && h.mind.breach != null, "crew: (a siege begins)");
        List<UUID> all = new ArrayList<>(h.wights);
        List<UUID> close = new ArrayList<>(all.subList(all.size() - 4, all.size()));
        for (UUID id : all) {
            mob(id).teleport(close.contains(id) ? new Location(WORLD, OX + 4.0, FEET, OZ + 0.5) : new Location(WORLD, OX + 30.5, FEET, OZ + 0.5));
        }
        env.tick(1);
        expect(h.mind.crew.size() == env.cfg.breachCrew && h.mind.crew.containsAll(close), "crew: the bodies nearest the door are the crew (" + h.mind.crew.size() + ")");
    }

    // ------------------------------------------------------------------ more about piles

    private static void gatherFails() {
        for (int variant = 0; variant < 2; variant++) {
            Env env = arena();
            plateau(5);
            env.cfg.stackGatherSeconds = 8;
            env.cfg.knightReinforceCount = 0; // nobody new turns up beside him
            Kit.TestPlayer alex = onTop(env, 5);
            Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
            Mob knight = (Mob) env.walkerOf(h);
            for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
                second(env, 3.5);
            }
            String what = variant == 0 ? "bodies a few blocks off" : "bodies on another level";
            expect(h.mind != null && h.mind.stack != null, "gather (" + what + "): (a pile begins)");
            Location at = knight.getLocation();
            for (UUID id : h.wights) {
                Mob w = mob(id);
                w.setAI(false);
                w.teleport(variant == 0 ? new Location(WORLD, at.getX() + 8, at.getY(), at.getZ()) : new Location(WORLD, at.getX(), at.getY() - 8, at.getZ()));
            }
            seconds(env, 12, 3.5);
            expect(h.mind.stack == null && h.mind.noClimbUntil > 0 && knight.getLocation().getY() < FEET + 1, "gather (" + what + "): are not at the foot of the wall, so no pile forms (stack=" + h.mind.stack + " noClimb=" + h.mind.noClimbUntil + " y=" + knight.getLocation().getY() + ")");
        }

        // the army shrinks on the way: by the time he gets there he has too few and does not even start
        Env env = arena();
        plateau(5);
        env.cfg.stackGatherSeconds = 60;
        env.cfg.knightPlanSeconds = 60;
        env.cfg.knightReinforceCount = 0;
        env.cfg.reinforceCount = 0;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        second(env, 3.5);
        int i = 0;
        for (UUID id : new ArrayList<>(h.wights)) {
            if (i++ >= 2) {
                Bukkit.getEntity(id).remove();
            }
        }
        seconds(env, 30, 3.5);
        expect(h.mind.stack == null && h.mind.noClimbUntil > h.ticksLived, "gather: too few left at the wall, so he does not start a pile and will not for a while (" + h.mind.noClimbUntil + " now " + h.ticksLived + " stack=" + h.mind.stack + " route=" + h.mind.route.moves().size() + ")");
        expect(knight.getLocation().getY() < FEET + 1, "gather: and stays at the foot (y=" + knight.getLocation().getY() + ")");
    }

    private static boolean isTop(Entity e) {
        return isTopAt(5, e);
    }

    private static boolean isTopAt(int height, Entity e) {
        return e.getLocation().getY() >= FEET + height - 0.01;
    }

    /** Once the pile starts nobody walks: the army stands where it was gathered, so every lift is the director's. */
    private static void strictLadder(int army, String label, int perSecond, int height) {
        Env env = arena();
        plateau(height);
        env.cfg.stackGatherSeconds = 8;
        env.cfg.stackPerSecond = perSecond;
        env.cfg.wightsBase = army;
        if (army < 12) {
            env.cfg.reinforceCount = 0;
            env.cfg.knightReinforceCount = 0;
        }
        Kit.TestPlayer alex = onTop(env, height);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
            second(env, 3.5);
        }
        expect(h.mind.stack != null, "strict ladder (" + label + "): (a pile begins)");
        Location foot = knight.getLocation();
        int i = 0;
        List<Mob> all = new ArrayList<>();
        for (UUID id : h.wights) {
            Mob w = mob(id);
            w.teleport(new Location(WORLD, foot.getX() + (i % 3 - 1) * 0.5, foot.getY(), foot.getZ() + ((i / 3) % 3 - 1) * 0.5));
            w.setFallDistance(7f);
            all.add(w);
            i++;
        }
        int needed = h.mind.stack.needed;
        List<Integer> snowCalls = new ArrayList<>();
        int below = StackPlan.stepsBelow(height);
        boolean sawAscend = false;
        List<Integer> held = new ArrayList<>();
        boolean yawOk = true;
        boolean pitchOk = true;
        boolean oneColumn = true;
        boolean leans = false;
        boolean glow = true;
        boolean glowLong = true;
        boolean newcomersWalk = false;
        boolean knightMid = false;
        boolean knightOver = false;
        boolean knightGlows = false;
        int ascents = 0;
        int maxMid = 0;
        int firstMid = -1;
        boolean sawMidOther = false;
        boolean sidesApart = true;
        int pairs = 0;
        boolean chainStill = true;
        boolean crewGoChecked = false;
        boolean crewGoClean = true;
        boolean crewNeverDown = true;
        java.util.Map<UUID, Double> crewY = new java.util.HashMap<>();
        java.util.Set<UUID> wasUp = new java.util.HashSet<>();
        int lastSteps = -1;
        int lastUp = -1;
        int snowPlaced = 0;
        boolean cloudJoin = false;
        boolean stepSound = false;
        boolean cloudLand = false;
        boolean done = false;
        boolean everyoneUp = false;
        boolean knightUp = false;
        for (Mob w : all) {
            w.getPathfinder().stopPathfinding();
        }
        WORLD.soundLog.clear();
        WORLD.particleLog.clear();
        for (int t = 0; t < 80 && !done; t++) {
            env.tick(1);
            StackJob.Phase phase = h.mind.stack == null ? null : h.mind.stack.phase();
            if (phase == StackJob.Phase.BUILD) {
                int n = 0;
                double colX = Double.NaN;
                java.util.Set<Double> zs = new java.util.HashSet<>();
                int walking = 0;
                for (Mob w : all) {
                    if (!w.hasAI()) {
                        n++;
                        glow &= w.hasPotionEffect(PotionEffectType.GLOWING);
                        crewY.put(w.getUniqueId(), w.getLocation().getY());
                        glowLong &= w.hasPotionEffect(PotionEffectType.GLOWING) && w.getPotionEffect(PotionEffectType.GLOWING).getDuration() > 20;
                        Location at = w.getLocation();
                        pitchOk &= Math.abs(at.getPitch() + 25f) < 0.01f;
                        yawOk &= Math.abs(at.getYaw() - 90f) < 0.01f;
                        if (Double.isNaN(colX)) {
                            colX = at.getX();
                        }
                        oneColumn &= Math.abs(at.getX() - colX) < 0.001;
                        zs.add(Math.round(at.getZ() * 1000) / 1000.0);
                    } else if (h.mind.crew.contains(w.getUniqueId()) && w.getPathfinder().hasPath()) {
                        walking++;
                    }
                }
                leans |= zs.size() >= 2;
                newcomersWalk |= n < needed && walking == needed - n;
                held.add(n);
                snowPlaced += (int) WORLD.soundLog.stream().filter(x -> x == org.bukkit.Sound.BLOCK_SNOW_PLACE).count();
                cloudJoin |= WORLD.particleLog.contains(org.bukkit.Particle.CLOUD);
                WORLD.soundLog.clear();
                WORLD.particleLog.clear();
            }
            if (phase == StackJob.Phase.ASCEND) {
                ascents++;
                stepSound |= WORLD.soundLog.contains(org.bukkit.Sound.ENTITY_ZOMBIE_STEP);
                snowCalls.add((int) WORLD.particleLog.stream().filter(x -> x == org.bukkit.Particle.SNOWFLAKE).count());
                cloudLand |= WORLD.particleLog.contains(org.bukkit.Particle.CLOUD);
                WORLD.soundLog.clear();
                WORLD.particleLog.clear();
                if (!sawAscend) {
                    sawAscend = true;
                    knightMid = below == 0 || !knight.hasAI() && !isTopAt(height, knight) && knight.getLocation().getY() > FEET + 1.0;
                    knightGlows = knight.hasPotionEffect(PotionEffectType.GLOWING);
                }
                if (ascents == below + 1) {
                    knightOver = isTopAt(height, knight) && knight.hasAI() && knight.hasGravity();
                }
                int mid = knight.hasAI() ? 0 : 1;
                int starts = 0;
                if (below > 0) {
                    for (java.util.Map.Entry<UUID, Integer> e : h.mind.steps.entrySet()) {
                        starts += e.getValue() == 1 && !h.mind.crew.contains(e.getKey()) ? 1 : 0;
                    }
                } else {
                    for (Mob w : all) {
                        starts += isTopAt(height, w) && !wasUp.contains(w.getUniqueId()) && !h.mind.crew.contains(w.getUniqueId()) ? 1 : 0;
                    }
                    starts += isTopAt(height, knight) && !wasUp.contains(knight.getUniqueId()) ? 1 : 0;
                }
                chainStill &= knight.hasAI() || !knight.getPathfinder().hasPath();
                int up = 0;
                for (Mob w : all) {
                    up += isTopAt(height, w) ? 1 : 0;
                    boolean crew = h.mind.crew.contains(w.getUniqueId());
                    boolean inAir = !w.hasAI() && w.getLocation().getY() > FEET + 1.0 && !isTopAt(height, w);
                    if (inAir && !crew) {
                        mid++;
                        sawMidOther = true;
                        chainStill &= !w.getPathfinder().hasPath();
                        for (Mob c : all) {
                            if (h.mind.crew.contains(c.getUniqueId()) && !c.hasAI() && Math.abs(c.getLocation().getY() - w.getLocation().getY()) < 0.01) {
                                pairs++;
                                sidesApart &= Math.abs(c.getLocation().getZ() - w.getLocation().getZ()) > 0.1;
                            }
                        }
                    }
                    if (crew) {
                        Double before = crewY.put(w.getUniqueId(), w.getLocation().getY());
                        crewNeverDown &= before == null || w.getLocation().getY() >= before - 0.001 || isTopAt(height, w);
                    }
                }
                maxMid = Math.max(maxMid, starts);
                if (firstMid < 0) {
                    firstMid = starts;
                }
                wasUp.clear();
                for (Mob w : all) {
                    if (isTopAt(height, w)) {
                        wasUp.add(w.getUniqueId());
                    }
                }
                if (isTopAt(height, knight)) {
                    wasUp.add(knight.getUniqueId());
                }
                if (h.mind.crewGo && !crewGoChecked) {
                    crewGoChecked = true;
                    for (UUID id : h.mind.steps.keySet()) {
                        crewGoClean &= h.mind.crew.contains(id);
                    }
                    for (Mob w : all) {
                        crewGoClean &= !(w.hasAI() && w.getLocation().getY() < FEET + 1.0);
                    }
                }
                lastSteps = h.mind.steps.size();
                lastUp = up;
            }
            if (h.mind.stack == null) {
                done = true;
                int up = 0;
                for (Mob w : all) {
                    up += isTopAt(height, w) ? 1 : 0;
                }
                everyoneUp = up == all.size();
                knightUp = isTopAt(height, knight);
            }
        }
        expect(done, "strict ladder (" + label + "): (the job ends)");
        boolean snowfall = !snowCalls.isEmpty() && java.util.Collections.max(snowCalls) > java.util.Collections.min(snowCalls);
        boolean grows = held.size() >= 3 && held.get(0) < needed && held.get(held.size() - 1) == needed;
        int distinct = (int) held.stream().distinct().count();
        for (int k = 1; k < held.size(); k++) {
            grows &= held.get(k) >= held.get(k - 1);
        }
        expect(grows && distinct >= 3, "strict ladder (" + label + "): the pile grows a body at a time, not all at once " + held);
        expect(newcomersWalk, "strict ladder (" + label + "): and the bodies not in it yet are still walking in");
        expect(yawOk && pitchOk, "strict ladder (" + label + "): the bodies in the pile look up at the wall");
        expect(oneColumn && leans, "strict ladder (" + label + "): in one column along the wall, leaning a little to each side");
        expect(glow && (below == 0 || knightGlows), "strict ladder (" + label + "): the pile and the climbers glow, so they show through the wall");
        expect(glowLong, "strict ladder (" + label + "): and the glow lasts longer than the second before it is renewed");
        expect(snowPlaced == needed && cloudJoin, "strict ladder (" + label + "): every body that joins the pile gets one thud and a puff of snow (" + snowPlaced + ")");
        expect(stepSound && snowfall && cloudLand, "strict ladder (" + label + "): the climb has its own sounds and snow, and a puff where they land");
        expect(sawAscend && knightMid, "strict ladder (" + label + "): the Knight climbs first, up the side of the pile, not straight to the top");
        expect(knightOver, "strict ladder (" + label + "): and is over the wall, on his own legs, the next second");
        expect(chainStill, "strict ladder (" + label + "): nobody on the pile is told to walk off it");
        if (army > needed && below > 0) {
            expect(sawMidOther, "strict ladder (" + label + "): the army climbs hand over hand through the pile");
            expect(pairs > 0 && sidesApart, "strict ladder (" + label + "): on the other side of the pile from the bodies holding it");
        }
        if (army > needed) {
            expect(firstMid == Math.min(perSecond, 1 + army - needed), "strict ladder (" + label + "): the first second sends the Knight and the climbers-per-second budget, all from the army (" + firstMid + ")");
            expect(crewGoChecked && crewGoClean, "strict ladder (" + label + "): the pile itself climbs only when every one else is over");
            expect(crewNeverDown, "strict ladder (" + label + "): and each of them climbs from where they stand");
        }
        expect(lastSteps == 0 && lastUp == all.size(), "strict ladder (" + label + "): the job does not end until every climber is over (" + lastSteps + " left, " + lastUp + " up)");
        expect(maxMid <= perSecond, "strict ladder (" + label + "): no more than climbers-per-second start up the pile in a second (" + maxMid + ")");
        expect(knightUp && everyoneUp, "strict ladder (" + label + "): when the job ends everyone is on top, nobody left behind");
        boolean awake = true;
        boolean unhurt = true;
        for (Mob w : all) {
            awake &= w.hasAI() && w.hasGravity();
            unhurt &= w.getFallDistance() == 0f;
        }
        expect(awake, "strict ladder (" + label + "): everyone has their legs back");
        expect(unhurt, "strict ladder (" + label + "): and the fall they would have taken is forgotten");
    }

    /** The Knight stops leading (a stuck fallback) while the pile stands: nobody may be left frozen in the air. */
    private static void pileAbandoned() {
        for (int stage = 0; stage < 3; stage++) {
            Env env = arena();
            plateau(5);
            env.cfg.stackGatherSeconds = 8;
            env.cfg.stackBuildSeconds = 6;
            Kit.TestPlayer alex = onTop(env, 5);
            Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
            Mob knight = (Mob) env.walkerOf(h);
            for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
                second(env, 3.5);
            }
            Location foot = knight.getLocation();
            List<Mob> all = new ArrayList<>();
            for (UUID id : h.wights) {
                Mob w = mob(id);
                w.teleport(new Location(WORLD, foot.getX(), foot.getY(), foot.getZ()));
                all.add(w);
            }
            String when = stage == 0 ? "while building" : stage == 1 ? "while climbing" : "while still gathering";
            StackJob.Phase wanted = stage == 0 ? StackJob.Phase.BUILD : stage == 1 ? StackJob.Phase.ASCEND : StackJob.Phase.GATHER;
            for (int t = 0; t < 60 && h.mind.stack != null && h.mind.stack.phase() != wanted; t++) {
                env.tick(1);
            }
            expect(h.mind.stack != null && (stage == 2 || !h.mind.held.isEmpty()), "abandoned (" + when + "): (the job is running" + (stage == 2 ? ")" : " and bodies are held in the pile)"));
            h.mind.fallbackUntil = h.ticksLived + 100_000L;
            env.tick(1);
            boolean awake = knight.hasAI() && knight.hasGravity();
            for (Mob w : all) {
                awake &= w.hasAI() && w.hasGravity();
            }
            expect(awake && h.mind.stack == null && h.mind.held.isEmpty() && h.mind.steps.isEmpty() && h.mind.crew.isEmpty() && !h.mind.crewGo, "abandoned (" + when + "): everyone gets their legs back and the job is dropped");
            expect(h.mind.nextPlanTick <= h.ticksLived, "abandoned (" + when + "): and he plans afresh as soon as he leads again (" + h.mind.nextPlanTick + " now " + h.ticksLived + ")");
        }
    }

    /** Far too many bodies for the climb to clear in time: the job still ends, and leaves nobody frozen or half-way up. */
    private static void pileTimeout() {
        Env env = arena();
        plateau(5);
        env.cfg.stackGatherSeconds = 8;
        env.cfg.stackPerSecond = 1;
        env.cfg.wightsBase = 100;
        env.cfg.wightsStartMax = 100;
        env.cfg.wightsMaxAlive = 100;
        env.cfg.knightReinforceCount = 0;
        env.cfg.reinforceCount = 0;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
            second(env, 3.5);
        }
        Location foot = knight.getLocation();
        List<Mob> all = new ArrayList<>();
        for (UUID id : h.wights) {
            Mob w = mob(id);
            w.teleport(new Location(WORLD, foot.getX(), foot.getY(), foot.getZ()));
            all.add(w);
        }
        List<UUID> crew = new ArrayList<>();
        int ascents = 0;
        for (int t = 0; t < 120 && h.mind.stack != null; t++) {
            env.tick(1);
            if (h.mind.stack != null && h.mind.stack.phase() == StackJob.Phase.ASCEND) {
                ascents++;
                if (crew.isEmpty()) {
                    crew.addAll(h.mind.crew);
                }
            }
        }
        expect(h.mind.stack == null && ascents >= 38, "timeout: the climb gives up after its time limit, not before (" + ascents + " seconds, " + all.size() + " bodies)");
        boolean crewUp = !crew.isEmpty();
        boolean hover = true;
        boolean awake = knight.hasAI() && knight.hasGravity();
        for (Mob w : all) {
            awake &= w.hasAI() && w.hasGravity();
            crewUp &= !crew.contains(w.getUniqueId()) || isTop(w);
            hover &= w.getLocation().getY() < FEET + 1.0 || isTop(w);
        }
        expect(hover, "timeout: and nobody is left hanging in the air half-way up the wall");
        expect(isTop(knight) && crewUp, "timeout: but the Knight and the whole pile are over (" + crew.size() + " in the pile)");
        expect(awake, "timeout: and nobody is left frozen, in the pile or on its side");
    }

    /** A climber dies half-way up, and some bodies are far off: the climb carries on without them. */
    private static void pileCasualty() {
        Env env = arena();
        plateau(5);
        env.cfg.stackGatherSeconds = 8;
        env.cfg.knightReinforceCount = 0;
        env.cfg.reinforceCount = 0;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
            second(env, 3.5);
        }
        Location foot = knight.getLocation();
        List<Mob> all = new ArrayList<>();
        for (UUID id : h.wights) {
            Mob w = mob(id);
            w.teleport(new Location(WORLD, foot.getX(), foot.getY(), foot.getZ()));
            all.add(w);
        }
        for (int t = 0; t < 30 && h.mind.stack != null && h.mind.stack.phase() != StackJob.Phase.BUILD; t++) {
            env.tick(1);
        }
        List<Mob> far = new ArrayList<>();
        for (Mob w : all) {
            if (far.size() < 2 && !h.mind.crew.contains(w.getUniqueId())) {
                w.setAI(false);
                w.teleport(new Location(WORLD, foot.getX() + 25, foot.getY(), foot.getZ()));
                far.add(w);
            }
        }
        expect(far.size() == 2, "casualty: (two bodies are far off)");
        Mob victim = null;
        for (int t = 0; t < 30 && victim == null && h.mind.stack != null; t++) {
            env.tick(1);
            for (Mob w : all) {
                if (victim == null && h.mind.steps.containsKey(w.getUniqueId()) && !h.mind.crew.contains(w.getUniqueId())) {
                    victim = w;
                }
            }
        }
        expect(victim != null, "casualty: (somebody is half-way up)");
        UUID dead = victim.getUniqueId();
        victim.remove();
        env.tick(1);
        expect(!h.mind.steps.containsKey(dead) && !h.mind.held.contains(dead), "casualty: the dead are forgotten at once, from the climb and from the pile");
        int ascents = 0;
        for (int t = 0; t < 60 && h.mind.stack != null; t++) {
            env.tick(1);
            ascents++;
        }
        expect(h.mind.stack == null && ascents < 25, "casualty: and the climb does not wait for them (" + ascents + " more seconds)");
        boolean up = isTop(knight);
        for (Mob w : all) {
            if (w != victim && !far.contains(w)) {
                up &= isTop(w);
            }
        }
        expect(up, "casualty: everyone else gets over");
        boolean stayed = true;
        for (Mob w : far) {
            stayed &= w.getLocation().getY() < FEET + 1.0 && w.getLocation().getX() > foot.getX() + 20;
        }
        expect(stayed, "casualty: bodies far from the wall are not whisked over it");
    }

    /** With the glow turned off nobody in the pile is outlined. */
    private static void pileDark() {
        Env env = arena();
        plateau(5);
        env.cfg.stackGatherSeconds = 8;
        env.cfg.stackGlow = false;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 30.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        for (int t = 0; t < 80 && (h.mind == null || h.mind.stack == null); t++) {
            second(env, 3.5);
        }
        Location foot = knight.getLocation();
        List<Mob> all = new ArrayList<>();
        all.add(knight);
        for (UUID id : h.wights) {
            Mob w = mob(id);
            w.teleport(new Location(WORLD, foot.getX(), foot.getY(), foot.getZ()));
            all.add(w);
        }
        boolean held = false;
        boolean lit = false;
        for (int t = 0; t < 60 && h.mind.stack != null; t++) {
            env.tick(1);
            for (Mob w : all) {
                held |= !w.hasAI();
                lit |= w.hasPotionEffect(PotionEffectType.GLOWING);
            }
        }
        expect(held && h.mind.stack == null, "dark: the pile still forms and climbs");
        expect(!lit, "dark: but nobody glows");
    }

    /** A low terrace, then a high wall: two piles in one march, and the second must free its crew too. */
    private static void twoClimbs() {
        Env env = arena();
        plateau(5);
        for (int x = OX + 22; x <= OX + 30; x++) {
            for (int z = OZ - 60; z <= OZ + 60; z++) {
                for (int y = FEET; y < FEET + 2; y++) {
                    WORLD.stubSetBlock(x, y, z, Material.STONE);
                }
            }
        }
        env.cfg.stackGatherSeconds = 10;
        Kit.TestPlayer alex = onTop(env, 5);
        Horde h = muster(env, alex, OX + 45.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        int piles = 0;
        boolean freshPile = true;
        boolean was = false;
        for (int t = 0; t < 150; t++) {
            second(env, 3.5);
            boolean now = h.mind.stack != null;
            piles += now && !was ? 1 : 0;
            was = now;
            if (now && h.mind.stack.phase() == StackJob.Phase.BUILD) {
                freshPile &= !h.mind.crewGo && h.mind.steps.isEmpty();
            }
        }
        expect(piles == 2, "two climbs: two piles are built, one for each wall (" + piles + ")");
        expect(freshPile, "two climbs: and each one starts afresh, with its crew still to climb");
        boolean awake = true;
        for (UUID id : h.wights) {
            awake &= mob(id).hasAI() && mob(id).hasGravity();
        }
        expect(awake, "two climbs: and after the second one everybody has their legs back");
        expect(knight.getLocation().getY() >= FEET + 4.99 && flat(knight.getLocation(), alex.getLocation()) <= env.cfg.knightEngageDistance + 1.0, "two climbs: the Knight is on top, with his target");
    }

    // ------------------------------------------------------------------ manners and nerves

    private static void manners() {
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        fence();
        Horde h = muster(env, alex, OX + 10.5, FEET, OZ + 0.5);
        Mob knight = (Mob) env.walkerOf(h);
        env.tick(1);
        expect(knight.getTarget() == null, "manners: with a door still between them he does not rush his target, however close");

        // standing in the shape of a slab: his feet are counted as the cell above
        Env slab = arena();
        Kit.TestPlayer bea = slab.player("Bea", OX, OZ);
        WORLD.stubSetBlock(OX + 30, FEET, OZ, Material.STONE);
        Horde hs = muster(slab, bea, OX + 30.5, FEET + 0.5, OZ + 0.5);
        slab.tick(1);
        expect(hs.mind.startY == FEET + 1, "manners: standing inside a slab he plans from the cell above it (" + hs.mind.startY + ")");

        // a Knight thrown ahead along his route does not skip the route
        Env far = arena();
        Kit.TestPlayer cy = far.player("Cy", OX, OZ);
        Horde hf = muster(far, cy, OX + 40.5, FEET, OZ + 0.5);
        Mob kf = (Mob) far.walkerOf(hf);
        far.tick(1);
        kf.teleport(new Location(WORLD, OX + 40.5 - 20, FEET, OZ + 0.5));
        far.tick(1);
        expect(hf.mind.cursor <= 6, "manners: thrown twenty blocks along he moves the plan on only a few steps at a time (" + hf.mind.cursor + ")");

        // "reached" and "near enough to begin"
        Location at = new Location(WORLD, 10.5, 64.0, 10.5);
        expect(FrostCommander.reached(at, new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: standing in the cell");
        expect(FrostCommander.reached(new Location(WORLD, 11.5, 64.0, 10.5), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: a block away is close enough");
        expect(!FrostCommander.reached(new Location(WORLD, 11.7, 64.0, 10.5), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: but not 1.2 blocks east");
        expect(!FrostCommander.reached(new Location(WORLD, 10.5, 64.0, 11.7), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: or 1.2 south");
        expect(FrostCommander.reached(new Location(WORLD, 10.5, 65.5, 10.5), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: one and a half up is within a step");
        expect(!FrostCommander.reached(new Location(WORLD, 10.5, 66.0, 10.5), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: two up is another storey");
        expect(!FrostCommander.reached(new Location(WORLD, 10.5, 62.0, 10.5), new Move(MoveKind.WALK, 10, 64, 10, 0)), "reach: and two down");
        Location top = new Location(WORLD, 10.5, 69.0, 10.5);
        expect(FrostCommander.atTop(new Location(WORLD, 10.8, 69.0, 10.7), top), "top: right where the pile lands them");
        expect(!FrostCommander.atTop(new Location(WORLD, 9.5, 69.0, 10.5), top), "top: a body hanging in the air at the foot of the wall, a block away, is not");
        expect(!FrostCommander.atTop(new Location(WORLD, 10.5, 69.0, 11.4), top), "top: nor nine-tenths of a block off the other way");
        expect(FrostCommander.atTop(new Location(WORLD, 10.5, 68.0, 10.5), top) && FrostCommander.atTop(new Location(WORLD, 10.5, 70.0, 10.5), top), "top: a block either way in height is still standing there");
        expect(!FrostCommander.atTop(new Location(WORLD, 10.5, 67.9, 10.5), top) && !FrostCommander.atTop(new Location(WORLD, 10.5, 70.1, 10.5), top), "top: more is not");
        int[] origin = {10, 64, 10};
        expect(FrostCommander.nearOrigin(new Location(WORLD, 12.5, 64.0, 10.5), origin), "near: two blocks from the gathering cell is near enough");
        expect(!FrostCommander.nearOrigin(new Location(WORLD, 14.5, 64.0, 10.5), origin), "near: four blocks is not");
        expect(FrostCommander.nearOrigin(new Location(WORLD, 10.5, 66.0, 10.5), origin), "near: two blocks up still counts");
        expect(!FrostCommander.nearOrigin(new Location(WORLD, 10.5, 67.0, 10.5), origin), "near: three up does not");
        expect(!FrostCommander.nearOrigin(new Location(WORLD, 10.5, 61.0, 10.5), origin), "near: nor three down");
    }

    private static void nerves() {
        // a Knight who cannot move: when each stage of trouble comes
        Env env = arena();
        env.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        Horde h = muster(env, alex, OX + 40.5, FEET, OZ + 0.5);
        env.tick(1);
        Route first = h.mind.route;
        env.tick(3);
        expect(h.mind.route == first && h.mind.stuckReplans == 1, "nerves: after three still seconds he has had one worry (" + h.mind.stuckReplans + ")");
        env.tick(1);
        expect(h.mind.route != first, "nerves: and next second he plans again");
        env.tick(4);
        expect(h.mind.fallbackUntil == 0, "nerves: it takes three such worries to give up the plan, not fewer");
        env.tick(1);
        expect(h.mind.fallbackUntil > 0, "nerves: and the third is exactly the tenth second");

        // a little progress does not cancel earlier worries, a lot does
        Env brief = arena();
        brief.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer bea = brief.player("Bea", OX, OZ);
        Horde hb = muster(brief, bea, OX + 40.5, FEET, OZ + 0.5);
        brief.tick(7);
        expect(hb.mind.stuckReplans == 2, "nerves: (two worries)");
        second(brief, 1.0);
        second(brief, 1.0);
        brief.tick(5);
        expect(hb.mind.fallbackUntil > 0, "nerves: a short burst of movement does not wipe them out");

        Env lasting = arena();
        lasting.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer cy = lasting.player("Cy", OX, OZ);
        Horde hl = muster(lasting, cy, OX + 60.5, FEET, OZ + 0.5);
        lasting.tick(7);
        expect(hl.mind.stuckReplans == 2, "nerves: (two worries again)");
        seconds(lasting, 10, 1.0);
        lasting.tick(9);
        expect(hl.mind.fallbackUntil == 0, "nerves: but a good stretch of real progress does, and he starts afresh (" + hl.mind.stuckReplans + ")");

        // not stuck when there is a reason to stand
        Env near = arena();
        near.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer dee = near.player("Dee", OX, OZ);
        Horde hn = muster(near, dee, OX + 10.5, FEET, OZ + 0.5);
        near.tick(20);
        expect(hn.mind.fallbackUntil == 0, "nerves: a Knight at his target's side, fighting, is not stuck");

        Env door = arena();
        door.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer eve = door.player("Eve", OX, OZ);
        fence();
        Horde hd = muster(door, eve, OX + 10.5, FEET, OZ + 0.5);
        door.tick(20);
        expect(hd.mind.fallbackUntil == 0 && hd.mind.stuckSeconds < 3, "nerves: nor one close to his target who is held up at a door (" + hd.mind.stuckSeconds + ")");

        Env wait = arena();
        wait.cfg.knightMaxStuckSeconds = 3;
        Kit.TestPlayer flo = wait.player("Flo", OX - 8, OZ);
        lake(Material.LAVA);
        Horde hw = muster(wait, flo, OX + 25.5, FEET, OZ + 0.5);
        second(wait, 3.5);
        hw.mind.bridged = 1_000_000; // no more ice may be made, so he waits at the shore
        Mob kw = (Mob) wait.walkerOf(hw);
        boolean dry = true;
        for (int t = 0; t < 40; t++) {
            second(wait, 3.5);
            Material under = block((int) Math.floor(kw.getLocation().getX()), GROUND, (int) Math.floor(kw.getLocation().getZ()));
            dry &= under != Material.LAVA;
        }
        expect(wait.director.ledger().size() == 0 && hw.mind.fallbackUntil == 0 && hw.mind.stuckReplans == 0, "nerves: waiting for ice is not being stuck");
        expect(dry && kw.getLocation().getX() > OX + 10.9, "nerves: and he waits on the shore rather than walking out onto the lava (x=" + kw.getLocation().getX() + ")");
        int firstBridge = 0;
        while (firstBridge < hw.mind.route.moves().size() && hw.mind.route.moves().get(firstBridge).kind() != MoveKind.BRIDGE) {
            firstBridge++;
        }
        expect(hw.mind.cursor == firstBridge, "nerves: and his plan stays at the first block of ice that is not there yet (" + hw.mind.cursor + " of " + firstBridge + ")");
    }

    private static void ice() {
        // the first ice goes down as soon as the lake is within reach, from a distance
        Env env = arena();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        lake(Material.WATER);
        Horde h = muster(env, alex, OX + 19.0, FEET, OZ + 0.5);
        env.tick(1);
        expect(env.ledger.size() == 2, "ice: from nine blocks off he freezes the two blocks within reach (" + env.ledger.size() + ")");

        Env rate = arena();
        Kit.TestPlayer bea = rate.player("Bea", OX, OZ);
        lake(Material.WATER);
        Horde hr = muster(rate, bea, OX + 12.5, FEET, OZ + 0.5);
        rate.tick(1);
        expect(rate.ledger.size() == 3, "ice: three blocks in a second, however many are in reach (" + rate.ledger.size() + ")");
        long laid = rate.ledger.entries().get(0).expiresMillis();
        rate.now[0] += 1_000L;
        rate.cfg.bridgeEnabled = false;
        rate.tick(1);
        expect(rate.ledger.entries().get(0).expiresMillis() == laid, "ice: and none is touched once bridges are switched off");
        rate.cfg.bridgeEnabled = true;
        rate.tick(1);
        expect(rate.ledger.entries().get(0).expiresMillis() == laid + 1_000L, "ice: (but it is looked after again when they are back on)");

        Env budget = arena();
        budget.cfg.bridgeMaxBlocks = 6;
        Kit.TestPlayer cy = budget.player("Cy", OX, OZ);
        lake(Material.WATER);
        Horde hb = muster(budget, cy, OX + 12.5, FEET, OZ + 0.5);
        budget.tick(30);
        expect(hb.mind.bridged == 18, "ice: he never lays more than three times the longest bridge in one march (" + hb.mind.bridged + ")");

        Env doorFirst = arena();
        Kit.TestPlayer dee = doorFirst.player("Dee", OX + 20, OZ);
        fence();
        for (int x = OX + 6; x <= OX + 11; x++) {
            for (int z = OZ - 30; z <= OZ + 30; z++) {
                WORLD.stubSetBlock(x, GROUND, z, Material.WATER);
            }
        }
        Horde hd = muster(doorFirst, dee, OX + 0.5, FEET, OZ + 0.5);
        doorFirst.tick(2);
        expect(hd.mind.route.bridgeCount() >= 6 && doorFirst.ledger.size() == 0 && doorStands(), "ice: he does not freeze what is behind a door until the door is open (" + doorFirst.ledger.size() + ")");

        Env blue = arena();
        blue.cfg.bridgeMaterial = "BLUE_ICE";
        Kit.TestPlayer eve = blue.player("Eve", OX, OZ);
        lake(Material.WATER);
        muster(blue, eve, OX + 12.5, FEET, OZ + 0.5);
        blue.tick(1);
        expect(block(OX + 10, GROUND, OZ) == Material.BLUE_ICE, "ice: the bridge is made of what the config says");

        Env typo = arena();
        typo.cfg.bridgeMaterial = "NOT_A_BLOCK";
        Kit.TestPlayer flo = typo.player("Flo", OX, OZ);
        lake(Material.WATER);
        muster(typo, flo, OX + 12.5, FEET, OZ + 0.5);
        typo.tick(1);
        expect(block(OX + 10, GROUND, OZ) == Material.PACKED_ICE, "ice: and packed ice if it names nothing real");

        // a bridge block that is no longer liquid (built over, or frozen some other way) is simply walked on
        Env built = arena();
        Kit.TestPlayer gus = built.player("Gus", OX, OZ);
        lake(Material.WATER);
        built.cfg.knightPlanSeconds = 60;
        Horde hg = muster(built, gus, OX + 25.5, FEET, OZ + 0.5);
        second(built, 3.5);
        for (int x = OX + 5; x <= OX + 10; x++) {
            WORLD.stubSetBlock(x, GROUND, OZ, Material.OAK_PLANKS);
        }
        boolean through = false;
        Mob kg = (Mob) built.walkerOf(hg);
        for (int t = 0; t < 9 && !through; t++) {
            second(built, 3.5);
            through = kg.getLocation().getX() < OX + 5;
        }
        expect(through, "ice: if someone has already bridged it for him he walks straight over");
    }
}
