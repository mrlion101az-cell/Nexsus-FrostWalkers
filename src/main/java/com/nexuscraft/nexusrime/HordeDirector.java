package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runs the winter hordes. Once a second it walks every active horde: the walker marches on its
 * target, the wights follow or fight, the cold closes in on nearby players, the walker may unleash
 * a frost nova, new wights are raised, and the horde ends when the walker falls, time runs out, the
 * season turns, the walker reaches protected ground, or there is no one left to hunt.
 *
 * <p>All the rules (may a horde begin, who can be hunted, how much damage lands, how much frost) live
 * in the small pure classes next to this one; this class only applies their answers to the world.
 */
final class HordeDirector implements Runnable {

    /** The outcome of asking for a horde: the gate's verdict, and the horde if one began. */
    record StartResult(GateVerdict verdict, Horde horde) {
    }

    private static final String WALKER = "walker";
    private static final String WIGHT = "wight";
    private static final long RECENT_HIT_MILLIS = 8_000L;
    private static final int SECOND = 20;

    private final JavaPlugin plugin;
    private final RimeConfig cfg;
    private final Keys keys;
    private final RimeItems items;
    private final ZoneGuard zones;
    private final SeasonBridge seasons;
    private final LongSupplier clock;
    private final Random random;
    private final Runnable stateChanged;
    private final BridgeLedger ledger;
    private final FrostCommander commander;

    private final List<Horde> active = new ArrayList<>();
    private final Map<UUID, long[]> recentHordeHits = new HashMap<>();
    private final Map<UUID, UUID> recentHitHorde = new HashMap<>();
    private long lastEventMillis;
    /** Worlds where this plugin turned the storm on (so it is the one to turn it off again). */
    private final Set<String> stormRaised = new HashSet<>();

    HordeDirector(JavaPlugin plugin, RimeConfig cfg, Keys keys, RimeItems items, ZoneGuard zones, SeasonBridge seasons,
                  LongSupplier clock, Random random, Runnable stateChanged) {
        this(plugin, cfg, keys, items, zones, seasons, clock, random, stateChanged, new BridgeLedger(null, w -> { }, clock));
    }

    HordeDirector(JavaPlugin plugin, RimeConfig cfg, Keys keys, RimeItems items, ZoneGuard zones, SeasonBridge seasons,
                  LongSupplier clock, Random random, Runnable stateChanged, BridgeLedger ledger) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.keys = keys;
        this.items = items;
        this.zones = zones;
        this.seasons = seasons;
        this.clock = clock;
        this.random = random;
        this.stateChanged = stateChanged;
        this.ledger = ledger;
        this.commander = new FrostCommander(cfg, zones, ledger, random);
    }

    BridgeLedger ledger() {
        return ledger;
    }

    /** The name of whoever leads this horde. */
    String leaderName(Horde h) {
        return h.knight ? cfg.knightName : cfg.walkerName;
    }

    // ------------------------------------------------------------------ queries

    List<Horde> hordes() {
        return new ArrayList<>(active);
    }

    long lastEventMillis() {
        return lastEventMillis;
    }

    void setLastEventMillis(long millis) {
        this.lastEventMillis = millis;
    }

    /** The horde a creature belongs to, or null if it is not (or no longer) one of an active horde. */
    Horde hordeOf(Entity entity) {
        UUID id = hordeId(entity);
        if (id == null) {
            return null;
        }
        for (Horde h : active) {
            if (h.id.equals(id)) {
                return h;
            }
        }
        return null;
    }

    /** "walker", "wight", or null when the creature is not tagged as one of ours at all. */
    String roleOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(keys.mobRole, PersistentDataType.STRING);
    }

    private UUID hordeId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(keys.mobHorde, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    boolean isHordeCreature(Entity entity) {
        return roleOf(entity) != null;
    }

    // ------------------------------------------------------------------ choosing a target

    List<Player> eligibleTargets(World world) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (TargetRules.eligible(factsFor(p, world))) {
                out.add(p);
            }
        }
        return out;
    }

    private TargetFacts factsFor(Player p, World world) {
        Location at = p.getLocation();
        boolean inWorld = at != null && at.getWorld() != null && at.getWorld().getName().equals(world.getName());
        GameMode mode = p.getGameMode();
        boolean playable = mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
        return new TargetFacts(p.isOnline(), !p.isDead(), inWorld, playable, inWorld && zones.isProtected(at));
    }

    // ------------------------------------------------------------------ starting a horde

    /** The natural roll, run on a timer: each allowed world gets one chance, in random order. */
    void roll() {
        List<String> names = new ArrayList<>(cfg.worlds);
        java.util.Collections.shuffle(names, random);
        for (String name : names) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                continue;
            }
            if (tryStart(world, null, false).horde() != null) {
                return;
            }
        }
    }

    /** Asks for a horde in {@code world}. {@code wanted} picks the target (an admin start); null
     *  lets the dice and the target rules decide. */
    StartResult tryStart(World world, Player wanted, boolean forced) {
        return tryStart(world, wanted, forced, null);
    }

    /** As above; {@code knightWanted} forces a Frost Knight (true) or a plain walker (false), and
     *  null lets the dice decide. */
    StartResult tryStart(World world, Player wanted, boolean forced, Boolean knightWanted) {
        List<Player> eligible = eligibleTargets(world);
        Player target = null;
        if (wanted != null) {
            if (eligible.contains(wanted)) {
                target = wanted;
            }
        } else if (!eligible.isEmpty()) {
            target = eligible.get(random.nextInt(eligible.size()));
        }
        long now = clock.getAsLong();
        GateFacts facts = new GateFacts(forced, seasons.status(), world.getName(), active.size(), now,
                lastEventMillis, eligible.size(), target != null, random.nextDouble() * 100.0);
        GateVerdict verdict = GateRules.evaluate(cfg, facts);
        if (verdict != GateVerdict.OK) {
            return new StartResult(verdict, null);
        }

        final Player hunted = target;
        final Location[] site = new Location[1];
        double[] offset = SpawnPlanner.pick(cfg, random, (dx, dz) -> {
            site[0] = groundAt(world, hunted.getLocation().getX() + dx, hunted.getLocation().getZ() + dz);
            return site[0] != null;
        });
        if (offset == null || site[0] == null) {
            return new StartResult(GateVerdict.NO_SPAWN_SITE, null);
        }

        HordeEvent event = new HordeEvent(world, hunted);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return new StartResult(GateVerdict.VETOED, null);
        }

        Horde horde = new Horde(world.getName(), now);
        horde.forced = forced;
        horde.knight = knightWanted != null ? knightWanted : cfg.knightChance > 0 && random.nextDouble() * 100.0 < cfg.knightChance;
        Mob walker = spawnWalker(world, site[0], horde);
        if (walker == null) {
            return new StartResult(GateVerdict.NO_SPAWN_SITE, null);
        }
        horde.walker = walker.getUniqueId();
        horde.target = hunted.getUniqueId();
        horde.targetName = hunted.getName();
        active.add(horde);

        int opening = HordeScale.openingWights(cfg, world.getPlayers().size());
        for (int i = 0; i < opening; i++) {
            raiseWight(world, horde, site[0], 6.0);
        }
        horde.nextRaiseTick = (long) (horde.knight ? cfg.knightReinforceSeconds : cfg.reinforceSeconds) * SECOND;
        horde.nextNovaTick = (long) novaCooldown(horde) * SECOND;
        horde.nextColdTick = 0;

        if (cfg.bossBar) {
            horde.bar = Bukkit.createBossBar(ColorText.translate("&b&l" + leaderName(horde)), BarColor.WHITE, BarStyle.SEGMENTED_10);
        }
        if (cfg.forceStorm && !world.hasStorm()) {
            world.setStorm(true);
            world.setWeatherDuration(cfg.maxDurationMinutes * 1200);
            stormRaised.add(world.getName());
        }

        lastEventMillis = now;
        stateChanged.run();

        String leader = leaderName(horde);
        if (cfg.announce) {
            String line = cfg.fill(horde.knight ? cfg.msgKnightWarn : cfg.msgWarn, hunted.getName(), world.getName(), leader);
            for (Player p : world.getPlayers()) {
                p.sendMessage(line);
            }
        }
        hunted.sendMessage(cfg.fill(cfg.msgWarnTarget, hunted.getName(), world.getName(), leader));
        report("CONFLICT", horde.knight ? -8 : -5, leader + " marches on " + hunted.getName() + " with an army of " + cfg.wightName);
        return new StartResult(GateVerdict.OK, horde);
    }

    // ------------------------------------------------------------------ spawning

    /** A safe place to stand at (x, z): loaded chunk, real ground, not liquid, not protected. */
    Location groundAt(World world, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        if (!world.isChunkLoaded(ix >> 4, iz >> 4)) {
            return null;
        }
        int y = world.getHighestBlockYAt(ix, iz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) {
            return null;
        }
        if (world.getBlockAt(ix, y, iz).isLiquid()) {
            return null;
        }
        Location loc = new Location(world, ix + 0.5, y + 1.0, iz + 0.5);
        return zones.isProtected(loc) ? null : loc;
    }

    private Mob spawnWalker(World world, Location at, Horde horde) {
        Stray walker = world.spawn(at, Stray.class);
        if (walker == null) {
            return null;
        }
        boolean knight = horde.knight;
        double health = knight ? cfg.knightHealth : cfg.walkerHealth;
        tag(walker, horde, WALKER);
        setAttribute(walker, Attribute.MAX_HEALTH, health);
        walker.setHealth(health);
        setAttribute(walker, Attribute.SCALE, knight ? cfg.knightScale : cfg.walkerScale);
        setAttribute(walker, Attribute.MOVEMENT_SPEED, knight ? cfg.knightSpeed : cfg.walkerSpeed);
        setAttribute(walker, Attribute.KNOCKBACK_RESISTANCE, knight ? cfg.knightKnockbackResistance : cfg.walkerKnockbackResistance);
        setAttribute(walker, Attribute.FOLLOW_RANGE, 64.0);
        setAttribute(walker, Attribute.ATTACK_DAMAGE, knight ? 11.0 : 9.0);
        walker.setCustomName(ColorText.translate("&b&l" + leaderName(horde)));
        walker.setCustomNameVisible(true);
        walker.setRemoveWhenFarAway(false);
        Reflect.noSunBurn(walker);
        return walker;
    }

    /** Raises one wight near {@code around}; returns it, or null if there was no safe ground. */
    Mob raiseWight(World world, Horde horde, Location around, double spread) {
        if (horde.wights.size() >= maxAlive(horde)) {
            return null;
        }
        Location at = groundAt(world, around.getX() + (random.nextDouble() - 0.5) * 2 * spread,
                around.getZ() + (random.nextDouble() - 0.5) * 2 * spread);
        if (at == null) {
            return null;
        }
        Zombie wight = world.spawn(at, Zombie.class);
        if (wight == null) {
            return null;
        }
        wight.setBaby(false);
        tag(wight, horde, WIGHT);
        setAttribute(wight, Attribute.MAX_HEALTH, cfg.wightHealth);
        wight.setHealth(cfg.wightHealth);
        setAttribute(wight, Attribute.MOVEMENT_SPEED, cfg.wightSpeed);
        setAttribute(wight, Attribute.FOLLOW_RANGE, 48.0);
        setAttribute(wight, Attribute.SPAWN_REINFORCEMENTS, 0.0);
        wight.setCustomName(ColorText.translate("&3" + cfg.wightName));
        wight.setCustomNameVisible(false);
        wight.setRemoveWhenFarAway(false);
        Reflect.noSunBurn(wight);
        horde.wights.add(wight.getUniqueId());
        horde.lastSeen.put(wight.getUniqueId(), at);
        return wight;
    }

    private int maxAlive(Horde h) {
        return cfg.wightsMaxAlive + (h.knight ? cfg.knightExtraWights : 0);
    }

    private int novaCooldown(Horde h) {
        return h.knight ? cfg.knightNovaCooldownSeconds : cfg.novaCooldownSeconds;
    }

    private void tag(Entity e, Horde horde, String role) {
        e.getPersistentDataContainer().set(keys.mobHorde, PersistentDataType.STRING, horde.id.toString());
        e.getPersistentDataContainer().set(keys.mobRole, PersistentDataType.STRING, role);
    }

    private static void setAttribute(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    // ------------------------------------------------------------------ the loop

    @Override
    public void run() {
        for (Horde h : new ArrayList<>(active)) {
            step(h);
        }
        pruneRecentHits();
        ledger.restoreDue(this::someoneStandsOn);
        ledger.flush();
    }

    /** Is a player standing on (or in) this block? Ice is not melted out from under anyone. */
    private boolean someoneStandsOn(org.bukkit.block.Block block) {
        Location at = block.getLocation();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location pl = p.getLocation();
            if (pl != null && pl.getWorld() != null && at.getWorld() != null && pl.getWorld().getName().equals(at.getWorld().getName())
                    && Math.abs(pl.getX() - (at.getX() + 0.5)) < 1.0 && Math.abs(pl.getZ() - (at.getZ() + 0.5)) < 1.0
                    && pl.getY() >= at.getY() && pl.getY() <= at.getY() + 3.0) {
                return true;
            }
        }
        return false;
    }

    private void step(Horde h) {
        h.ticksLived += SECOND;
        World world = Bukkit.getWorld(h.worldName);
        if (world == null) {
            end(h, HordeEnd.FADED);
            return;
        }
        if (h.ticksLived > (long) cfg.maxDurationMinutes * 1200L) {
            end(h, HordeEnd.FADED);
            return;
        }
        if (cfg.seasonRequired && !h.forced && seasons.status() == SeasonStatus.NOT_WINTER) {
            end(h, HordeEnd.SEASON_ENDED);
            return;
        }

        Entity walkerEntity = Bukkit.getEntity(h.walker);
        if (!(walkerEntity instanceof Mob walker) || !walker.isValid()) {
            Location last = h.walkerLastSeen;
            if (last == null || (last.getWorld() != null && last.getWorld().isChunkLoaded(last.getBlockX() >> 4, last.getBlockZ() >> 4))) {
                // Its chunk is loaded and it is not there: it is really gone (removed, or lost
                // to the void), so there is nothing left to wait for.
                end(h, HordeEnd.FADED);
                return;
            }
            // Its chunk is unloaded. Give it a couple of minutes, then call it faded.
            h.ticksWalkerMissing += SECOND;
            if (h.ticksWalkerMissing > 120 * SECOND) {
                end(h, HordeEnd.FADED);
            }
            return;
        }
        h.ticksWalkerMissing = 0;
        h.walkerLastSeen = walker.getLocation().clone();

        if (zones.isProtected(walker.getLocation())) {
            end(h, HordeEnd.DISSOLVED);
            return;
        }

        Player target = resolveTarget(h, world, walker);
        if (target == null) {
            h.ticksWithoutTarget += SECOND;
            if (h.ticksWithoutTarget > cfg.lostTargetSeconds * SECOND) {
                end(h, HordeEnd.LOST_TARGET);
                return;
            }
        } else {
            h.ticksWithoutTarget = 0;
        }

        List<Mob> alive = census(h);
        boolean led = target != null && h.knight && commander.lead(h, world, walker, target, alive);
        if (!led) {
            commander.thaw(h);
            if (target != null) {
                march(walker, target);
            }
            driveWights(alive, walker, target);
        }

        if (h.ticksLived >= h.nextColdTick) {
            h.nextColdTick = h.ticksLived + (long) cfg.coldIntervalSeconds * SECOND;
            coldAura(world, walker);
        }
        if (cfg.novaEnabled && h.ticksLived >= h.nextNovaTick) {
            if (nova(world, walker)) {
                h.nextNovaTick = h.ticksLived + (long) novaCooldown(h) * SECOND;
            }
        }
        if (h.ticksLived >= h.nextRaiseTick) {
            int every = h.knight ? cfg.knightReinforceSeconds : cfg.reinforceSeconds;
            h.nextRaiseTick = h.ticksLived + (long) every * SECOND;
            int n = HordeScale.reinforcements(h.knight ? cfg.knightReinforceCount : cfg.reinforceCount, h.wights.size(), maxAlive(h));
            for (int i = 0; i < n; i++) {
                raiseWight(world, h, walker.getLocation(), 5.0);
            }
        }
        updateBar(h, world, walker);
        if (cfg.particles) {
            world.spawnParticle(Particle.SNOWFLAKE, walker.getLocation().clone().add(0, 1.2, 0), 30, 0.8, 1.2, 0.8);
        }
    }

    /** The current target, or the nearest eligible player if the old one is gone, or null. */
    private Player resolveTarget(Horde h, World world, Mob walker) {
        Player current = h.target == null ? null : Bukkit.getPlayer(h.target);
        if (current != null && TargetRules.eligible(factsFor(current, world))) {
            return current;
        }
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : eligibleTargets(world)) {
            double d = distance(p.getLocation(), walker.getLocation());
            if (d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        if (best != null) {
            h.target = best.getUniqueId();
            h.targetName = best.getName();
        }
        return best;
    }

    /** Walks the walker toward the target a stretch at a time, so a long route still resolves. */
    private void march(Mob walker, Player target) {
        double d = distance(walker.getLocation(), target.getLocation());
        if (d <= 40.0) {
            walker.setTarget(target);
        }
        Location from = walker.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat > 3.0) {
            double stride = Math.min(24.0, flat);
            double fx = from.getX() + dx / flat * stride;
            double fz = from.getZ() + dz / flat * stride;
            World w = from.getWorld();
            int y = w.getHighestBlockYAt((int) Math.floor(fx), (int) Math.floor(fz), HeightMap.MOTION_BLOCKING_NO_LEAVES);
            walker.getPathfinder().moveTo(new Location(w, fx, y + 1.0, fz), 1.0);
        }
    }

    /** Walks the roster: forgets the fallen, crumbles any that strayed onto safe ground, and returns
     *  the ones still standing. */
    private List<Mob> census(Horde h) {
        List<Mob> alive = new ArrayList<>();
        Set<UUID> gone = new HashSet<>();
        for (UUID id : h.wights) {
            Entity e = Bukkit.getEntity(id);
            if (e == null) {
                // Not found: either its chunk is unloaded (keep it on the roster) or it is really
                // gone -- removed, or lost to the void. Where it was last seen tells the two apart.
                Location last = h.lastSeen.get(id);
                if (last == null || (last.getWorld() != null && last.getWorld().isChunkLoaded(last.getBlockX() >> 4, last.getBlockZ() >> 4))) {
                    gone.add(id);
                }
                continue;
            }
            if (!(e instanceof Mob wight) || !wight.isValid()) {
                gone.add(id);
                continue;
            }
            h.lastSeen.put(id, wight.getLocation().clone());
            if (zones.isProtected(wight.getLocation())) {
                crumble(wight);
                gone.add(id);
                continue;
            }
            alive.add(wight);
        }
        h.wights.removeAll(gone);
        h.lastSeen.keySet().removeAll(gone);
        return alive;
    }

    /** The plain orders: fight a near target, otherwise stay close to the walker. */
    private void driveWights(List<Mob> alive, Mob walker, Player target) {
        for (Mob wight : alive) {
            double toTarget = target == null ? Double.MAX_VALUE : distance(wight.getLocation(), target.getLocation());
            if (target != null && toTarget <= 30.0) {
                wight.setTarget(target);
            } else {
                double toWalker = distance(wight.getLocation(), walker.getLocation());
                if (toWalker > 8.0) {
                    wight.getPathfinder().moveTo(walker.getLocation(), 1.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the cold

    private void coldAura(World world, Mob walker) {
        for (Player p : world.getPlayers()) {
            if (!playable(p) || zones.isProtected(p.getLocation())) {
                continue;
            }
            double d = distance(p.getLocation(), walker.getLocation());
            if (d >= cfg.coldRadius) {
                continue;
            }
            ColdFacts facts = new ColdFacts(d, leatherPieces(p), nearWarmth(p.getLocation()));
            int gain = ColdRules.frostGain(cfg, facts);
            if (gain > 0) {
                p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + gain));
            }
            if (ColdRules.slowed(cfg, facts)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, cfg.slownessAmplifier, true, false));
            }
            if (cfg.particles && !facts.nearWarmth()) {
                world.spawnParticle(Particle.SNOWFLAKE, p.getLocation().clone().add(0, 1.0, 0), 25, 2.0, 1.0, 2.0);
            }
        }
    }

    private static boolean playable(Player p) {
        GameMode m = p.getGameMode();
        return m == GameMode.SURVIVAL || m == GameMode.ADVENTURE;
    }

    private static int leatherPieces(Player p) {
        int n = 0;
        ItemStack[] armor = p.getInventory().getArmorContents();
        if (armor == null) {
            return 0;
        }
        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != null && piece.getType().name().startsWith("LEATHER_")) {
                n++;
            }
        }
        return n;
    }

    /** True when a campfire, fire, lava or similar heat source is within the warmth radius. */
    boolean nearWarmth(Location at) {
        int r = cfg.warmthRadius;
        if (r <= 0 || at == null || at.getWorld() == null) {
            return false;
        }
        World w = at.getWorld();
        int bx = at.getBlockX();
        int by = at.getBlockY();
        int bz = at.getBlockZ();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Material m = w.getBlockAt(bx + x, by + y, bz + z).getType();
                    if (m != null && cfg.warmthBlocks.contains(m.name())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The walker's frost nova: everyone close is frozen solid, slowed, hurt and shoved back. */
    private boolean nova(World world, Mob walker) {
        List<Player> near = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (playable(p) && distance(p.getLocation(), walker.getLocation()) <= cfg.novaRadius && !zones.isProtected(p.getLocation())) {
                near.add(p);
            }
        }
        if (near.isEmpty()) {
            return false;
        }
        world.spawnParticle(Particle.SNOWFLAKE, walker.getLocation().clone().add(0, 1.0, 0), 200, cfg.novaRadius / 2, 1.0, cfg.novaRadius / 2);
        world.playSound(walker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        for (Player p : near) {
            p.setFreezeTicks(p.getMaxFreezeTicks());
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, true, true));
            if (cfg.novaDamage > 0) {
                p.damage(cfg.novaDamage, walker);
            }
            Vector away = p.getLocation().toVector().subtract(walker.getLocation().toVector());
            if (away.lengthSquared() > 0.0001) {
                p.setVelocity(away.normalize().multiply(0.8).setY(0.35));
            }
            p.sendMessage(ColorText.translate(cfg.msgFrozen));
        }
        return true;
    }

    // ------------------------------------------------------------------ the boss bar

    private void updateBar(Horde h, World world, Mob walker) {
        if (h.bar == null) {
            return;
        }
        double max = walker.getAttribute(Attribute.MAX_HEALTH) == null ? cfg.walkerHealth : walker.getAttribute(Attribute.MAX_HEALTH).getValue();
        h.bar.setProgress(Math.max(0.0, Math.min(1.0, walker.getHealth() / Math.max(1.0, max))));
        Set<UUID> inRange = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (distance(p.getLocation(), walker.getLocation()) <= cfg.bossBarRange) {
                h.bar.addPlayer(p);
                inRange.add(p.getUniqueId());
            }
        }
        for (Player p : new ArrayList<>(h.bar.getPlayers())) {
            if (!inRange.contains(p.getUniqueId())) {
                h.bar.removePlayer(p);
            }
        }
    }

    // ------------------------------------------------------------------ events from the world

    /** The walker died. Called from the death listener, with the event so rewards can be added. */
    void onWalkerDeath(Horde h, EntityDeathEvent event, Player killer) {
        event.getDrops().clear();
        for (RimeConfig.Drop d : h.knight ? cfg.knightRewardDrops : cfg.rewardDrops) {
            if (RecipeSpec.SHARD.equalsIgnoreCase(d.material())) {
                event.getDrops().add(items.shard(d.amount()));
                continue;
            }
            Material m = Material.matchMaterial(d.material());
            if (m != null) {
                event.getDrops().add(new ItemStack(m, d.amount()));
            }
        }
        event.setDroppedExp(h.knight ? cfg.knightRewardXp : cfg.rewardXp);
        end(h, HordeEnd.VICTORY, killer == null ? null : killer.getName());
    }

    void onWightDeath(Horde h, Entity wight) {
        h.wights.remove(wight.getUniqueId());
        h.lastSeen.remove(wight.getUniqueId());
    }

    /** A creature of the horde just hurt a player: remember it, so a death soon after counts. */
    void noteHit(Player victim, Horde horde) {
        recentHordeHits.put(victim.getUniqueId(), new long[] {clock.getAsLong()});
        recentHitHorde.put(victim.getUniqueId(), horde.id);
    }

    /** A player died. If the horde had just hurt them, a wight rises where they fell. */
    void onPlayerDeath(Player victim) {
        long[] when = recentHordeHits.remove(victim.getUniqueId());
        UUID hordeId = recentHitHorde.remove(victim.getUniqueId());
        if (when == null || hordeId == null || clock.getAsLong() - when[0] > RECENT_HIT_MILLIS) {
            return;
        }
        Horde h = null;
        for (Horde candidate : active) {
            if (candidate.id.equals(hordeId)) {
                h = candidate;
            }
        }
        if (h == null) {
            return;
        }
        h.playersLost++;
        report("DEATH", -3, victim.getName() + " fell to the " + cfg.wightName);
        if (cfg.raiseFallen) {
            Location where = victim.getLocation();
            if (where != null && where.getWorld() != null) {
                raiseWight(where.getWorld(), h, where, 1.5);
            }
        }
    }

    private void pruneRecentHits() {
        long now = clock.getAsLong();
        recentHordeHits.entrySet().removeIf(e -> now - e.getValue()[0] > RECENT_HIT_MILLIS);
        recentHitHorde.keySet().removeIf(id -> !recentHordeHits.containsKey(id));
    }

    // ------------------------------------------------------------------ ending

    void end(Horde h, HordeEnd why) {
        end(h, why, null);
    }

    void end(Horde h, HordeEnd why, String killerName) {
        if (!active.remove(h)) {
            return;
        }
        World world = Bukkit.getWorld(h.worldName);

        for (UUID id : new ArrayList<>(h.wights)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                crumble(e);
            }
        }
        h.wights.clear();
        h.lastSeen.clear();
        commander.release(h);

        Entity walker = h.walker == null ? null : Bukkit.getEntity(h.walker);
        if (walker != null && walker.isValid() && why != HordeEnd.VICTORY) {
            crumble(walker);
        }
        if (h.bar != null) {
            h.bar.removeAll();
        }
        if (world != null && stormRaised.contains(h.worldName) && !stormWanted(h.worldName)) {
            stormRaised.remove(h.worldName);
            if (cfg.clearStormOnEnd) {
                world.setStorm(false);
            }
        }

        String template = switch (why) {
            case VICTORY -> cfg.msgVictory;
            case DISSOLVED -> cfg.msgDissolved;
            case SEASON_ENDED -> cfg.msgSeasonEnded;
            case LOST_TARGET -> cfg.msgLostTarget;
            case FADED, ADMIN, SHUTDOWN -> cfg.msgFaded;
        };
        if (cfg.announce && world != null && why != HordeEnd.SHUTDOWN) {
            String line = cfg.fill(template, h.targetName, h.worldName, leaderName(h));
            if (why == HordeEnd.VICTORY && killerName != null) {
                line = line + ColorText.translate(" &7(" + killerName + " struck the final blow)");
            }
            for (Player p : world.getPlayers()) {
                p.sendMessage(line);
            }
        }
        if (why == HordeEnd.VICTORY) {
            report("COMMUNITY", h.knight ? 9 : 6, leaderName(h) + " was slain" + (killerName == null ? "" : " by " + killerName)
                    + (h.playersLost > 0 ? " after claiming " + h.playersLost + " life(s)" : ""));
        } else if (why != HordeEnd.SHUTDOWN) {
            report("NATURE", 1, "The cold passed: " + leaderName(h) + " faded away (" + why.name().toLowerCase(java.util.Locale.ROOT) + ")");
        }
        stateChanged.run();
    }

    private boolean stormWanted(String worldName) {
        for (Horde other : active) {
            if (other.worldName.equals(worldName)) {
                return true;
            }
        }
        return false;
    }

    /** Ends every horde, e.g. when the plugin is switched off. */
    void shutdown() {
        for (Horde h : new ArrayList<>(active)) {
            end(h, HordeEnd.SHUTDOWN);
        }
        recentHordeHits.clear();
        recentHitHorde.clear();
    }

    /** Ends every horde on an admin's say-so; returns how many there were. */
    int stopAll() {
        int n = active.size();
        for (Horde h : new ArrayList<>(active)) {
            end(h, HordeEnd.ADMIN);
        }
        return n;
    }

    private void crumble(Entity e) {
        Location at = e.getLocation();
        if (at != null && at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1.0, 0), 40, 0.4, 0.8, 0.4);
        }
        e.remove();
    }

    /** Removes any horde creature that belongs to a horde that is not running -- left over from a
     *  crash or restart, or in a chunk that was unloaded when its horde ended. Returns how many. */
    int sweepOrphans(Iterable<? extends Entity> entities) {
        int removed = 0;
        for (Entity e : entities) {
            if (isHordeCreature(e) && hordeOf(e) == null) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    static double distance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().getName().equals(b.getWorld().getName())) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void report(String category, int weight, String summary) {
        if (cfg.heartbeat) {
            HeartbeatBridge.report(plugin, category, weight, summary);
        }
    }
}
