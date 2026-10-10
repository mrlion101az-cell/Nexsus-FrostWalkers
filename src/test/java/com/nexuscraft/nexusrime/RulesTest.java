package com.nexuscraft.nexusrime;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.List;
import java.util.Random;

import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** The pure rules: nothing here touches a world. */
public final class RulesTest {

    private static final long MIN = 60_000L;

    private static GateFacts facts(boolean forced, SeasonStatus season, String world, int active, long now, long last,
                                   int eligible, boolean target, double roll) {
        return new GateFacts(forced, season, world, active, now, last, eligible, target, roll);
    }

    private static GateFacts good() {
        return facts(false, SeasonStatus.WINTER, "world", 0, 10_000 * MIN, 0, 2, true, 1.0);
    }

    public static void main(String[] args) {
        gate();
        targets();
        damage();
        cold();
        scale();
        zones();
        spawnPlanner();
        config();
        Kit.finish();
    }

    // ------------------------------------------------------------------ may a horde begin?

    private static void gate() {
        RimeConfig c = new RimeConfig();
        expect(GateRules.evaluate(c, good()) == GateVerdict.OK, "gate: all clear -> OK");

        RimeConfig off = new RimeConfig();
        off.enabled = false;
        expect(GateRules.evaluate(off, good()) == GateVerdict.DISABLED, "gate: disabled");
        expect(GateRules.evaluate(off, facts(true, SeasonStatus.WINTER, "world", 0, 0, 0, 2, true, 1)) == GateVerdict.DISABLED,
                "gate: forced still cannot beat the off switch");

        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world_nether", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.WORLD_NOT_ALLOWED,
                "gate: world not on the list");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.WINTER, "world_nether", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.WORLD_NOT_ALLOWED,
                "gate: forced still cannot pick a forbidden world");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, null, 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.WORLD_NOT_ALLOWED,
                "gate: null world refused");
        RimeConfig upper = new RimeConfig();
        expect(GateRules.worldAllowed(upper, "WORLD"), "gate: world names are not case sensitive");

        // season
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.NOT_WINTER, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.WRONG_SEASON,
                "gate: summer refused");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.NOT_WINTER, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: forced start works out of season");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.UNKNOWN, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.SEASON_UNKNOWN,
                "gate: unknown season refused by default");
        RimeConfig lax = new RimeConfig();
        lax.allowWhenSeasonsMissing = true;
        expect(GateRules.evaluate(lax, facts(false, SeasonStatus.UNKNOWN, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: unknown season allowed when configured");
        expect(GateRules.evaluate(lax, facts(false, SeasonStatus.NOT_WINTER, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.WRONG_SEASON,
                "gate: the missing-plugin switch never lets a known summer through");
        RimeConfig anySeason = new RimeConfig();
        anySeason.seasonRequired = false;
        expect(GateRules.evaluate(anySeason, facts(false, SeasonStatus.NOT_WINTER, "world", 0, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: winter-only switched off allows any season");

        // active limit
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 1, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.TOO_MANY_ACTIVE,
                "gate: one already out");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.WINTER, "world", 1, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.TOO_MANY_ACTIVE,
                "gate: forced still respects the active limit");
        RimeConfig two = new RimeConfig();
        two.maxConcurrent = 2;
        expect(GateRules.evaluate(two, facts(false, SeasonStatus.WINTER, "world", 1, 10_000 * MIN, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: a second is fine when max-concurrent is 2");

        // cooldown (120 minutes by default)
        long now = 10_000 * MIN;
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, now - 119 * MIN, 2, true, 1)) == GateVerdict.COOLDOWN,
                "gate: 119 minutes after is still cooling down");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, now - 120 * MIN, 2, true, 1)) == GateVerdict.OK,
                "gate: exactly 120 minutes after is allowed");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.WINTER, "world", 0, now, now - 1 * MIN, 2, true, 1)) == GateVerdict.OK,
                "gate: forced skips the cooldown");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: never having had one means no cooldown");
        expect(GateRules.cooldownMinutesLeft(c, now, now - 100 * MIN) == 20, "gate: 20 minutes left");
        expect(GateRules.cooldownMinutesLeft(c, now, now - 119 * MIN - 1000) == 1, "gate: partial minute rounds up");
        expect(GateRules.cooldownMinutesLeft(c, now, now - 500 * MIN) == 0, "gate: none left");
        expect(GateRules.cooldownMinutesLeft(c, now, 0) == 0, "gate: never had one -> none left");

        // players and target
        RimeConfig three = new RimeConfig();
        three.minPlayers = 3;
        expect(GateRules.evaluate(three, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 1)) == GateVerdict.NOT_ENOUGH_PLAYERS,
                "gate: too few players");
        expect(GateRules.evaluate(three, facts(true, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 1)) == GateVerdict.OK,
                "gate: forced skips the player minimum");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, false, 1)) == GateVerdict.NO_TARGET,
                "gate: nobody to hunt");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.WINTER, "world", 0, now, 0, 2, false, 1)) == GateVerdict.NO_TARGET,
                "gate: forced still needs a target");

        // dice (8 percent by default)
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 7.99)) == GateVerdict.OK, "gate: roll under chance passes");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 8.0)) == GateVerdict.NO_ROLL, "gate: roll at chance fails");
        expect(GateRules.evaluate(c, facts(true, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 99.0)) == GateVerdict.OK, "gate: forced ignores the dice");
        RimeConfig never = new RimeConfig();
        never.chancePercent = 0;
        expect(GateRules.evaluate(never, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, true, 0.0)) == GateVerdict.NO_ROLL, "gate: 0 percent never fires");

        // ordering: first match wins
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.NOT_WINTER, "world", 1, now, now, 0, false, 99)) == GateVerdict.WRONG_SEASON,
                "gate: season is checked before everything after it");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 1, now, now, 0, false, 99)) == GateVerdict.TOO_MANY_ACTIVE,
                "gate: active limit before cooldown");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, now, 0, false, 99)) == GateVerdict.COOLDOWN,
                "gate: cooldown before player count");
        expect(GateRules.evaluate(three, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 0, false, 99)) == GateVerdict.NOT_ENOUGH_PLAYERS,
                "gate: player count before target");
        expect(GateRules.evaluate(c, facts(false, SeasonStatus.WINTER, "world", 0, now, 0, 2, false, 99)) == GateVerdict.NO_TARGET,
                "gate: target before dice");
    }

    // ------------------------------------------------------------------ who can be hunted

    private static void targets() {
        expect(TargetRules.eligible(new TargetFacts(true, true, true, true, false)), "target: a normal player");
        expect(!TargetRules.eligible(new TargetFacts(false, true, true, true, false)), "target: offline");
        expect(!TargetRules.eligible(new TargetFacts(true, false, true, true, false)), "target: dead");
        expect(!TargetRules.eligible(new TargetFacts(true, true, false, true, false)), "target: in another world");
        expect(!TargetRules.eligible(new TargetFacts(true, true, true, false, false)), "target: creative/spectator");
        expect(!TargetRules.eligible(new TargetFacts(true, true, true, true, true)), "target: standing on safe ground");
    }

    // ------------------------------------------------------------------ damage

    private static void damage() {
        RimeConfig c = new RimeConfig();
        expect(near(DamageRules.walkerDamage(c, DamageKind.ORDINARY, 8.0), 0.4), "damage: a sword barely scratches the walker (8 -> 0.4)");
        expect(near(DamageRules.walkerDamage(c, DamageKind.FORGED, 7.0), 14.0), "damage: the forged blade doubles the swing");
        expect(near(DamageRules.walkerDamage(c, DamageKind.FIRE, 1.0), 3.0), "damage: a fire tick is tripled");
        expect(near(DamageRules.walkerDamage(c, DamageKind.FIRE, 4.0), 4.0), "damage: lava is capped per hit (4*3 -> 4)");
        expect(near(DamageRules.walkerDamage(c, DamageKind.FIRE, 100.0), 4.0), "damage: huge fire still capped");
        expect(DamageRules.walkerDamage(c, DamageKind.ORDINARY, -5.0) == 0.0, "damage: negative input never heals");
        expect(DamageRules.walkerDamage(c, DamageKind.FORGED, -5.0) == 0.0, "damage: negative forged input never heals");
        // The weakness ordering: forged > fire-capped > ordinary for a typical swing/tick
        expect(DamageRules.walkerDamage(c, DamageKind.FORGED, 7.0) > DamageRules.walkerDamage(c, DamageKind.ORDINARY, 7.0) * 10,
                "damage: forged hits at least 10x harder than ordinary");

        expect(near(DamageRules.wightDamage(c, DamageKind.FORGED, 7.0), 42.0), "damage: forged shatters a wight (7 -> 42, wight has 14 hp)");
        expect(DamageRules.wightDamage(c, DamageKind.FORGED, 7.0) >= c.wightHealth, "damage: one forged swing outweighs a wight's health");
        expect(near(DamageRules.wightDamage(c, DamageKind.FIRE, 1.0), 2.5), "damage: wights burn harder");
        expect(near(DamageRules.wightDamage(c, DamageKind.ORDINARY, 5.0), 5.0), "damage: ordinary damage hurts a wight normally");
        expect(DamageRules.wightDamage(c, DamageKind.ORDINARY, -1.0) == 0.0, "damage: negative never heals a wight");

        expect(DamageRules.classify(DamageCause.FIRE, false) == DamageKind.FIRE, "classify: fire");
        expect(DamageRules.classify(DamageCause.FIRE_TICK, false) == DamageKind.FIRE, "classify: burning");
        expect(DamageRules.classify(DamageCause.LAVA, false) == DamageKind.FIRE, "classify: lava");
        expect(DamageRules.classify(DamageCause.HOT_FLOOR, false) == DamageKind.FIRE, "classify: magma");
        expect(DamageRules.classify(DamageCause.ENTITY_ATTACK, true) == DamageKind.FORGED, "classify: blade melee");
        expect(DamageRules.classify(DamageCause.ENTITY_SWEEP_ATTACK, true) == DamageKind.FORGED, "classify: blade sweep");
        expect(DamageRules.classify(DamageCause.ENTITY_ATTACK, false) == DamageKind.ORDINARY, "classify: other melee");
        expect(DamageRules.classify(DamageCause.PROJECTILE, true) == DamageKind.ORDINARY, "classify: an arrow is not the blade even if you hold it");
        expect(DamageRules.classify(DamageCause.FALL, true) == DamageKind.ORDINARY, "classify: a fall is ordinary");
        expect(DamageRules.classify(DamageCause.ENTITY_EXPLOSION, false) == DamageKind.ORDINARY, "classify: explosion is ordinary");
        expect(DamageRules.classify(null, true) == DamageKind.ORDINARY, "classify: null cause is ordinary");
    }

    // ------------------------------------------------------------------ the cold

    private static void cold() {
        RimeConfig c = new RimeConfig(); // radius 40, 100 per cycle, 25% per leather piece
        expect(ColdRules.frostGain(c, new ColdFacts(0, 0, false)) == 100, "cold: point blank, bare, full frost");
        expect(ColdRules.frostGain(c, new ColdFacts(20, 0, false)) == 50, "cold: halfway, half frost");
        expect(ColdRules.frostGain(c, new ColdFacts(40, 0, false)) == 0, "cold: at the edge, none");
        expect(ColdRules.frostGain(c, new ColdFacts(90, 0, false)) == 0, "cold: far away, none");
        expect(ColdRules.frostGain(c, new ColdFacts(0, 1, false)) == 75, "cold: one leather piece cuts a quarter");
        expect(ColdRules.frostGain(c, new ColdFacts(0, 2, false)) == 50, "cold: two pieces cut half");
        expect(ColdRules.frostGain(c, new ColdFacts(0, 4, false)) == 5, "cold: four pieces leave only a trace (capped at 95% off)");
        expect(ColdRules.frostGain(c, new ColdFacts(0, 9, false)) == 5, "cold: more pieces than slots still capped");
        expect(ColdRules.frostGain(c, new ColdFacts(0, -3, false)) == 100, "cold: negative pieces never add frost");
        expect(ColdRules.frostGain(c, new ColdFacts(0, 0, true)) == 0, "cold: beside a fire there is no frost");
        expect(ColdRules.frostGain(c, new ColdFacts(-5, 0, false)) == 100, "cold: negative distance treated as point blank");
        RimeConfig none = new RimeConfig();
        none.frostPerCycle = 0;
        expect(ColdRules.frostGain(none, new ColdFacts(0, 0, false)) == 0, "cold: zero frost configured");

        expect(ColdRules.slowed(c, new ColdFacts(10, 0, false)), "cold: slowed in the whiteout");
        expect(!ColdRules.slowed(c, new ColdFacts(10, 0, true)), "cold: not slowed beside a fire");
        expect(!ColdRules.slowed(c, new ColdFacts(45, 0, false)), "cold: not slowed outside the aura");
        RimeConfig noSlow = new RimeConfig();
        noSlow.slownessAmplifier = -1;
        expect(!ColdRules.slowed(noSlow, new ColdFacts(10, 0, false)), "cold: slowness can be switched off");
    }

    // ------------------------------------------------------------------ army size

    private static void scale() {
        RimeConfig c = new RimeConfig(); // base 10, per-player 2, start-max 36, max-alive 40
        expect(HordeScale.openingWights(c, 0) == 10, "scale: no players -> base");
        expect(HordeScale.openingWights(c, 5) == 20, "scale: five players");
        expect(HordeScale.openingWights(c, 100) == 36, "scale: capped at start-max");
        expect(HordeScale.openingWights(c, -4) == 10, "scale: negative players treated as none");
        RimeConfig tight = new RimeConfig();
        tight.wightsStartMax = 50;
        tight.wightsMaxAlive = 30;
        expect(HordeScale.openingWights(tight, 100) == 30, "scale: never above max-alive even if start-max is larger");
        expect(HordeScale.reinforcements(c, 0) == 3, "scale: a batch of three");
        expect(HordeScale.reinforcements(c, 38) == 2, "scale: only room for two");
        expect(HordeScale.reinforcements(c, 40) == 0, "scale: full");
        expect(HordeScale.reinforcements(c, 55) == 0, "scale: over full never negative");
    }

    // ------------------------------------------------------------------ protected places

    private static void zones() {
        RimeConfig c = new RimeConfig(); // spawn radius 128 in "world"
        expect(ProtectedZones.isProtected(c, "world", 0, 0, 0, 0), "zones: spawn itself");
        expect(ProtectedZones.isProtected(c, "world", 127, 0, 0, 0), "zones: just inside the radius");
        expect(ProtectedZones.isProtected(c, "world", 128, 0, 0, 0), "zones: exactly on the radius");
        expect(!ProtectedZones.isProtected(c, "world", 129, 0, 0, 0), "zones: just outside");
        expect(ProtectedZones.isProtected(c, "world", 90, 90, 0, 0), "zones: diagonal inside (127.3)");
        expect(!ProtectedZones.isProtected(c, "world", 100, 100, 0, 0), "zones: diagonal outside (141.4)");
        expect(ProtectedZones.isProtected(c, "WORLD", 10, 10, 0, 0), "zones: world name case-insensitive");
        expect(!ProtectedZones.isProtected(c, "world_nether", 0, 0, 0, 0), "zones: other worlds unaffected by spawn radius");
        c.protectedWorlds = List.of("world_nether");
        expect(ProtectedZones.isProtected(c, "world_nether", 5000, 5000, 0, 0), "zones: a wholly protected world");
        c.spawnProtectRadius = 0;
        expect(!ProtectedZones.isProtected(c, "world", 0, 0, 0, 0), "zones: radius 0 turns spawn protection off");
        c.spawnProtectRadius = 128;
        expect(ProtectedZones.isProtected(c, "world", 500 + 90, 500 + 90, 500, 500), "zones: spawn need not be at the origin");
    }

    // ------------------------------------------------------------------ where the horde appears

    private static void spawnPlanner() {
        RimeConfig c = new RimeConfig(); // spawn-distance 80
        double[] any = SpawnPlanner.pick(c, new Random(1), (dx, dz) -> true);
        expect(any != null, "planner: finds a site when all are allowed");
        expect(near(Math.hypot(any[0], any[1]), 80.0), "planner: site is exactly spawn-distance away");

        expect(SpawnPlanner.pick(c, new Random(1), (dx, dz) -> false) == null, "planner: every site refused -> null");

        int[] calls = {0};
        double[] third = SpawnPlanner.pick(c, new Random(2), (dx, dz) -> ++calls[0] == 3);
        expect(third != null && calls[0] == 3, "planner: skips refused sites and takes the first allowed one");

        int[] seen = {0};
        SpawnPlanner.pick(c, new Random(3), (dx, dz) -> {
            seen[0]++;
            return false;
        });
        expect(seen[0] == 16, "planner: tries a bounded 16 sites");

        double[] a = SpawnPlanner.pick(c, new Random(7), (dx, dz) -> true);
        double[] b = SpawnPlanner.pick(c, new Random(7), (dx, dz) -> true);
        expect(a[0] == b[0] && a[1] == b[1], "planner: same seed, same site");

        // Only the east side is allowed: the planner still finds it by walking round the ring.
        double[] east = SpawnPlanner.pick(c, new Random(9), (dx, dz) -> dx > 60);
        expect(east != null && east[0] > 60, "planner: finds the one allowed side of the ring");
    }

    // ------------------------------------------------------------------ config parsing

    private static void config() {
        RimeConfig d = new RimeConfig();
        d.load(null);
        expect(d.walkerName.equals("Rimewalker"), "config: null section keeps defaults");

        Kit.MapSection s = new Kit.MapSection();
        s.put("names.walker", "Ice King");
        s.put("names.wight", "Frozen");
        s.put("events.chance-percent", 500);
        s.put("events.cooldown-minutes", -10);
        s.put("events.max-concurrent", 99);
        s.put("events.spawn-distance", 2);
        s.put("walker.health", -5);
        s.put("walker.scale", 99);
        s.put("walker.nova.cooldown-seconds", 0);
        s.put("wights.start-max", 90);
        s.put("wights.max-alive", 20);
        s.put("damage.walker-ordinary-multiplier", 5);
        s.put("damage.fire-max-per-hit", 0);
        s.put("cold.leather-protection-per-piece", 9);
        s.put("cold.slowness-amplifier", 99);
        s.put("weapon.material", "netherite_sword");
        s.put("spear.potency", 9);
        s.put("dagger.recipe.amount", 500);
        s.put("events.worlds", List.of("a", "b"));
        s.put("rewards.drops", List.of("DIAMOND:99", "BLUE_ICE", "nonsense:x", ""));
        s.put("cold.warmth-blocks", List.of("campfire"));
        s.put("zones.realms-check", " Territory ");
        RimeConfig c = new RimeConfig();
        c.load(s);
        expect(c.walkerName.equals("Ice King") && c.wightName.equals("Frozen"), "config: names load");
        expect(near(c.chancePercent, 100.0), "config: chance clamped to 100");
        expect(c.cooldownMinutes == 0, "config: negative cooldown clamped to 0");
        expect(c.maxConcurrent == 5, "config: max-concurrent clamped to 5");
        expect(c.spawnDistance == 24, "config: spawn-distance has a floor");
        expect(near(c.walkerHealth, 20.0), "config: walker health has a floor");
        expect(near(c.walkerScale, 4.0), "config: scale has a ceiling");
        expect(c.novaCooldownSeconds == 3, "config: nova cooldown has a floor");
        expect(c.wightsMaxAlive == 20 && c.wightsStartMax == 20, "config: start-max can never exceed max-alive");
        expect(near(c.walkerOrdinaryMultiplier, 1.0), "config: ordinary multiplier clamped to 1");
        expect(near(c.fireMaxPerHit, 0.5), "config: fire cap has a floor");
        expect(near(c.leatherProtectionPerPiece, 0.5), "config: leather protection capped");
        expect(c.slownessAmplifier == 4, "config: slowness amplifier capped");
        expect(c.materialOf("blade").equals("NETHERITE_SWORD"), "config: material upper-cased");
        expect(near(c.potency("spear"), 3.0) && c.gear.get("dagger").recipe.amount == 64, "config: potency and recipe amount are clamped");
        expect(c.worlds.equals(List.of("a", "b")), "config: world list");
        expect(c.rewardDrops.size() == 2 && c.rewardDrops.get(0).amount() == 64 && c.rewardDrops.get(1).amount() == 1,
                "config: drops parse, amount clamped, junk skipped");
        expect(c.warmthBlocks.equals(List.of("CAMPFIRE")), "config: warmth blocks upper-cased");
        expect(c.realmsCheck.equals("territory"), "config: realms-check is trimmed and lower-cased");
        Kit.MapSection bad = new Kit.MapSection();
        bad.put("zones.realms-check", "nonsense");
        RimeConfig bc = new RimeConfig();
        bc.load(bad);
        expect(bc.realmsCheck.equals("both"), "config: an unknown realms-check falls back to both");
        expect(new RimeConfig().realmsCheck.equals("both"), "config: realms-check defaults to both");

        Kit.MapSection empty = new Kit.MapSection();
        empty.put("events.worlds", List.of());
        empty.put("rewards.drops", List.of());
        RimeConfig e = new RimeConfig();
        e.load(empty);
        expect(e.worlds.equals(List.of("world")), "config: an empty world list falls back to the default");
        expect(e.rewardDrops.size() == 3, "config: an empty drop list falls back to the default");

        expect(RimeConfig.parseDrop("DIAMOND:3").equals(new RimeConfig.Drop("DIAMOND", 3)), "drop: with amount");
        expect(RimeConfig.parseDrop(" diamond ").equals(new RimeConfig.Drop("DIAMOND", 1)), "drop: bare name, trimmed, upper-cased");
        expect(RimeConfig.parseDrop("DIAMOND:0").amount() == 1, "drop: zero becomes one");
        expect(RimeConfig.parseDrop("DIAMOND:x") == null, "drop: bad amount rejected");
        expect(RimeConfig.parseDrop(null) == null && RimeConfig.parseDrop("  ") == null && RimeConfig.parseDrop(":3") == null, "drop: empty rejected");

        RimeConfig n = new RimeConfig();
        String msg = n.fill("&b{walker} / {wight} / {weapon} / {shard} / {event} / {target} / {world}", "Alex", "world");
        expect(msg.equals("§bRimewalker / Rimebound / Dragonglass Blade / Dragonglass Shard / the Long Winter / Alex / world"), "fill: every placeholder, colour codes translated");
        expect(n.fill("{target}", null, null).equals("someone"), "fill: missing target reads 'someone'");
        expect(n.fill(null, "A", "w").isEmpty(), "fill: null template is empty");
        expect(RimeConfig.titleCase("BLUE_ICE").equals("Blue Ice"), "titleCase");
        expect(RimeConfig.clampD(Double.NaN, 1.0, 5.0) == 1.0, "clampD: NaN falls to the floor");
    }
}
