package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrime.Kit.WORLD;
import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** The horde director against a stub world: starting, marching, cold, nova, reinforcing, ending. */
public final class DirectorTest {

    public static void main(String[] args) {
        starting();
        gates();
        marching();
        coldAura();
        nova();
        reinforcing();
        victory();
        otherEndings();
        fallen();
        orphans();
        Kit.finish();
    }

    private static HordeDirector.StartResult begin(Env env, Kit.TestPlayer target) {
        return env.director.tryStart(WORLD, target, true);
    }

    private static double flat(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ------------------------------------------------------------------ starting a horde

    private static void starting() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Kit.TestPlayer bea = env.player("Bea", 610, 600);
        HordeDirector.StartResult r = begin(env, alex);
        expect(r.verdict() == GateVerdict.OK && r.horde() != null, "start: a forced start with an eligible target succeeds");
        Horde h = r.horde();
        expect(env.director.hordes().size() == 1, "start: one horde is active");

        Entity walker = env.walkerOf(h);
        expect(walker instanceof Stray, "start: the walker is a stray");
        expect("walker".equals(env.director.roleOf(walker)), "start: the walker is tagged as walker");
        expect(env.director.hordeOf(walker) == h, "start: the walker belongs to the horde");
        expect(Math.abs(flat(walker.getLocation(), alex.getLocation()) - 80.0) < 1.0, "start: the walker rises about 80 blocks from the target");
        Stray s = (Stray) walker;
        expect(near(s.getHealth(), 180.0) && near(s.getAttribute(Attribute.MAX_HEALTH).getValue(), 180.0), "start: walker health 180");
        expect(near(s.getAttribute(Attribute.SCALE).getValue(), 1.35), "start: walker is large");
        expect(near(s.getAttribute(Attribute.KNOCKBACK_RESISTANCE).getValue(), 0.8), "start: walker resists knockback");
        expect(near(s.getAttribute(Attribute.ATTACK_DAMAGE).getValue(), 9.0), "start: walker hits hard in melee");
        expect(s.getCustomName().contains("Rimewalker") && s.isCustomNameVisible(), "start: walker's name is shown");
        expect(!s.getRemoveWhenFarAway(), "start: walker never despawns by distance");

        int expected = HordeScale.openingWights(env.cfg, 2);
        expect(expected == 14, "start: two players -> 14 wights is the expected army");
        expect(h.wights.size() == expected, "start: the opening army has the right size (" + h.wights.size() + ")");
        boolean allGood = true;
        for (UUID id : h.wights) {
            Entity w = Bukkit.getEntity(id);
            allGood &= w instanceof Zombie && w.isValid() && "wight".equals(env.director.roleOf(w))
                    && env.director.hordeOf(w) == h
                    && near(((Zombie) w).getAttribute(Attribute.MAX_HEALTH).getValue(), 14.0)
                    && near(((Zombie) w).getAttribute(Attribute.SPAWN_REINFORCEMENTS).getValue(), 0.0)
                    && !((Zombie) w).getRemoveWhenFarAway();
        }
        expect(allGood, "start: every wight is a tagged, valid, 14 hp zombie that calls no reinforcements");
        expect(Env.creatures() == 1 + expected, "start: no stray creatures were spawned");

        expect(WORLD.hasStorm() && WORLD.getWeatherDuration() == 25 * 1200, "start: a storm is raised for the length of the event");
        expect(h.bar != null && h.bar.getTitle().contains("Rimewalker"), "start: a boss bar is made");
        expect(env.saves[0] == 1 && env.director.lastEventMillis() == env.now[0], "start: the time is remembered and saved");
        expect(alex.heard("coming for you"), "start: the target gets a personal warning");
        expect(alex.heard("A bitter wind rises") && bea.heard("A bitter wind rises"), "start: everyone in the world hears the warning");
        expect(Env.dispatched("heartbeat report CONFLICT -5 Rimewalker marches on Alex"), "start: the mood system is told");
        expect(h.forced, "start: an admin start is marked forced");

        // Nothing at all when the plugin is not installed / heartbeat reports are off.
        Env quiet = new Env();
        quiet.cfg.heartbeat = false;
        Kit.TestPlayer q = quiet.player("Q", 600, 600);
        begin(quiet, q);
        expect(Bukkit.DISPATCHED.isEmpty(), "start: heartbeat reports can be switched off");
        Env missing = new Env();
        org.bukkit.plugin.PluginManager.ENABLED.clear();
        Kit.TestPlayer m = missing.player("M", 600, 600);
        begin(missing, m);
        expect(Bukkit.DISPATCHED.isEmpty(), "start: no heartbeat command when NexusHeartbeat is not installed");

        // Storm that was already on is left alone.
        Env stormy = new Env();
        WORLD.setStorm(true);
        Kit.TestPlayer st = stormy.player("S", 600, 600);
        Horde sh = begin(stormy, st).horde();
        expect(sh != null, "start: a horde can start in a storm that is already on");
        stormy.director.stopAll();
        expect(WORLD.hasStorm(), "start: and is not cleared when the horde ends");

        // Storm switch off
        Env noStorm = new Env();
        noStorm.cfg.forceStorm = false;
        Kit.TestPlayer ns = noStorm.player("N", 600, 600);
        begin(noStorm, ns);
        expect(!WORLD.hasStorm(), "start: force-storm can be switched off");

        // Boss bar and announcements can be switched off.
        Env plain = new Env();
        plain.cfg.bossBar = false;
        plain.cfg.announce = false;
        Kit.TestPlayer p1 = plain.player("P1", 600, 600);
        Kit.TestPlayer p2 = plain.player("P2", 640, 600);
        Horde ph = begin(plain, p1).horde();
        expect(ph.bar == null, "start: boss bar can be switched off");
        expect(!p2.heard("A bitter wind rises") && p1.heard("coming for you"), "start: world announcement off, personal warning stays");
        plain.tick(3);
        expect(plain.director.hordes().size() == 1, "start: running without a boss bar is fine");
    }

    // ------------------------------------------------------------------ the gate, through the director

    private static void gates() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);

        env.season.value = SeasonStatus.NOT_WINTER;
        expect(env.director.tryStart(WORLD, alex, false).verdict() == GateVerdict.WRONG_SEASON, "gate: no natural horde in summer");
        expect(Env.creatures() == 0 && env.director.hordes().isEmpty() && env.saves[0] == 0, "gate: a refusal spawns and records nothing");
        env.season.value = SeasonStatus.UNKNOWN;
        expect(env.director.tryStart(WORLD, alex, false).verdict() == GateVerdict.SEASON_UNKNOWN, "gate: unknown season refused");

        env.season.value = SeasonStatus.WINTER;
        env.cfg.chancePercent = 0;
        env.director.roll();
        expect(env.director.hordes().isEmpty(), "gate: a 0 percent roll starts nothing");
        env.cfg.chancePercent = 100;
        env.director.roll();
        expect(env.director.hordes().size() == 1, "gate: a certain roll in winter starts a horde");
        Horde h = env.director.hordes().get(0);
        expect(!h.forced, "gate: a natural horde is not forced");
        expect(h.targetName.equals("Alex"), "gate: the only eligible player is hunted");

        env.director.roll();
        expect(env.director.hordes().size() == 1, "gate: the active limit stops a second horde");
        expect(env.director.tryStart(WORLD, alex, true).verdict() == GateVerdict.TOO_MANY_ACTIVE, "gate: forced cannot exceed the active limit");

        env.director.stopAll();
        env.now[0] += 119 * Env.MIN;
        expect(env.director.tryStart(WORLD, alex, false).verdict() == GateVerdict.COOLDOWN, "gate: 119 minutes later still cooling down");
        env.now[0] += 1 * Env.MIN;
        expect(env.director.tryStart(WORLD, alex, false).verdict() == GateVerdict.OK, "gate: 120 minutes later a new horde may start");

        // Who can be hunted
        Env e2 = new Env();
        Kit.TestPlayer safe = e2.player("Safe", 50, 50);
        expect(e2.director.tryStart(WORLD, safe, true).verdict() == GateVerdict.NO_TARGET, "gate: a player at spawn cannot be hunted");
        expect(e2.director.tryStart(WORLD, null, false).verdict() == GateVerdict.NOT_ENOUGH_PLAYERS, "gate: nobody eligible -> not enough players");
        Kit.TestPlayer creative = e2.player("Cre", 600, 600);
        creative.mode = org.bukkit.GameMode.CREATIVE;
        expect(e2.director.tryStart(WORLD, creative, true).verdict() == GateVerdict.NO_TARGET, "gate: creative players cannot be hunted");
        creative.mode = org.bukkit.GameMode.SPECTATOR;
        expect(e2.director.eligibleTargets(WORLD).isEmpty(), "gate: spectators cannot be hunted");
        creative.mode = org.bukkit.GameMode.ADVENTURE;
        expect(e2.director.eligibleTargets(WORLD).size() == 1, "gate: adventure players can");
        creative.setDead(true);
        expect(e2.director.eligibleTargets(WORLD).isEmpty(), "gate: dead players cannot be hunted");
        creative.setDead(false);
        creative.loc = new Location(Kit.NETHER, 600, 64, 600);
        expect(e2.director.eligibleTargets(WORLD).isEmpty(), "gate: players in another world are not hunted from this one");
        creative.loc = new Location(WORLD, 600, 64, 600);
        e2.realms.claimed = l -> l.getX() > 500;
        expect(e2.director.eligibleTargets(WORLD).isEmpty(), "gate: a player inside a claim cannot be hunted");
        expect(e2.director.tryStart(WORLD, creative, true).verdict() == GateVerdict.NO_TARGET, "gate: a forced start cannot target a claim either");
        e2.cfg.useRealms = false;
        expect(e2.director.eligibleTargets(WORLD).size() == 1, "gate: with use-realms off, claims are ignored");

        // Where it may appear
        Env e3 = new Env();
        Kit.TestPlayer p = e3.player("P", 600, 600);
        WORLD.chunkLoaded = (cx, cz) -> false;
        expect(e3.director.tryStart(WORLD, p, true).verdict() == GateVerdict.NO_SPAWN_SITE, "site: no loaded ground -> no horde");
        expect(Env.creatures() == 0 && e3.saves[0] == 0 && !WORLD.hasStorm() && e3.director.lastEventMillis() == 0,
                "site: and nothing was spawned, saved or stormed");
        WORLD.chunkLoaded = null;
        e3.realms.claimed = l -> HordeDirector.distance(l, p.getLocation()) > 50;
        expect(e3.director.tryStart(WORLD, p, true).verdict() == GateVerdict.NO_SPAWN_SITE, "site: a ring of claimed land -> nowhere safe to rise");
        e3.realms.claimed = l -> false;
        WORLD.heightMap = (x, z) -> -64;
        expect(e3.director.tryStart(WORLD, p, true).verdict() == GateVerdict.NO_SPAWN_SITE, "site: no ground (void) -> nowhere to stand");
        WORLD.heightMap = null;
        Horde ok = e3.director.tryStart(WORLD, p, true).horde();
        expect(ok != null, "site: and it works once the ground is back");

        // Liquid ground is refused
        Env e4 = new Env();
        Kit.TestPlayer q = e4.player("Q", 600, 600);
        WORLD.heightMap = (x, z) -> 64;
        for (int x = 480; x <= 720; x++) {
            for (int z = 480; z <= 720; z++) {
                WORLD.stubSetBlock(x, 64, z, Material.WATER);
            }
        }
        expect(e4.director.tryStart(WORLD, q, true).verdict() == GateVerdict.NO_SPAWN_SITE, "site: water is not a place to rise");
        WORLD.heightMap = null;

        // Another plugin may veto
        Env e5 = new Env();
        Kit.TestPlayer v = e5.player("V", 600, 600);
        List<HordeEvent> seen = new ArrayList<>();
        org.bukkit.plugin.PluginManager.HOOK = ev -> {
            if (ev instanceof HordeEvent he) {
                seen.add(he);
                he.setCancelled(true);
            }
        };
        expect(e5.director.tryStart(WORLD, v, true).verdict() == GateVerdict.VETOED, "veto: a cancelled event stops the horde");
        expect(seen.size() == 1 && seen.get(0).getTarget() == v && seen.get(0).getWorld() == WORLD, "veto: the event names the world and target");
        expect(Env.creatures() == 0 && e5.saves[0] == 0 && e5.director.lastEventMillis() == 0, "veto: nothing spawned or recorded");
        org.bukkit.plugin.PluginManager.HOOK = null;
        expect(e5.director.tryStart(WORLD, v, true).horde() != null, "veto: without the veto it starts");

        // Disabled / wrong world
        Env e6 = new Env();
        Kit.TestPlayer d = e6.player("D", 600, 600);
        e6.cfg.enabled = false;
        expect(e6.director.tryStart(WORLD, d, true).verdict() == GateVerdict.DISABLED, "gate: the off switch");
        e6.director.roll();
        expect(e6.director.hordes().isEmpty(), "gate: roll respects the off switch");
        Env e7 = new Env();
        e7.cfg.chancePercent = 100;
        Kit.TestPlayer n = e7.player("Nether", 600, 600);
        n.loc = new Location(Kit.NETHER, 600, 64, 600);
        e7.director.roll();
        expect(e7.director.hordes().isEmpty(), "gate: a player outside the allowed worlds draws no horde");
        e7.cfg.worlds = List.of("world_nether", "world");
        e7.director.roll();
        expect(e7.director.hordes().size() == 1 && e7.director.hordes().get(0).worldName.equals("world_nether"), "gate: allowed worlds are each rolled for");
    }

    // ------------------------------------------------------------------ marching

    private static void marching() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();
        Mob walker = (Mob) env.walkerOf(h);
        double before = flat(walker.getLocation(), alex.getLocation());
        env.tick(1);
        expect(h.ticksLived == 20, "march: a second is 20 ticks");
        Location goal = walker.getPathfinder().lastLocation;
        expect(goal != null, "march: the walker is told where to walk");
        expect(near(flat(walker.getLocation(), goal), 24.0), "march: in strides of 24 blocks");
        expect(near(flat(goal, alex.getLocation()), before - 24.0), "march: straight at the target");
        expect(walker.getTarget() == null, "march: too far to lock on to the target");

        walker.teleport(new Location(WORLD, 600, 65, 630));
        env.tick(1);
        expect(walker.getTarget() == alex, "march: locks on once within 40 blocks");
        expect(near(flat(walker.getPathfinder().lastLocation, alex.getLocation()), 6.0), "march: a short stride when close (30 -> 24 -> 6 left)");
        walker.teleport(new Location(WORLD, 600, 65, 602));
        walker.getPathfinder().lastLocation = null;
        env.tick(1);
        expect(walker.getPathfinder().lastLocation == null, "march: right on top of the target, no more walking");

        // Wights: far ones follow the walker; near ones fight the target.
        Mob far = null;
        Mob near = null;
        for (UUID id : h.wights) {
            Mob w = (Mob) Bukkit.getEntity(id);
            if (far == null) {
                far = w;
            } else if (near == null) {
                near = w;
            }
        }
        far.teleport(new Location(WORLD, 700, 65, 700));
        near.teleport(new Location(WORLD, 610, 65, 600));
        walker.teleport(new Location(WORLD, 650, 65, 650));
        far.setTarget(null);
        near.setTarget(null);
        env.tick(1);
        expect(near.getTarget() == alex, "wights: one near the target goes for it");
        expect(far.getTarget() == null && far.getPathfinder().lastLocation != null
                && flat(far.getPathfinder().lastLocation, walker.getLocation()) < 1.0, "wights: a far one marches to the walker");
        // A wight beside the walker is left to its own devices.
        far.teleport(new Location(WORLD, 652, 65, 650));
        far.getPathfinder().lastLocation = null;
        env.tick(1);
        expect(far.getPathfinder().lastLocation == null, "wights: one already beside the walker is not nagged");
        // A dead wight is forgotten.
        int count = h.wights.size();
        far.remove();
        env.tick(1);
        expect(h.wights.size() == count - 1, "wights: an invalid wight is dropped from the roster");
        // An unloaded one is kept.
        UUID keep = near.getUniqueId();
        Bukkit.ENTITIES.remove(keep);
        WORLD.chunkLoaded = (cx, cz) -> false;
        env.tick(1);
        expect(h.wights.contains(keep), "wights: one in an unloaded chunk stays on the roster");
        WORLD.chunkLoaded = null;
        env.tick(1);
        expect(!h.wights.contains(keep), "wights: but once its chunk is loaded and it is still missing, it is gone");

        // Target logic: a new nearest player is picked when the target logs off.
        Kit.TestPlayer bea = env.player("Bea", 700, 700);
        Bukkit.PLAYERS.remove(alex.getUniqueId());
        env.tick(1);
        expect(h.target.equals(bea.getUniqueId()) && h.targetName.equals("Bea"), "target: it turns on the nearest other player");
        expect(h.ticksWithoutTarget == 0, "target: and is not counting down");
    }

    // ------------------------------------------------------------------ the cold

    private static void coldAura() {
        Env env = new Env();
        Kit.TestPlayer anchor = env.player("Anchor", 600, 600);
        Horde h = begin(env, anchor).horde();
        Mob walker = (Mob) env.walkerOf(h);
        walker.teleport(new Location(WORLD, 600, 64, 620));

        Kit.TestPlayer near = env.player("Near", 600, 610);       // 10 from the walker
        Kit.TestPlayer far = env.player("Far", 600, 700);         // 80 away
        Kit.TestPlayer leather = env.player("Leather", 604, 610);
        leather.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        leather.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        leather.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        leather.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        Kit.TestPlayer warm = env.player("Warm", 596, 610);
        WORLD.stubSetBlock(596, 64, 612, Material.CAMPFIRE);
        Kit.TestPlayer creative = env.player("Creative", 600, 612);
        creative.mode = org.bukkit.GameMode.CREATIVE;
        Kit.TestPlayer claimed = env.player("Claimed", 612, 612);
        env.realms.claimed = l -> l.getX() >= 610 && l.getX() <= 620;

        env.tick(1);
        expect(near.getFreezeTicks() > 60 && near.getFreezeTicks() <= 75, "cold: a player 10 blocks away is chilled (" + near.getFreezeTicks() + ")");
        expect(near.hasPotionEffect(PotionEffectType.SLOWNESS), "cold: and slowed");
        expect(far.getFreezeTicks() == 0 && !far.hasPotionEffect(PotionEffectType.SLOWNESS), "cold: a player out of range is untouched");
        expect(leather.getFreezeTicks() > 0 && leather.getFreezeTicks() * 5 < near.getFreezeTicks(),
                "cold: full leather armour leaves only a trace (" + leather.getFreezeTicks() + " vs " + near.getFreezeTicks() + ")");
        expect(warm.getFreezeTicks() == 0 && !warm.hasPotionEffect(PotionEffectType.SLOWNESS), "cold: a campfire keeps the cold off");
        expect(creative.getFreezeTicks() == 0, "cold: creative players are not frozen");
        expect(claimed.getFreezeTicks() == 0, "cold: nor anyone standing on claimed land");
        env.tick(2);
        expect(near.getFreezeTicks() == near.getMaxFreezeTicks(), "cold: standing there builds to a full freeze");

        // Warmth can be switched off, and warmth blocks are configurable.
        env.cfg.warmthRadius = 0;
        warm.setFreezeTicks(0);
        env.tick(1);
        expect(warm.getFreezeTicks() > 0, "cold: warmth radius 0 turns the fire rule off");
        env.cfg.warmthRadius = 3;
        env.cfg.warmthBlocks = List.of("FURNACE");
        warm.setFreezeTicks(0);
        env.tick(1);
        expect(warm.getFreezeTicks() > 0, "cold: only configured blocks count as warm");
        env.cfg.warmthBlocks = List.of("CAMPFIRE");
        // Warmth reaches exactly the configured radius.
        WORLD.stubSetBlock(596, 64, 612, Material.AIR);
        WORLD.stubSetBlock(596 + 3, 64 + 3, 610 + 3, Material.CAMPFIRE);
        expect(env.director.nearWarmth(new Location(WORLD, 596, 64, 610)), "cold: a fire at exactly the radius counts");
        WORLD.stubSetBlock(596 + 3, 64 + 3, 610 + 3, Material.AIR);
        WORLD.stubSetBlock(596 + 4, 64, 610, Material.CAMPFIRE);
        expect(!env.director.nearWarmth(new Location(WORLD, 596, 64, 610)), "cold: a fire one block past the radius does not");
        expect(!env.director.nearWarmth(null), "cold: null location is not warm");

        // Slowness can be turned off.
        Env env2 = new Env();
        env2.cfg.slownessAmplifier = -1;
        Kit.TestPlayer a2 = env2.player("A2", 600, 600);
        Horde h2 = begin(env2, a2).horde();
        ((Mob) env2.walkerOf(h2)).teleport(new Location(WORLD, 600, 64, 610));
        env2.tick(1);
        expect(a2.getFreezeTicks() > 0 && !a2.hasPotionEffect(PotionEffectType.SLOWNESS), "cold: slowness off, frost still builds");

        // The cold interval is respected: with a 3 second cycle it acts on seconds 0, 3, 6...
        Env env3 = new Env();
        env3.cfg.coldIntervalSeconds = 3;
        Kit.TestPlayer a3 = env3.player("A3", 600, 600);
        Horde h3 = begin(env3, a3).horde();
        ((Mob) env3.walkerOf(h3)).teleport(new Location(WORLD, 600, 64, 610));
        env3.tick(1);
        int first = a3.getFreezeTicks();
        a3.setFreezeTicks(0);
        env3.tick(2);
        expect(first > 0 && a3.getFreezeTicks() == 0, "cold: a 3 second cycle does not act every second");
        env3.tick(1);
        expect(a3.getFreezeTicks() > 0, "cold: but acts again on schedule");
    }

    // ------------------------------------------------------------------ the frost nova

    private static void nova() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();
        Mob walker = (Mob) env.walkerOf(h);
        walker.teleport(new Location(WORLD, 600, 64, 700));

        env.tick(40);
        expect(alex.lastDamage == 0.0, "nova: nobody close, no nova");
        expect(h.nextNovaTick == 360, "nova: and the timer is not spent");

        walker.teleport(new Location(WORLD, 600, 64, 604));
        Kit.TestPlayer far = env.player("Far", 600, 660);
        Kit.TestPlayer creative = env.player("Cre", 602, 604);
        creative.mode = org.bukkit.GameMode.CREATIVE;
        env.tick(1);
        expect(near(alex.lastDamage, 4.0) && alex.lastDamager == walker, "nova: the first close player is hit for 4 by the walker");
        expect(alex.getFreezeTicks() == alex.getMaxFreezeTicks(), "nova: frozen solid");
        expect(alex.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() == 2, "nova: heavily slowed");
        expect(alex.getVelocity().getY() > 0 && alex.getVelocity().length() > 0.5, "nova: shoved away");
        expect(alex.heard("cold bites"), "nova: and told so");
        expect(far.lastDamage == 0.0 && creative.lastDamage == 0.0, "nova: only close, playing people are hit");
        double after = alex.lastDamage;
        alex.lastDamage = 0.0;
        env.tick(5);
        expect(alex.lastDamage == 0.0, "nova: the cooldown holds");
        env.tick(18);
        expect(near(after, alex.lastDamage), "nova: and it comes round again");

        // Safe ground
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        Horde h2 = begin(e2, p).horde();
        ((Mob) e2.walkerOf(h2)).teleport(new Location(WORLD, 600, 64, 604));
        e2.realms.claimed = l -> HordeDirector.distance(l, p.getLocation()) < 1.0;
        e2.tick(20);
        expect(p.lastDamage == 0.0, "nova: never hurts someone standing on claimed land");

        // Switched off, and zero damage
        Env e3 = new Env();
        e3.cfg.novaEnabled = false;
        Kit.TestPlayer a = e3.player("A", 600, 600);
        Horde h3 = begin(e3, a).horde();
        ((Mob) e3.walkerOf(h3)).teleport(new Location(WORLD, 600, 64, 604));
        e3.tick(40);
        expect(a.lastDamage == 0.0, "nova: can be switched off");
        Env e4 = new Env();
        e4.cfg.novaDamage = 0.0;
        Kit.TestPlayer b = e4.player("B", 600, 600);
        Horde h4 = begin(e4, b).horde();
        ((Mob) e4.walkerOf(h4)).teleport(new Location(WORLD, 600, 64, 604));
        e4.tick(20);
        expect(b.lastDamage == 0.0 && b.getFreezeTicks() == b.getMaxFreezeTicks(), "nova: zero damage still freezes");
    }

    // ------------------------------------------------------------------ more wights

    private static void reinforcing() {
        Env env = new Env();
        env.cfg.wightsMaxAlive = 16;
        env.cfg.wightsStartMax = 16;
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();
        expect(h.wights.size() == 12, "reinforce: starts with 12 (one player)");
        env.tick(19);
        expect(h.wights.size() == 12, "reinforce: nothing before the first interval");
        env.tick(1);
        expect(h.wights.size() == 15, "reinforce: a batch of three at 20 seconds");
        env.tick(20);
        expect(h.wights.size() == 16, "reinforce: the next batch is trimmed to the cap");
        env.tick(60);
        expect(h.wights.size() == 16, "reinforce: never above the cap");
        // Losing some makes room again.
        for (UUID id : new ArrayList<>(h.wights).subList(0, 5)) {
            Bukkit.getEntity(id).remove();
        }
        env.tick(1);
        expect(h.wights.size() == 11, "reinforce: five fell");
        env.tick(20);
        expect(h.wights.size() >= 14, "reinforce: the walker raises replacements (" + h.wights.size() + ")");

        // Not in protected ground
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        Horde h2 = begin(e2, p).horde();
        int before = h2.wights.size();
        e2.realms.claimed = l -> l.getX() < 700 && HordeDirector.distance(l, ((Mob) e2.walkerOf(h2)).getLocation()) < 40
                && HordeDirector.distance(l, ((Mob) e2.walkerOf(h2)).getLocation()) > 0.001;
        e2.tick(20);
        expect(h2.wights.size() <= before, "reinforce: no one is raised on claimed ground");
        // Raising with nowhere safe returns null
        expect(e2.director.raiseWight(WORLD, h2, new Location(WORLD, 0, 64, 0), 1.0) == null, "reinforce: raising at spawn is refused");
        e2.cfg.wightsMaxAlive = h2.wights.size();
        e2.realms.claimed = l -> false;
        expect(e2.director.raiseWight(WORLD, h2, new Location(WORLD, 600, 64, 600), 1.0) == null, "reinforce: raising at the cap is refused");
    }

    // ------------------------------------------------------------------ victory

    private static void victory() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Kit.TestPlayer bea = env.player("Bea", 610, 600);
        Horde h = begin(env, alex).horde();
        List<UUID> wights = new ArrayList<>(h.wights);
        Stray walker = (Stray) env.walkerOf(h);
        walker.teleport(new Location(WORLD, 600, 64, 640));
        env.tick(2);
        alex.messages.clear();

        EntityDeathEvent death = new EntityDeathEvent(walker);
        death.getDrops().add(new ItemStack(Material.BONE, 2));
        death.setDroppedExp(5);
        env.director.onWalkerDeath(h, death, alex);

        expect(env.director.hordes().isEmpty(), "victory: the horde is over");
        boolean allGone = true;
        for (UUID id : wights) {
            allGone &= Bukkit.getEntity(id) == null;
        }
        expect(allGone, "victory: every wight crumbled");
        expect(h.wights.isEmpty(), "victory: the roster is cleared");
        expect(walker.isValid(), "victory: the dying walker itself is left to the death event");
        int blueIce = 0;
        int diamonds = 0;
        int echo = 0;
        boolean bone = false;
        for (ItemStack s : death.getDrops()) {
            if (s.getType() == Material.BLUE_ICE) {
                blueIce += s.getAmount();
            } else if (s.getType() == Material.DIAMOND) {
                diamonds += s.getAmount();
            } else if (s.getType() == Material.ECHO_SHARD) {
                echo += s.getAmount();
            } else if (s.getType() == Material.BONE) {
                bone = true;
            }
        }
        expect(blueIce == 8 && diamonds == 3 && echo == 1, "victory: the reward drops (8 blue ice, 3 diamond, 1 echo shard)");
        expect(!bone, "victory: the ordinary drops are replaced");
        expect(death.getDroppedExp() == 500, "victory: 500 experience");
        expect(h.bar.getPlayers().isEmpty(), "victory: the boss bar is cleared");
        expect(!WORLD.hasStorm(), "victory: the storm we raised is cleared");
        expect(alex.heard("has fallen") && bea.heard("has fallen"), "victory: the world is told");
        expect(alex.heard("Alex struck the final blow"), "victory: the killer is named");
        expect(Env.dispatched("heartbeat report COMMUNITY 6 Rimewalker was slain by Alex"), "victory: the mood system hears the good news");
        expect(env.saves[0] == 2, "victory: state saved again");

        // Ending twice does nothing more.
        Bukkit.DISPATCHED.clear();
        env.director.end(h, HordeEnd.ADMIN);
        expect(Bukkit.DISPATCHED.isEmpty(), "victory: ending an ended horde is a no-op");

        // Without a killer, and counting the dead.
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        Horde h2 = begin(e2, p).horde();
        h2.playersLost = 2;
        EntityDeathEvent d2 = new EntityDeathEvent((Stray) e2.walkerOf(h2));
        e2.director.onWalkerDeath(h2, d2, null);
        expect(Env.dispatched("COMMUNITY 6 Rimewalker was slain after claiming 2 life(s)"), "victory: lives lost are mentioned");
        expect(!p.heard("struck the final blow"), "victory: no killer, no credit line");

        // The storm stays if another horde is still in that world.
        Env e3 = new Env();
        e3.cfg.maxConcurrent = 2;
        Kit.TestPlayer a = e3.player("A", 600, 600);
        Kit.TestPlayer b = e3.player("B", 700, 700);
        Horde one = e3.director.tryStart(WORLD, a, true).horde();
        Horde two = e3.director.tryStart(WORLD, b, true).horde();
        expect(one != null && two != null && e3.director.hordes().size() == 2, "storm: two hordes at once when allowed");
        e3.director.end(one, HordeEnd.ADMIN);
        expect(WORLD.hasStorm(), "storm: stays while another horde walks");
        e3.director.end(two, HordeEnd.ADMIN);
        expect(!WORLD.hasStorm(), "storm: and goes when the last horde ends");
        // clear-on-end off keeps it
        Env e4 = new Env();
        e4.cfg.clearStormOnEnd = false;
        Kit.TestPlayer c = e4.player("C", 600, 600);
        e4.director.end(begin(e4, c).horde(), HordeEnd.ADMIN);
        expect(WORLD.hasStorm(), "storm: clear-on-end off leaves it");
    }

    // ------------------------------------------------------------------ the other endings

    private static void otherEndings() {
        // Time runs out
        Env env = new Env();
        env.cfg.maxDurationMinutes = 1;
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();
        Entity walker = env.walkerOf(h);
        List<UUID> wights = new ArrayList<>(h.wights);
        env.tick(60);
        expect(env.director.hordes().size() == 1, "end: still walking just under the limit");
        env.tick(2);
        expect(env.director.hordes().isEmpty(), "end: faded when time runs out");
        expect(!walker.isValid() && Bukkit.getEntity(wights.get(0)) == null, "end: the walker and wights are gone");
        expect(alex.heard("The cold lifts"), "end: faded message");
        expect(Env.dispatched("heartbeat report NATURE 1 The cold passed: Rimewalker faded away (faded)"), "end: faded reported");

        // Winter ends
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        e2.cfg.chancePercent = 100;
        e2.director.roll();
        expect(e2.director.hordes().size() == 1, "season: a natural horde in winter");
        e2.season.value = SeasonStatus.UNKNOWN;
        e2.tick(2);
        expect(e2.director.hordes().size() == 1, "season: an unreachable season plugin does not end it");
        e2.season.value = SeasonStatus.NOT_WINTER;
        e2.tick(1);
        expect(e2.director.hordes().isEmpty() && p.heard("Winter is over"), "season: it melts away when winter ends");
        expect(Env.creatures() == 0, "season: nothing left behind");
        // A forced horde ignores the season.
        Env e3 = new Env();
        e3.season.value = SeasonStatus.NOT_WINTER;
        Kit.TestPlayer f = e3.player("F", 600, 600);
        begin(e3, f);
        e3.tick(5);
        expect(e3.director.hordes().size() == 1, "season: an admin-started horde runs out of season");
        // Season rule can be turned off.
        Env e4 = new Env();
        e4.cfg.chancePercent = 100;
        e4.cfg.seasonRequired = false;
        e4.season.value = SeasonStatus.NOT_WINTER;
        Kit.TestPlayer g = e4.player("G", 600, 600);
        e4.director.roll();
        e4.tick(3);
        expect(e4.director.hordes().size() == 1, "season: winter-only off lets a horde run in any season");

        // The cold cannot pass safe ground
        Env e5 = new Env();
        Kit.TestPlayer q = e5.player("Q", 600, 600);
        Horde h5 = begin(e5, q).horde();
        Entity w5 = e5.walkerOf(h5);
        w5.teleport(new Location(WORLD, 20, 64, 20));
        e5.tick(1);
        expect(e5.director.hordes().isEmpty() && q.heard("cannot pass the wards"), "zones: a walker reaching safe ground dissolves the horde");
        expect(Env.creatures() == 0, "zones: and everything crumbles");
        // claimed ground works the same
        Env e6 = new Env();
        Kit.TestPlayer r = e6.player("R", 600, 600);
        Horde h6 = begin(e6, r).horde();
        Entity w6 = e6.walkerOf(h6);
        e6.realms.claimed = l -> HordeDirector.distance(l, w6.getLocation()) < 1.0;
        e6.tick(1);
        expect(e6.director.hordes().isEmpty(), "zones: a walker at a claim's edge dissolves the horde");
        // a single wight on safe ground crumbles alone
        Env e7 = new Env();
        Kit.TestPlayer s = e7.player("S", 600, 600);
        Horde h7 = begin(e7, s).horde();
        UUID victim = h7.wights.iterator().next();
        Entity wv = Bukkit.getEntity(victim);
        wv.teleport(new Location(WORLD, 30, 64, 30));
        int before = h7.wights.size();
        e7.tick(1);
        expect(!wv.isValid() && !h7.wights.contains(victim) && h7.wights.size() == before - 1, "zones: one wight on safe ground crumbles alone");
        expect(e7.director.hordes().size() == 1, "zones: and the horde carries on");

        // Nobody left to hunt
        Env e8 = new Env();
        Kit.TestPlayer t = e8.player("T", 600, 600);
        Horde h8 = begin(e8, t).horde();
        Bukkit.PLAYERS.remove(t.getUniqueId());
        e8.tick(44);
        expect(e8.director.hordes().size() == 1 && h8.ticksWithoutTarget == 880, "lost: still searching just under the limit");
        e8.tick(2);
        expect(e8.director.hordes().isEmpty(), "lost: it gives up after 45 seconds");
        Env e9 = new Env();
        Kit.TestPlayer u = e9.player("U", 600, 600);
        Horde h9 = begin(e9, u).horde();
        Bukkit.PLAYERS.remove(u.getUniqueId());
        e9.tick(20);
        Kit.online(u);
        e9.tick(1);
        expect(h9.ticksWithoutTarget == 0, "lost: the count resets when a target returns");
        u.setDead(true);
        e9.tick(1);
        expect(h9.ticksWithoutTarget == 20, "lost: a dead target counts as gone");
        u.setDead(false);
        u.loc = new Location(WORLD, 20, 64, 20);
        e9.tick(1);
        expect(h9.ticksWithoutTarget == 40, "lost: a target who reaches safe ground counts as gone");
        e9.tick(44);
        expect(e9.director.hordes().isEmpty() && u.heard("lost the scent"), "lost: and the lost-scent message is shown");

        // Walker missing from the world
        Env e10 = new Env();
        Kit.TestPlayer v = e10.player("V", 600, 600);
        Horde h10 = begin(e10, v).horde();
        Entity w10 = e10.walkerOf(h10);
        e10.tick(1); // sees it once, so it knows where it was
        Bukkit.ENTITIES.remove(w10.getUniqueId());
        WORLD.chunkLoaded = (cx, cz) -> false; // ...because its chunk unloaded
        e10.tick(119);
        expect(e10.director.hordes().size() == 1, "missing: patient for under two minutes while its chunk is unloaded");
        Bukkit.ENTITIES.put(w10.getUniqueId(), w10);
        e10.tick(1);
        expect(h10.ticksWalkerMissing == 0, "missing: the count resets when the walker is back");
        Bukkit.ENTITIES.remove(w10.getUniqueId());
        e10.tick(121);
        expect(e10.director.hordes().isEmpty(), "missing: fades after two minutes of absence");
        WORLD.chunkLoaded = null;
        Env e10b = new Env();
        Kit.TestPlayer v2 = e10b.player("V2", 600, 600);
        Horde h10b = begin(e10b, v2).horde();
        e10b.tick(1);
        e10b.walkerOf(h10b).remove(); // really gone, chunk loaded
        e10b.tick(1);
        expect(e10b.director.hordes().isEmpty() && Env.creatures() == 0, "missing: a walker that is really gone ends the horde at once");

        // An admin stops it
        Env e11 = new Env();
        Kit.TestPlayer w = e11.player("W", 600, 600);
        begin(e11, w);
        expect(e11.director.stopAll() == 1 && e11.director.hordes().isEmpty() && Env.creatures() == 0, "stop: stopAll ends and clears");
        expect(e11.director.stopAll() == 0, "stop: nothing more to stop");
        expect(w.heard("The cold lifts"), "stop: the faded message is shown");

        // Shutdown is silent
        Env e12 = new Env();
        Kit.TestPlayer x = e12.player("X", 600, 600);
        begin(e12, x);
        x.messages.clear();
        Bukkit.DISPATCHED.clear();
        e12.director.shutdown();
        expect(e12.director.hordes().isEmpty() && Env.creatures() == 0, "shutdown: everything is cleared");
        expect(x.messages.isEmpty() && Bukkit.DISPATCHED.isEmpty(), "shutdown: with no announcements or reports");
        expect(!WORLD.hasStorm(), "shutdown: and the storm we raised is cleared");

        // The world vanishing is survivable
        Env e13 = new Env();
        Kit.TestPlayer y = e13.player("Y", 600, 600);
        begin(e13, y);
        Bukkit.WORLDS.remove("world");
        e13.tick(1);
        expect(e13.director.hordes().isEmpty() || e13.director.hordes().size() == 1, "world: a missing world does not crash the loop");
    }

    // ------------------------------------------------------------------ a player falls

    private static void fallen() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();
        int before = h.wights.size();

        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == before && h.playersLost == 0, "fallen: a death with no horde hit before it raises nothing");

        env.director.noteHit(alex, h);
        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == before + 1 && h.playersLost == 1, "fallen: a player the horde hurt rises as a wight");
        expect(Env.dispatched("heartbeat report DEATH -3 Alex fell to the Rimebound"), "fallen: the mood system is told");
        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == before + 1, "fallen: one hit only counts for one death");

        env.director.noteHit(alex, h);
        env.now[0] += 9_000;
        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == before + 1 && h.playersLost == 1, "fallen: a hit more than 8 seconds ago does not count");

        env.director.noteHit(alex, h);
        env.now[0] += 7_000;
        env.director.onPlayerDeath(alex);
        expect(h.playersLost == 2, "fallen: a hit 7 seconds ago still counts");

        env.cfg.raiseFallen = false;
        int count = h.wights.size();
        env.director.noteHit(alex, h);
        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == count && h.playersLost == 3, "fallen: raise-fallen off still counts the loss");
        env.cfg.raiseFallen = true;

        env.cfg.wightsMaxAlive = h.wights.size();
        env.director.noteHit(alex, h);
        env.director.onPlayerDeath(alex);
        expect(h.wights.size() == count, "fallen: at the cap no extra wight rises");

        // The horde is over before the death
        env.director.noteHit(alex, h);
        env.director.stopAll();
        Bukkit.DISPATCHED.clear();
        env.director.onPlayerDeath(alex);
        expect(Bukkit.DISPATCHED.isEmpty(), "fallen: nothing happens once the horde has ended");

        // Old hits are pruned by the loop.
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        Horde h2 = begin(e2, p).horde();
        e2.director.noteHit(p, h2);
        e2.now[0] += 20_000;
        e2.tick(1);
        e2.director.onPlayerDeath(p);
        expect(h2.playersLost == 0, "fallen: stale hits are forgotten");
    }

    // ------------------------------------------------------------------ left-overs

    private static void orphans() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = begin(env, alex).horde();

        Zombie plain = WORLD.spawn(new Location(WORLD, 600, 64, 600), Zombie.class);
        Zombie stale = WORLD.spawn(new Location(WORLD, 601, 64, 600), Zombie.class);
        stale.getPersistentDataContainer().set(env.keys.mobHorde, PersistentDataType.STRING, UUID.randomUUID().toString());
        stale.getPersistentDataContainer().set(env.keys.mobRole, PersistentDataType.STRING, "wight");
        Zombie garbled = WORLD.spawn(new Location(WORLD, 602, 64, 600), Zombie.class);
        garbled.getPersistentDataContainer().set(env.keys.mobHorde, PersistentDataType.STRING, "not-a-uuid");
        garbled.getPersistentDataContainer().set(env.keys.mobRole, PersistentDataType.STRING, "wight");
        Zombie noHorde = WORLD.spawn(new Location(WORLD, 603, 64, 600), Zombie.class);
        noHorde.getPersistentDataContainer().set(env.keys.mobRole, PersistentDataType.STRING, "walker");

        int removed = env.director.sweepOrphans(new ArrayList<>(Bukkit.ENTITIES.values()));
        expect(removed == 3, "orphans: three left-overs removed (" + removed + ")");
        expect(plain.isValid(), "orphans: ordinary zombies are left alone");
        expect(!stale.isValid() && !garbled.isValid() && !noHorde.isValid(), "orphans: stale, garbled and unattached horde creatures go");
        expect(Env.creatures() == 1 + 1 + h.wights.size(), "orphans: the live horde is untouched");

        // A wight whose chunk was unloaded when the horde ended is swept when it loads again.
        UUID id = h.wights.iterator().next();
        Entity sleeper = Bukkit.getEntity(id);
        Bukkit.ENTITIES.remove(id);
        env.director.stopAll();
        expect(sleeper.isValid(), "orphans: an unloaded wight could not be removed when the horde ended");
        List<Entity> loading = new ArrayList<>();
        loading.add(sleeper);
        org.bukkit.event.world.EntitiesLoadEvent load = new org.bukkit.event.world.EntitiesLoadEvent(loading);
        env.listener.onEntitiesLoad(load);
        expect(!sleeper.isValid(), "orphans: it crumbles when its chunk loads");
    }
}
