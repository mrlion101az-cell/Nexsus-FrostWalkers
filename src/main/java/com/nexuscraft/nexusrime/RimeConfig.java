package com.nexuscraft.nexusrime;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses config.yml. Every value is clamped on load so a typo can never produce a zero-length timer,
 * a negative health pool or an unbounded horde. Anything missing falls back to a built-in default.
 */
final class RimeConfig {

    /** One reward item: a material name and an amount. */
    record Drop(String material, int amount) {
    }

    // -- names (everything the players read comes from here, so the whole theme can be renamed) --
    String walkerName = "Rimewalker";
    String wightName = "Rimebound";
    String weaponName = "Dragonglass Blade";
    String shardName = "Dragonglass Shard";
    String daggerName = "Dragonglass Dagger";
    String spearName = "Dragonglass Spear";
    String arrowName = "Dragonglass Arrow";
    String eventName = "the Long Winter";
    String knightName = "Frost Knight";

    // -- the events --
    boolean enabled = true;
    int checkIntervalMinutes = 10;
    double chancePercent = 8.0;
    int cooldownMinutes = 120;
    int maxConcurrent = 1;
    int minPlayers = 1;
    int maxDurationMinutes = 25;
    List<String> worlds = new ArrayList<>(List.of("world"));
    boolean seasonRequired = true;
    boolean allowWhenSeasonsMissing = false;
    int spawnDistance = 80;
    int lostTargetSeconds = 45;
    boolean announce = true;

    // -- the walker --
    double walkerHealth = 180.0;
    double walkerScale = 1.35;
    double walkerSpeed = 0.23;
    double walkerKnockbackResistance = 0.8;
    boolean bossBar = true;
    double bossBarRange = 120.0;
    boolean novaEnabled = true;
    double novaRadius = 7.0;
    int novaCooldownSeconds = 18;
    double novaDamage = 4.0;
    boolean raiseFallen = true;

    // -- obsidian: breaking it can shake loose a dragonglass shard --
    boolean obsidianDrop = true;
    double obsidianChance = 10.0;
    int obsidianMin = 1;
    int obsidianMax = 2;

    // -- the Frost Knight: a rare commander who leads some hordes and thinks --
    double knightChance = 25.0;
    double knightHealth = 320.0;
    double knightScale = 1.5;
    double knightSpeed = 0.24;
    double knightKnockbackResistance = 0.9;
    int knightNovaCooldownSeconds = 14;
    int knightReinforceSeconds = 12;
    int knightReinforceCount = 4;
    int knightExtraWights = 20;
    int knightRewardXp = 1000;
    List<Drop> knightRewardDrops = new ArrayList<>(List.of(new Drop("BLUE_ICE", 16), new Drop("DIAMOND", 6),
            new Drop("ECHO_SHARD", 2), new Drop("DRAGONGLASS", 6)));
    /** How often (seconds) the Knight re-plans his route, and how much of the world he will search. */
    int knightPlanSeconds = 5;
    int knightPlanNodes = 5000;
    int knightPlanRadius = 56;
    /** Within this many blocks of their target the Rimebound stop following the plan and attack. */
    double knightEngageDistance = 14.0;
    int knightMaxStuckSeconds = 10;
    // sieges: doors, trapdoors and fence gates can be smashed open by a crowd, and stay broken
    boolean breachEnabled = true;
    double breachCost = 10.0;
    double doorStrength = 60.0;
    double ironDoorFactor = 2.5;
    double breachPerWight = 1.0;
    double breachKnightPower = 3.0;
    int breachCrew = 8;
    int breachMaxContributors = 12;
    int breachTimeoutSeconds = 90;
    // living ladders: the dead pile on each other to get over a wall or cliff
    boolean stackEnabled = true;
    int stackMaxHeight = 8;
    int stackBuildSeconds = 5;
    boolean stackGlow = true;
    int stackGatherSeconds = 20;
    int stackPerSecond = 5;
    // frost bridges: temporary ice over water and lava
    boolean bridgeEnabled = true;
    String bridgeMaterial = "PACKED_ICE";
    int bridgeBlocksPerSecond = 3;
    int bridgeMaxBlocks = 32;
    int bridgeLifetimeSeconds = 90;
    double bridgeReach = 10.0;

    // -- the wights --
    int wightsBase = 10;
    int wightsPerPlayer = 2;
    int wightsStartMax = 36;
    int wightsMaxAlive = 40;
    double wightHealth = 14.0;
    double wightSpeed = 0.25;
    int reinforceSeconds = 20;
    int reinforceCount = 3;

    // -- damage rules --
    double walkerOrdinaryMultiplier = 0.05;
    double walkerFireMultiplier = 3.0;
    double fireMaxPerHit = 4.0;
    double forgedWalkerMultiplier = 2.0;
    double forgedWightMultiplier = 6.0;
    double wightFireMultiplier = 2.5;

    // -- dragonglass: the shard and the gear forged from it --
    /** One craftable item: what it looks like, what it makes it from, and how hard it hits. */
    static final class Gear {
        final String id;
        String material;
        boolean recipeEnabled = true;
        double potency;
        RecipeSpec recipe;

        Gear(String id, String material, double potency, RecipeSpec recipe) {
            this.id = id;
            this.material = material;
            this.potency = potency;
            this.recipe = recipe;
        }
    }

    static final List<String> GEAR_IDS = List.of("shard", "blade", "dagger", "spear", "arrow");

    final Map<String, Gear> gear = new LinkedHashMap<>();

    RimeConfig() {
        resetGear();
    }

    private void resetGear() {
        gear.clear();
        gear.put("shard", new Gear("shard", "FLINT", 1.0, RecipeSpec.shapeless(List.of("OBSIDIAN"), 2)));
        gear.put("blade", new Gear("blade", "DIAMOND_SWORD", 1.0, RecipeSpec.shaped(List.of(" S ", "SDS", " T "),
                Map.of('S', RecipeSpec.SHARD, 'D', "DIAMOND", 'T', "STICK"), 1)));
        gear.put("dagger", new Gear("dagger", "IRON_SWORD", 0.7, RecipeSpec.shaped(List.of("S", "S", "T"),
                Map.of('S', RecipeSpec.SHARD, 'T', "STICK"), 1)));
        gear.put("spear", new Gear("spear", "TRIDENT", 0.75, RecipeSpec.shaped(List.of(" SS", " TS", "T  "),
                Map.of('S', RecipeSpec.SHARD, 'T', "STICK"), 1)));
        gear.put("arrow", new Gear("arrow", "ARROW", 0.6, RecipeSpec.shaped(List.of("STF"),
                Map.of('S', RecipeSpec.SHARD, 'T', "STICK", 'F', "FEATHER"), 4)));
    }

    /** The config section each piece of gear lives in. */
    static String sectionOf(String id) {
        return switch (id) {
            case "shard" -> "dragonglass";
            case "blade" -> "weapon";
            default -> id;
        };
    }

    /** The name players see for a piece of gear. */
    String nameOf(String id) {
        return switch (id) {
            case "shard" -> shardName;
            case "blade" -> weaponName;
            case "dagger" -> daggerName;
            case "spear" -> spearName;
            case "arrow" -> arrowName;
            default -> id;
        };
    }

    /** How hard a piece of forged gear hits the walker, relative to the blade. */
    double potency(String id) {
        Gear g = gear.get(id);
        return g == null ? 1.0 : g.potency;
    }

    private void loadGear(ConfigurationSection c, Gear g, String base) {
        g.material = c.getString(base + ".material", g.material).toUpperCase(Locale.ROOT);
        g.recipeEnabled = c.getBoolean(base + ".recipe-enabled", g.recipeEnabled);
        g.potency = clampD(c.getDouble(base + ".potency", g.potency), 0.1, 3.0);
        int amount = clamp(c.getInt(base + ".recipe.amount", g.recipe.amount), 1, 64);
        RecipeSpec candidate = null;
        if (g.recipe.isShaped()) {
            List<String> shape = c.getStringList(base + ".recipe.shape");
            ConfigurationSection ing = c.getConfigurationSection(base + ".recipe.ingredients");
            if (!shape.isEmpty() && ing != null && !ing.getKeys(false).isEmpty()) {
                Map<Character, String> map = new LinkedHashMap<>();
                for (String k : ing.getKeys(false)) {
                    if (k.length() == 1) {
                        map.put(k.charAt(0), ing.getString(k, "AIR").toUpperCase(Locale.ROOT));
                    }
                }
                candidate = RecipeSpec.shaped(shape, map, amount);
            }
        } else {
            List<String> items = new ArrayList<>();
            for (String raw : c.getStringList(base + ".recipe.ingredients")) {
                items.add(raw.trim().toUpperCase(Locale.ROOT));
            }
            if (!items.isEmpty()) {
                candidate = RecipeSpec.shapeless(items, amount);
            }
        }
        g.recipe = candidate != null && candidate.valid() ? candidate : g.recipe.withAmount(amount);
    }

    /** The material a piece of gear is made of, falling back to its built-in default. */
    String materialOf(String id) {
        Gear g = gear.get(id);
        return g == null ? "AIR" : g.material;
    }

    // -- the cold --
    double coldRadius = 40.0;
    int coldIntervalSeconds = 1;
    int frostPerCycle = 100;
    int slownessAmplifier = 0;
    double leatherProtectionPerPiece = 0.25;
    int warmthRadius = 3;
    List<String> warmthBlocks = new ArrayList<>(List.of("CAMPFIRE", "SOUL_CAMPFIRE", "FIRE", "SOUL_FIRE", "LAVA", "FURNACE", "BLAST_FURNACE", "SMOKER", "MAGMA_BLOCK", "TORCH"));
    boolean particles = true;
    int wightHitFrostTicks = 100;
    boolean forceStorm = true;
    boolean clearStormOnEnd = true;

    // -- safe places --
    List<String> protectedWorlds = new ArrayList<>();
    List<String> spawnProtectWorlds = new ArrayList<>(List.of("world"));
    int spawnProtectRadius = 128;
    boolean useRealms = true;
    /** Which NexusRealms question counts as "claimed": territory, build, or both. */
    String realmsCheck = "both";

    // -- rewards --
    int rewardXp = 500;
    List<Drop> rewardDrops = new ArrayList<>(List.of(new Drop("BLUE_ICE", 8), new Drop("DIAMOND", 3), new Drop("ECHO_SHARD", 1)));

    // -- messages --
    String msgWarn = "&b&lA bitter wind rises. {walker} leads its army toward {target}!";
    String msgWarnTarget = "&3The {walker} is coming for you. Light fires, wear leather, and keep a {weapon} ready.";
    String msgVictory = "&a{walker} has fallen, and its {wight} crumble to frost.";
    String msgFaded = "&7The cold lifts. {walker} and its army fade into the snow.";
    String msgDissolved = "&7The cold cannot pass the wards. {walker} and its army crumble at the edge of safe ground.";
    String msgSeasonEnded = "&7Winter is over. {walker} and its army melt away.";
    String msgLostTarget = "&7{walker} has lost the scent and withers away.";
    String msgFrozen = "&bThe cold bites through you.";
    String msgKnightWarn = "&b&lA bitter wind rises. The {knight} leads an army toward {target}, and it thinks.";
    String msgKnightBreach = "&3The {knight} raises a hand. The {wight} throw themselves at a door!";
    String msgKnightStack = "&3The {knight} raises a hand. The {wight} pile upon each other to climb!";
    String msgKnightBridge = "&3The {knight} raises a hand. Ice spreads over the {liquid}!";

    // -- heartbeat --
    boolean heartbeat = true;

    void load(ConfigurationSection c) {
        if (c == null) {
            return;
        }
        resetGear();
        walkerName = c.getString("names.walker", walkerName);
        wightName = c.getString("names.wight", wightName);
        weaponName = c.getString("names.weapon", weaponName);
        shardName = c.getString("names.shard", shardName);
        daggerName = c.getString("names.dagger", daggerName);
        spearName = c.getString("names.spear", spearName);
        arrowName = c.getString("names.arrow", arrowName);
        eventName = c.getString("names.event", eventName);
        knightName = c.getString("names.knight", knightName);

        enabled = c.getBoolean("events.enabled", enabled);
        checkIntervalMinutes = clamp(c.getInt("events.check-interval-minutes", checkIntervalMinutes), 1, 1440);
        chancePercent = clampD(c.getDouble("events.chance-percent", chancePercent), 0.0, 100.0);
        cooldownMinutes = clamp(c.getInt("events.cooldown-minutes", cooldownMinutes), 0, 10080);
        maxConcurrent = clamp(c.getInt("events.max-concurrent", maxConcurrent), 1, 5);
        minPlayers = clamp(c.getInt("events.min-players", minPlayers), 1, 100);
        maxDurationMinutes = clamp(c.getInt("events.max-duration-minutes", maxDurationMinutes), 1, 240);
        List<String> w = c.getStringList("events.worlds");
        if (!w.isEmpty()) {
            worlds = new ArrayList<>(w);
        }
        seasonRequired = c.getBoolean("events.season.winter-only", seasonRequired);
        allowWhenSeasonsMissing = c.getBoolean("events.season.allow-when-seasons-missing", allowWhenSeasonsMissing);
        spawnDistance = clamp(c.getInt("events.spawn-distance", spawnDistance), 24, 200);
        lostTargetSeconds = clamp(c.getInt("events.lost-target-seconds", lostTargetSeconds), 5, 600);
        announce = c.getBoolean("events.announce", announce);

        walkerHealth = clampD(c.getDouble("walker.health", walkerHealth), 20.0, 2000.0);
        walkerScale = clampD(c.getDouble("walker.scale", walkerScale), 0.5, 4.0);
        walkerSpeed = clampD(c.getDouble("walker.speed", walkerSpeed), 0.1, 0.6);
        walkerKnockbackResistance = clampD(c.getDouble("walker.knockback-resistance", walkerKnockbackResistance), 0.0, 1.0);
        bossBar = c.getBoolean("walker.boss-bar", bossBar);
        bossBarRange = clampD(c.getDouble("walker.boss-bar-range", bossBarRange), 16.0, 512.0);
        novaEnabled = c.getBoolean("walker.nova.enabled", novaEnabled);
        novaRadius = clampD(c.getDouble("walker.nova.radius", novaRadius), 2.0, 24.0);
        novaCooldownSeconds = clamp(c.getInt("walker.nova.cooldown-seconds", novaCooldownSeconds), 3, 300);
        novaDamage = clampD(c.getDouble("walker.nova.damage", novaDamage), 0.0, 20.0);
        raiseFallen = c.getBoolean("walker.raise-fallen", raiseFallen);

        obsidianDrop = c.getBoolean("obsidian-drop.enabled", obsidianDrop);
        obsidianChance = clampD(c.getDouble("obsidian-drop.chance-percent", obsidianChance), 0.0, 100.0);
        obsidianMin = clamp(c.getInt("obsidian-drop.min", obsidianMin), 1, 16);
        obsidianMax = clamp(c.getInt("obsidian-drop.max", obsidianMax), obsidianMin, 16);

        knightChance = clampD(c.getDouble("knight.chance-percent", knightChance), 0.0, 100.0);
        knightHealth = clampD(c.getDouble("knight.health", knightHealth), 20.0, 2000.0);
        knightScale = clampD(c.getDouble("knight.scale", knightScale), 0.5, 4.0);
        knightSpeed = clampD(c.getDouble("knight.speed", knightSpeed), 0.1, 0.6);
        knightKnockbackResistance = clampD(c.getDouble("knight.knockback-resistance", knightKnockbackResistance), 0.0, 1.0);
        knightNovaCooldownSeconds = clamp(c.getInt("knight.nova-cooldown-seconds", knightNovaCooldownSeconds), 3, 300);
        knightReinforceSeconds = clamp(c.getInt("knight.reinforce-seconds", knightReinforceSeconds), 5, 600);
        knightReinforceCount = clamp(c.getInt("knight.reinforce-count", knightReinforceCount), 0, 30);
        knightExtraWights = clamp(c.getInt("knight.extra-wights", knightExtraWights), 0, 200);
        knightRewardXp = clamp(c.getInt("knight.rewards.xp", knightRewardXp), 0, 100000);
        List<String> kd = c.getStringList("knight.rewards.drops");
        if (!kd.isEmpty()) {
            List<Drop> parsed = new ArrayList<>();
            for (String d : kd) {
                Drop drop = parseDrop(d);
                if (drop != null) {
                    parsed.add(drop);
                }
            }
            knightRewardDrops = parsed;
        }
        knightPlanSeconds = clamp(c.getInt("knight.tactics.plan-seconds", knightPlanSeconds), 1, 60);
        knightPlanNodes = clamp(c.getInt("knight.tactics.plan-nodes", knightPlanNodes), 500, 40000);
        knightPlanRadius = clamp(c.getInt("knight.tactics.plan-radius", knightPlanRadius), 16, 128);
        knightEngageDistance = clampD(c.getDouble("knight.tactics.engage-distance", knightEngageDistance), 4.0, 48.0);
        knightMaxStuckSeconds = clamp(c.getInt("knight.tactics.max-stuck-seconds", knightMaxStuckSeconds), 3, 120);
        breachEnabled = c.getBoolean("knight.breach.enabled", breachEnabled);
        breachCost = clampD(c.getDouble("knight.breach.plan-cost", breachCost), 1.0, 200.0);
        doorStrength = clampD(c.getDouble("knight.breach.door-strength", doorStrength), 1.0, 5000.0);
        ironDoorFactor = clampD(c.getDouble("knight.breach.iron-factor", ironDoorFactor), 1.0, 20.0);
        breachPerWight = clampD(c.getDouble("knight.breach.damage-per-wight", breachPerWight), 0.1, 50.0);
        breachKnightPower = clampD(c.getDouble("knight.breach.knight-power", breachKnightPower), 0.0, 100.0);
        breachCrew = clamp(c.getInt("knight.breach.crew", breachCrew), 1, 40);
        breachMaxContributors = clamp(c.getInt("knight.breach.max-contributors", breachMaxContributors), 1, 60);
        breachTimeoutSeconds = clamp(c.getInt("knight.breach.timeout-seconds", breachTimeoutSeconds), 10, 600);
        stackEnabled = c.getBoolean("knight.stack.enabled", stackEnabled);
        stackMaxHeight = clamp(c.getInt("knight.stack.max-height", stackMaxHeight), 2, 16);
        stackBuildSeconds = clamp(c.getInt("knight.stack.build-seconds", stackBuildSeconds), 1, 30);
        stackGlow = c.getBoolean("knight.stack.glow", stackGlow);
        stackGatherSeconds = clamp(c.getInt("knight.stack.gather-seconds", stackGatherSeconds), 3, 120);
        stackPerSecond = clamp(c.getInt("knight.stack.climbers-per-second", stackPerSecond), 1, 30);
        bridgeEnabled = c.getBoolean("knight.bridge.enabled", bridgeEnabled);
        bridgeMaterial = c.getString("knight.bridge.material", bridgeMaterial).toUpperCase(Locale.ROOT);
        bridgeBlocksPerSecond = clamp(c.getInt("knight.bridge.blocks-per-second", bridgeBlocksPerSecond), 1, 16);
        bridgeMaxBlocks = clamp(c.getInt("knight.bridge.max-blocks", bridgeMaxBlocks), 1, 128);
        bridgeLifetimeSeconds = clamp(c.getInt("knight.bridge.lifetime-seconds", bridgeLifetimeSeconds), 10, 3600);
        bridgeReach = clampD(c.getDouble("knight.bridge.reach", bridgeReach), 3.0, 32.0);

        wightsBase = clamp(c.getInt("wights.base", wightsBase), 0, 100);
        wightsPerPlayer = clamp(c.getInt("wights.per-player", wightsPerPlayer), 0, 20);
        wightsStartMax = clamp(c.getInt("wights.start-max", wightsStartMax), 1, 200);
        wightsMaxAlive = clamp(c.getInt("wights.max-alive", wightsMaxAlive), 1, 300);
        if (wightsStartMax > wightsMaxAlive) {
            wightsStartMax = wightsMaxAlive;
        }
        wightHealth = clampD(c.getDouble("wights.health", wightHealth), 1.0, 200.0);
        wightSpeed = clampD(c.getDouble("wights.speed", wightSpeed), 0.1, 0.6);
        reinforceSeconds = clamp(c.getInt("wights.reinforce-seconds", reinforceSeconds), 5, 600);
        reinforceCount = clamp(c.getInt("wights.reinforce-count", reinforceCount), 0, 30);

        walkerOrdinaryMultiplier = clampD(c.getDouble("damage.walker-ordinary-multiplier", walkerOrdinaryMultiplier), 0.0, 1.0);
        walkerFireMultiplier = clampD(c.getDouble("damage.walker-fire-multiplier", walkerFireMultiplier), 0.0, 20.0);
        fireMaxPerHit = clampD(c.getDouble("damage.fire-max-per-hit", fireMaxPerHit), 0.5, 100.0);
        forgedWalkerMultiplier = clampD(c.getDouble("damage.forged-walker-multiplier", forgedWalkerMultiplier), 0.0, 20.0);
        forgedWightMultiplier = clampD(c.getDouble("damage.forged-wight-multiplier", forgedWightMultiplier), 0.0, 50.0);
        wightFireMultiplier = clampD(c.getDouble("damage.wight-fire-multiplier", wightFireMultiplier), 0.0, 20.0);

        loadGear(c, gear.get("shard"), "dragonglass");
        loadGear(c, gear.get("blade"), "weapon");
        loadGear(c, gear.get("dagger"), "dagger");
        loadGear(c, gear.get("spear"), "spear");
        loadGear(c, gear.get("arrow"), "arrow");

        coldRadius = clampD(c.getDouble("cold.radius", coldRadius), 8.0, 128.0);
        coldIntervalSeconds = clamp(c.getInt("cold.interval-seconds", coldIntervalSeconds), 1, 30);
        frostPerCycle = clamp(c.getInt("cold.frost-per-cycle", frostPerCycle), 0, 400);
        slownessAmplifier = clamp(c.getInt("cold.slowness-amplifier", slownessAmplifier), -1, 4);
        leatherProtectionPerPiece = clampD(c.getDouble("cold.leather-protection-per-piece", leatherProtectionPerPiece), 0.0, 0.5);
        warmthRadius = clamp(c.getInt("cold.warmth-radius", warmthRadius), 0, 8);
        List<String> wb = c.getStringList("cold.warmth-blocks");
        if (!wb.isEmpty()) {
            warmthBlocks = new ArrayList<>();
            for (String b : wb) {
                warmthBlocks.add(b.toUpperCase(Locale.ROOT));
            }
        }
        particles = c.getBoolean("cold.particles", particles);
        wightHitFrostTicks = clamp(c.getInt("cold.wight-hit-frost-ticks", wightHitFrostTicks), 0, 600);
        forceStorm = c.getBoolean("blizzard.force-storm", forceStorm);
        clearStormOnEnd = c.getBoolean("blizzard.clear-on-end", clearStormOnEnd);

        protectedWorlds = new ArrayList<>(c.getStringList("zones.protected-worlds"));
        List<String> sw = c.getStringList("zones.spawn-protection.worlds");
        if (!sw.isEmpty()) {
            spawnProtectWorlds = new ArrayList<>(sw);
        }
        spawnProtectRadius = clamp(c.getInt("zones.spawn-protection.radius", spawnProtectRadius), 0, 5000);
        useRealms = c.getBoolean("zones.use-realms", useRealms);
        String check = c.getString("zones.realms-check", realmsCheck).toLowerCase(Locale.ROOT).trim();
        realmsCheck = check.equals("territory") || check.equals("build") ? check : "both";

        rewardXp = clamp(c.getInt("rewards.xp", rewardXp), 0, 100000);
        List<String> drops = c.getStringList("rewards.drops");
        if (!drops.isEmpty()) {
            List<Drop> parsed = new ArrayList<>();
            for (String d : drops) {
                Drop drop = parseDrop(d);
                if (drop != null) {
                    parsed.add(drop);
                }
            }
            rewardDrops = parsed;
        }

        msgWarn = c.getString("messages.warn", msgWarn);
        msgWarnTarget = c.getString("messages.warn-target", msgWarnTarget);
        msgVictory = c.getString("messages.victory", msgVictory);
        msgFaded = c.getString("messages.faded", msgFaded);
        msgDissolved = c.getString("messages.dissolved", msgDissolved);
        msgSeasonEnded = c.getString("messages.season-ended", msgSeasonEnded);
        msgLostTarget = c.getString("messages.lost-target", msgLostTarget);
        msgFrozen = c.getString("messages.frozen", msgFrozen);
        msgKnightWarn = c.getString("messages.knight-warn", msgKnightWarn);
        msgKnightBreach = c.getString("messages.knight-breach", msgKnightBreach);
        msgKnightStack = c.getString("messages.knight-stack", msgKnightStack);
        msgKnightBridge = c.getString("messages.knight-bridge", msgKnightBridge);

        heartbeat = c.getBoolean("heartbeat.enabled", heartbeat);
    }

    /** "DIAMOND:3" or just "DIAMOND" (one). Returns null for anything unreadable. */
    static Drop parseDrop(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(":");
        String mat = parts[0].trim().toUpperCase(Locale.ROOT);
        if (mat.isEmpty()) {
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException bad) {
                return null;
            }
        }
        return new Drop(mat, clamp(amount, 1, 64));
    }

    /** Fills the {walker}/{wight}/{event}/{target}/{world} placeholders in a message. */
    String fill(String template, String target, String world) {
        return fill(template, target, world, walkerName);
    }

    /** Same, with {walker} standing for whoever leads this horde (the Rimewalker or the Knight). */
    String fill(String template, String target, String world, String leader) {
        if (template == null) {
            return "";
        }
        return ColorText.translate(template
                .replace("{walker}", leader)
                .replace("{knight}", knightName)
                .replace("{wight}", wightName)
                .replace("{weapon}", weaponName)
                .replace("{shard}", shardName)
                .replace("{event}", eventName)
                .replace("{target}", target == null ? "someone" : target)
                .replace("{world}", world == null ? "" : world));
    }

    /** "BLUE_ICE" -> "Blue Ice". */
    static String titleCase(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double clampD(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
