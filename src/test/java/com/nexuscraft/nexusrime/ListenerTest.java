package com.nexuscraft.nexusrime;

import com.nexusuniverse.realms.api.NexusRealmsApi;
import com.nexusuniverse.seasons.NexusSeasonsAPI;
import com.nexusuniverse.seasons.Season;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrime.Kit.WORLD;
import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** The listener (where the walker's weakness lives), the blade, the command, and the plugin bridges. */
public final class ListenerTest {

    /** A console-like sender. */
    static final class Console implements CommandSender {
        final List<String> out = new ArrayList<>();
        boolean admin;

        @Override
        public boolean hasPermission(String node) {
            return admin;
        }

        @Override
        public void sendMessage(String message) {
            out.add(message);
        }

        @Override
        public String getName() {
            return "CONSOLE";
        }

        @Override
        public void sendMessage(Component message) {
        }

        boolean heard(String fragment) {
            for (String s : out) {
                if (s.contains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) {
        bridges();
        weakness();
        playerHits();
        deathsAndTargets();
        blade();
        command();
        guard();
        heartbeat();
        reflect();
        Kit.finish();
    }

    // ------------------------------------------------------------------ helpers

    private static EntityDamageEvent hit(Entity victim, Entity damager, DamageCause cause, double damage) {
        EntityDamageEvent e = damager == null ? new EntityDamageEvent(victim, cause) : new EntityDamageByEntityEvent(damager, victim, cause);
        e.setDamage(damage);
        return e;
    }

    private static void hold(Kit.TestPlayer p, ItemStack item) {
        p.getInventory().setItemInMainHand(item);
    }

    // ------------------------------------------------------------------ reading the other plugins

    private static void bridges() {
        Kit.reset();
        SeasonBridge seasons = new SeasonBridge();
        expect(seasons.status() == SeasonStatus.UNKNOWN, "season bridge: nothing registered -> unknown");
        RimeConfig bridgeCfg = new RimeConfig();
        RealmsBridge realms = new RealmsBridge(bridgeCfg);
        expect(!realms.available(), "realms bridge: nothing registered -> not available");
        expect(!realms.isClaimed(new Location(WORLD, 1, 64, 1)), "realms bridge: not available -> nothing is claimed");

        Season[] now = {Season.WINTER};
        Bukkit.getServicesManager().register(NexusSeasonsAPI.class, new NexusSeasonsAPI() {
            @Override
            public Season getCurrentSeason() {
                return now[0];
            }

            @Override
            public int getCurrentYear() {
                return 1;
            }

            @Override
            public int getDayOfSeason() {
                return 1;
            }

            @Override
            public int getDaysPerSeason() {
                return 7;
            }
        }, new Kit.TestPlugin(), org.bukkit.plugin.ServicePriority.Normal);
        expect(seasons.status() == SeasonStatus.WINTER, "season bridge: winter reads as winter");
        for (Season s : new Season[] {Season.SPRING, Season.SUMMER, Season.FALL}) {
            now[0] = s;
            expect(seasons.status() == SeasonStatus.NOT_WINTER, "season bridge: " + s + " is not winter");
        }
        now[0] = null;
        expect(seasons.status() == SeasonStatus.UNKNOWN, "season bridge: a null season is unknown");
        Bukkit.getServicesManager().register(NexusSeasonsAPI.class, new NexusSeasonsAPI() {
            @Override
            public Season getCurrentSeason() {
                throw new IllegalStateException("boom");
            }

            @Override
            public int getCurrentYear() {
                return 0;
            }

            @Override
            public int getDayOfSeason() {
                return 0;
            }

            @Override
            public int getDaysPerSeason() {
                return 0;
            }
        }, new Kit.TestPlugin(), org.bukkit.plugin.ServicePriority.Normal);
        expect(seasons.status() == SeasonStatus.UNKNOWN, "season bridge: a failing season plugin is unknown, not a crash");

        // Realms: registered AFTER the bridge first looked, so the lazy retry is exercised.
        List<UUID> asked = new ArrayList<>();
        UUID team = UUID.randomUUID();
        Bukkit.getServicesManager().register(NexusRealmsApi.class, new NexusRealmsApi() {
            @Override
            public boolean canBuild(UUID id, Location loc) {
                asked.add(id);
                return loc.getX() < 1000;          // x >= 1000 is a personal claim
            }

            @Override
            public UUID teamOwning(Location loc) {
                return loc.getZ() > 2000 ? team : null; // z > 2000 is team territory
            }
        }, new Kit.TestPlugin(), org.bukkit.plugin.ServicePriority.Normal);
        expect(realms.available(), "realms bridge: found once it is registered (load order never matters)");
        Location open = new Location(WORLD, 500, 64, 0);
        Location personal = new Location(WORLD, 1500, 64, 0);
        Location territory = new Location(WORLD, 500, 64, 2500);
        expect(!realms.isClaimed(open), "realms bridge: open ground");
        expect(realms.isClaimed(personal), "realms bridge: a personal claim (a stranger cannot build)");
        expect(realms.isClaimed(territory), "realms bridge: team territory");
        expect(asked.get(0).equals(RealmsBridge.STRANGER), "realms bridge: asks about the nobody-id");
        expect(!realms.isClaimed(null), "realms bridge: null is not claimed");
        bridgeCfg.realmsCheck = "territory";
        expect(!realms.isClaimed(personal) && realms.isClaimed(territory), "realms bridge: 'territory' counts only team land");
        bridgeCfg.realmsCheck = "build";
        expect(realms.isClaimed(personal) && !realms.isClaimed(territory), "realms bridge: 'build' counts only what a stranger cannot build on");
        bridgeCfg.realmsCheck = "both";

        // An older Realms without teamOwning still works through the build check.
        Bukkit.getServicesManager().register(NexusRealmsApi.class, new NexusRealmsApi() {
            @Override
            public boolean canBuild(UUID id, Location loc) {
                throw new IllegalStateException("boom");
            }

            @Override
            public UUID teamOwning(Location loc) {
                throw new IllegalStateException("boom");
            }
        }, new Kit.TestPlugin(), org.bukkit.plugin.ServicePriority.Normal);
        RimeConfig freshCfg = new RimeConfig();
        RealmsBridge fresh = new RealmsBridge(freshCfg);
        expect(!fresh.isClaimed(personal), "realms bridge: a failing Realms means 'not claimed', not a crash");
        expect(fresh.available(), "realms bridge: and it is still reported as present");
    }

    // ------------------------------------------------------------------ the walker's weakness

    private static void weakness() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = env.director.tryStart(WORLD, alex, true).horde();
        Stray walker = (Stray) env.walkerOf(h);
        Zombie wight = (Zombie) Bukkit.getEntity(h.wights.iterator().next());

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack blade = env.items.blade(1);

        hold(alex, sword);
        EntityDamageEvent e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.35) && !e.isCancelled(), "weakness: an ordinary diamond sword does 0.35 to the walker");

        hold(alex, blade);
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 14.0), "weakness: the forged blade does 14");
        e = hit(walker, alex, DamageCause.ENTITY_SWEEP_ATTACK, 4.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 8.0), "weakness: a sweep with the blade counts too");
        e = hit(walker, alex, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.3), "weakness: an arrow fired while holding the blade is still just an arrow");

        // Offhand blade does not count; only the main hand.
        hold(alex, sword);
        alex.getInventory().setItemInOffHand(blade);
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.35), "weakness: the blade must be in the main hand");

        // A non-player attacker never counts as forged
        Zombie other = WORLD.spawn(new Location(WORLD, 1, 64, 1), Zombie.class);
        e = hit(walker, other, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.35), "weakness: a mob's hit is ordinary");

        // Fire
        e = hit(walker, null, DamageCause.FIRE_TICK, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 3.0), "weakness: burning hurts the walker for 3 a tick");
        e = hit(walker, null, DamageCause.LAVA, 4.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 4.0), "weakness: lava is capped at 4 a hit");
        e = hit(walker, null, DamageCause.FIRE, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 3.0), "weakness: standing in fire");
        e = hit(walker, null, DamageCause.FALL, 10.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.5), "weakness: a fall barely matters");
        e = hit(walker, null, DamageCause.ENTITY_EXPLOSION, 20.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 1.0), "weakness: neither does an explosion");
        e = hit(walker, null, DamageCause.FREEZE, 1.0);
        env.listener.onDamage(e);
        expect(e.isCancelled(), "weakness: the walker is immune to freezing");

        // The cheap tricks are closed: the walker's hp cannot be chipped down by spamming ordinary hits.
        double total = 0.0;
        for (int i = 0; i < 100; i++) {
            EntityDamageEvent spam = hit(walker, alex, DamageCause.ENTITY_ATTACK, 8.0);
            hold(alex, sword);
            env.listener.onDamage(spam);
            total += spam.getDamage();
        }
        expect(total < env.cfg.walkerHealth / 4, "weakness: a hundred sword hits do not take even a quarter of its health (" + total + ")");
        double forgedHits = Math.ceil(env.cfg.walkerHealth / DamageRules.walkerDamage(env.cfg, DamageKind.FORGED, 7.0));
        expect(forgedHits >= 10 && forgedHits <= 20, "weakness: about " + forgedHits + " blade hits fell it (a real fight, not a chore)");

        // Wights
        hold(alex, blade);
        e = hit(wight, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(e.getDamage() >= env.cfg.wightHealth, "weakness: one blade hit shatters a wight");
        hold(alex, sword);
        e = hit(wight, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 7.0), "weakness: a sword hurts a wight normally");
        e = hit(wight, null, DamageCause.FIRE_TICK, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 2.5), "weakness: wights are weak to fire");
        e = hit(wight, null, DamageCause.FREEZE, 1.0);
        env.listener.onDamage(e);
        expect(e.isCancelled(), "weakness: wights are immune to freezing");

        // Creatures that are not ours are never touched
        e = hit(other, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 7.0) && !e.isCancelled(), "weakness: ordinary zombies are untouched");
        e = hit(alex, null, DamageCause.FREEZE, 1.0);
        env.listener.onDamage(e);
        expect(!e.isCancelled() && near(e.getDamage(), 1.0), "weakness: players still take freezing damage");
        e = hit(alex, other, DamageCause.ENTITY_ATTACK, 3.0);
        env.listener.onDamage(e);
        expect(!e.isCancelled() && alex.getFreezeTicks() == 0, "weakness: an ordinary zombie's hit does not freeze");

        // Configurable
        env.cfg.walkerOrdinaryMultiplier = 1.0;
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 7.0), "weakness: the ordinary multiplier is configurable");
    }

    // ------------------------------------------------------------------ horde creatures hitting players

    private static void playerHits() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = env.director.tryStart(WORLD, alex, true).horde();
        Stray walker = (Stray) env.walkerOf(h);
        Zombie wight = (Zombie) Bukkit.getEntity(h.wights.iterator().next());

        EntityDamageEvent e = hit(alex, wight, DamageCause.ENTITY_ATTACK, 3.0);
        env.listener.onDamage(e);
        expect(!e.isCancelled() && near(e.getDamage(), 3.0), "hits: a wight's bite is not changed");
        expect(alex.getFreezeTicks() == 100, "hits: but it chills (100 frost ticks)");
        env.listener.onDamage(hit(alex, wight, DamageCause.ENTITY_ATTACK, 3.0));
        expect(alex.getFreezeTicks() == alex.getMaxFreezeTicks(), "hits: frost is capped at a full freeze");

        // an arrow shot by the walker
        Projectile arrow = new Projectile(walker);
        alex.setFreezeTicks(0);
        e = hit(alex, arrow, DamageCause.PROJECTILE, 4.0);
        env.listener.onDamage(e);
        expect(alex.getFreezeTicks() == 100, "hits: the walker's arrows chill too");
        env.director.onPlayerDeath(alex);
        expect(h.playersLost == 1, "hits: and an arrow death counts for the horde");
        // an arrow shot by a player is not a horde hit
        Kit.TestPlayer bea = env.player("Bea", 610, 600);
        alex.setFreezeTicks(0);
        e = hit(alex, new Projectile(bea), DamageCause.PROJECTILE, 4.0);
        env.listener.onDamage(e);
        expect(alex.getFreezeTicks() == 0, "hits: a friend's arrow does not chill");

        // Safe ground
        Kit.TestPlayer safe = env.player("Safe", 20, 20);
        e = hit(safe, wight, DamageCause.ENTITY_ATTACK, 3.0);
        env.listener.onDamage(e);
        expect(e.isCancelled() && safe.getFreezeTicks() == 0, "hits: nothing on the horde can hurt someone on spawn ground");
        e = hit(safe, new Projectile(walker), DamageCause.PROJECTILE, 3.0);
        env.listener.onDamage(e);
        expect(e.isCancelled(), "hits: nor its arrows");
        env.realms.claimed = l -> l.getX() > 650;
        Kit.TestPlayer claimed = env.player("Claimed", 700, 600);
        e = hit(claimed, wight, DamageCause.ENTITY_ATTACK, 3.0);
        env.listener.onDamage(e);
        expect(e.isCancelled(), "hits: nor anyone on claimed land");

        // No horde bookkeeping when the attacker is a left-over from a finished horde
        Zombie stale = WORLD.spawn(new Location(WORLD, 600, 64, 601), Zombie.class);
        stale.getPersistentDataContainer().set(env.keys.mobRole, PersistentDataType.STRING, "wight");
        stale.getPersistentDataContainer().set(env.keys.mobHorde, PersistentDataType.STRING, UUID.randomUUID().toString());
        alex.setFreezeTicks(0);
        e = hit(alex, stale, DamageCause.ENTITY_ATTACK, 2.0);
        env.listener.onDamage(e);
        expect(!e.isCancelled() && alex.getFreezeTicks() == 0, "hits: a left-over creature does no horde business");
        // Frost on hit can be switched off.
        env.cfg.wightHitFrostTicks = 0;
        e = hit(alex, wight, DamageCause.ENTITY_ATTACK, 2.0);
        env.listener.onDamage(e);
        expect(alex.getFreezeTicks() == 0, "hits: hit-frost can be switched off");
    }

    // ------------------------------------------------------------------ deaths and targeting

    private static void deathsAndTargets() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = env.director.tryStart(WORLD, alex, true).horde();
        Stray walker = (Stray) env.walkerOf(h);
        UUID wid = h.wights.iterator().next();
        Zombie wight = (Zombie) Bukkit.getEntity(wid);

        EntityDeathEvent wd = new EntityDeathEvent(wight);
        wd.getDrops().add(new ItemStack(Material.BONE, 1));
        wd.setDroppedExp(5);
        env.listener.onDeath(wd);
        expect(wd.getDrops().isEmpty() && wd.getDroppedExp() == 0, "death: wights drop nothing");
        expect(!h.wights.contains(wid), "death: a fallen wight leaves the roster");
        expect(env.director.hordes().size() == 1, "death: the horde carries on");

        Zombie plain = WORLD.spawn(new Location(WORLD, 1, 64, 1), Zombie.class);
        EntityDeathEvent pd = new EntityDeathEvent(plain);
        pd.getDrops().add(new ItemStack(Material.BONE, 1));
        env.listener.onDeath(pd);
        expect(pd.getDrops().size() == 1, "death: ordinary mobs keep their drops");

        Zombie stale = WORLD.spawn(new Location(WORLD, 2, 64, 1), Zombie.class);
        stale.getPersistentDataContainer().set(env.keys.mobRole, PersistentDataType.STRING, "walker");
        stale.getPersistentDataContainer().set(env.keys.mobHorde, PersistentDataType.STRING, UUID.randomUUID().toString());
        EntityDeathEvent sd = new EntityDeathEvent(stale);
        sd.getDrops().add(new ItemStack(Material.BONE, 1));
        env.listener.onDeath(sd);
        expect(sd.getDrops().isEmpty() && env.director.hordes().size() == 1, "death: a left-over walker drops nothing and ends nothing");

        EntityDeathEvent kd = new EntityDeathEvent(walker);
        kd.getDrops().add(new ItemStack(Material.BONE, 1));
        env.listener.onDeath(kd);
        expect(env.director.hordes().isEmpty(), "death: killing the walker ends the horde");
        expect(!kd.getDrops().isEmpty() && kd.getDrops().stream().noneMatch(s -> s.getType() == Material.BONE) && kd.getDroppedExp() == 500,
                "death: and pays out the rewards");

        // Player deaths feed the horde
        Env e2 = new Env();
        Kit.TestPlayer p = e2.player("P", 600, 600);
        Horde h2 = e2.director.tryStart(WORLD, p, true).horde();
        Zombie w2 = (Zombie) Bukkit.getEntity(h2.wights.iterator().next());
        e2.listener.onDamage(hit(p, w2, DamageCause.ENTITY_ATTACK, 3.0));
        int before = h2.wights.size();
        e2.listener.onPlayerDeath(new PlayerDeathEvent(p, new ArrayList<>(), 0, null));
        expect(h2.wights.size() == before + 1 && h2.playersLost == 1, "death: a player killed by the horde rises as a wight");

        // Targeting
        Env e3 = new Env();
        Kit.TestPlayer a = e3.player("A", 600, 600);
        Horde h3 = e3.director.tryStart(WORLD, a, true).horde();
        Zombie w3 = (Zombie) Bukkit.getEntity(h3.wights.iterator().next());
        Kit.TestPlayer safe = e3.player("Safe", 20, 20);
        EntityTargetLivingEntityEvent t1 = new EntityTargetLivingEntityEvent(w3, safe);
        e3.listener.onTarget(t1);
        expect(t1.isCancelled(), "target: horde creatures never lock on to someone on safe ground");
        EntityTargetLivingEntityEvent t2 = new EntityTargetLivingEntityEvent(w3, a);
        e3.listener.onTarget(t2);
        expect(!t2.isCancelled(), "target: but do lock on to anyone else");
        Zombie plain3 = WORLD.spawn(new Location(WORLD, 3, 64, 3), Zombie.class);
        EntityTargetLivingEntityEvent t3 = new EntityTargetLivingEntityEvent(plain3, safe);
        e3.listener.onTarget(t3);
        expect(!t3.isCancelled(), "target: ordinary mobs are not our business");
        EntityTargetLivingEntityEvent t4 = new EntityTargetLivingEntityEvent(w3, null);
        e3.listener.onTarget(t4);
        expect(!t4.isCancelled(), "target: losing a target is never blocked");
    }

    // ------------------------------------------------------------------ the blade

    private static void blade() {
        Env env = new Env();
        ItemStack b = env.items.blade(2);
        expect(b.getType() == Material.DIAMOND_SWORD && b.getAmount() == 2, "blade: a diamond sword, amount honoured");
        expect(b.getItemMeta().getDisplayName().contains("Dragonglass Blade"), "blade: named from the config");
        expect(b.getItemMeta().getLore().stream().anyMatch(s -> s.contains("Rimewalker")) && b.getItemMeta().getLore().stream().anyMatch(s -> s.contains("Rimebound")),
                "blade: the lore names the creatures it fights");
        expect(env.items.isBlade(b), "blade: recognised");
        expect(!env.items.isBlade(new ItemStack(Material.DIAMOND_SWORD)), "blade: a plain sword is not the blade");
        expect(!env.items.isBlade(null), "blade: null is not the blade");
        ItemStack renamed = new ItemStack(Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = renamed.getItemMeta();
        meta.setDisplayName("§b§lDragonglass Blade");
        renamed.setItemMeta(meta);
        expect(!env.items.isBlade(renamed), "blade: a sword merely renamed to match is not the blade (the tag decides)");
        expect(env.items.blade(1).getItemMeta().getPersistentDataContainer().get(env.keys.itemKind, PersistentDataType.STRING).equals("blade"),
                "blade: tagged in item data");

        env.cfg.gear.get("blade").material = "NETHERITE_SWORD";
        expect(env.items.blade(1).getType() == Material.NETHERITE_SWORD, "blade: material is configurable");
        env.cfg.gear.get("blade").material = "BANANA";
        expect(env.items.blade(1).getType() == Material.DIAMOND_SWORD, "blade: an unknown material falls back to a diamond sword");
        env.cfg.weaponName = "Ice Cutter";
        expect(env.items.blade(1).getItemMeta().getDisplayName().contains("Ice Cutter"), "blade: renaming works");
    }

    // ------------------------------------------------------------------ /rime

    private static void command() {
        Env env = new Env();
        int[] reloads = {0};
        RimeCommand cmd = new RimeCommand(env.cfg, env.director, env.items, env.season, env.realms, () -> env.now[0], () -> reloads[0]++);
        Command c = new Command();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Kit.TestPlayer admin = env.player("Admin", 640, 600);
        admin.perms.add("nexusrime.admin");
        Console console = new Console();

        cmd.onCommand(alex, c, "rime", new String[0]);
        expect(alex.heard("is quiet"), "command: status when nothing walks");
        cmd.onCommand(alex, c, "rime", new String[] {"recipe"});
        expect(alex.heard("Dragonglass Shard") && alex.heard("/rime recipe blade") && alex.heard("Only dragonglass truly wounds"), "command: recipe with no item lists what can be crafted");
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "blade"});
        expect(alex.heard("Dragonglass Blade") && alex.heard("Dragonglass Shard") && alex.heard("Diamond") && alex.heard("Stick"), "command: the blade recipe lists its ingredients");
        cmd.onCommand(alex, c, "rime", new String[] {"start"});
        expect(alex.heard("permission") && env.director.hordes().isEmpty(), "command: players cannot start hordes");
        cmd.onCommand(alex, c, "rime", new String[] {"stop"});
        cmd.onCommand(alex, c, "rime", new String[] {"give"});
        cmd.onCommand(alex, c, "rime", new String[] {"reload"});
        boolean gotBlade = false;
        for (ItemStack s : alex.getInventory().getContents()) {
            gotBlade |= env.items.isBlade(s);
        }
        expect(reloads[0] == 0 && !gotBlade && env.director.hordes().isEmpty(), "command: players cannot stop, give or reload");
        cmd.onCommand(alex, c, "rime", new String[] {"nonsense"});
        expect(alex.lastMessage().contains("/rime [status|recipe") && !alex.lastMessage().contains("start"), "command: help hides admin commands from players");
        cmd.onCommand(admin, c, "rime", new String[] {"nonsense"});
        expect(admin.lastMessage().contains("start"), "command: help shows admin commands to admins");

        cmd.onCommand(admin, c, "rime", new String[] {"start", "Nobody"});
        expect(admin.heard("No player named Nobody"), "command: start with an unknown name");
        cmd.onCommand(console, c, "rime", new String[] {"start"});
        expect(console.heard("permission"), "command: a sender without the permission is refused");
        console.admin = true;
        cmd.onCommand(console, c, "rime", new String[] {"start"});
        expect(console.heard("Name a player"), "command: console must name a player");
        cmd.onCommand(admin, c, "rime", new String[] {"start", "Alex"});
        expect(admin.heard("rises and marches on Alex") && env.director.hordes().size() == 1, "command: admin starts a horde on a named player");
        expect(env.director.hordes().get(0).forced, "command: an admin start is forced");
        cmd.onCommand(admin, c, "rime", new String[] {"start", "Alex"});
        expect(admin.heard("already out"), "command: a second start explains why not");
        cmd.onCommand(alex, c, "rime", new String[] {"status"});
        expect(alex.heard("is hunting") && alex.heard("Alex") && alex.heard("blocks from you"), "command: status shows the hunt and the distance");
        cmd.onCommand(admin, c, "rime", new String[] {"status"});
        expect(admin.heard("Season: winter") && admin.heard("claims: NexusRealms"), "command: admins also see season and claim info");
        expect(admin.heard("next event allowed in: ") , "command: and the cooldown");
        cmd.onCommand(admin, c, "rime", new String[] {"stop"});
        expect(admin.heard("Ended 1 horde") && env.director.hordes().isEmpty(), "command: stop ends it");

        // start with no name as a player targets themselves
        cmd.onCommand(admin, c, "rime", new String[] {"start"});
        expect(env.director.hordes().size() == 1 && env.director.hordes().get(0).targetName.equals("Admin"), "command: a player admin with no name hunts themselves");
        env.director.stopAll();
        // an ineligible target explains itself
        Kit.TestPlayer safe = env.player("Safe", 20, 20);
        cmd.onCommand(admin, c, "rime", new String[] {"start", "Safe"});
        expect(admin.heard("cannot be hunted"), "command: a protected target is explained");

        // give
        cmd.onCommand(admin, c, "rime", new String[] {"give", "Alex"});
        boolean has = false;
        for (ItemStack s : alex.getInventory().getContents()) {
            has |= env.items.isBlade(s);
        }
        expect(has && admin.heard("Gave 1 x Dragonglass Blade to Alex"), "command: give puts the blade in their inventory");
        cmd.onCommand(admin, c, "rime", new String[] {"give", "Nobody"});
        expect(admin.heard("No player named Nobody"), "command: give to an unknown player");
        cmd.onCommand(console, c, "rime", new String[] {"give"});
        expect(console.heard("Name an online player"), "command: console must name a player to give to");
        cmd.onCommand(admin, c, "rime", new String[] {"reload"});
        expect(reloads[0] == 1 && admin.heard("reloaded"), "command: reload runs the reload hook");

        // Every verdict has a sentence
        boolean allExplained = true;
        for (GateVerdict v : GateVerdict.values()) {
            allExplained &= !RimeCommand.explain(v).isBlank();
        }
        expect(allExplained, "command: every verdict is explained in words");

        // Tab completion
        List<String> top = cmd.onTabComplete(alex, c, "rime", new String[] {""});
        expect(top.equals(List.of("status", "recipe")), "tab: players see status and recipe");
        List<String> adminTop = cmd.onTabComplete(admin, c, "rime", new String[] {"s"});
        expect(adminTop.containsAll(List.of("status", "start", "stop")) && !adminTop.contains("give"), "tab: admins see the admin commands, filtered by prefix");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"start", "al"}).equals(List.of("Alex")), "tab: player names complete after start");
        expect(cmd.onTabComplete(alex, c, "rime", new String[] {"start", "al"}).isEmpty(), "tab: players get no names to start with");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"status", "al"}).isEmpty(), "tab: status takes no name");
    }

    // ------------------------------------------------------------------ the zone guard

    private static void guard() {
        Env env = new Env();
        ZoneGuard g = env.zones;
        expect(g.isProtected(new Location(WORLD, 10, 64, 10)), "guard: spawn is protected");
        expect(!g.isProtected(new Location(WORLD, 600, 64, 600)), "guard: open ground is not");
        expect(!g.isProtected(null), "guard: null is not");
        expect(!g.isProtected(new Location(null, 0, 0, 0)), "guard: a null world is not");
        env.realms.claimed = l -> l.getX() > 500;
        expect(g.isProtected(new Location(WORLD, 600, 64, 600)), "guard: a claimed spot is protected");
        env.cfg.useRealms = false;
        expect(!g.isProtected(new Location(WORLD, 600, 64, 600)), "guard: use-realms off ignores claims");
        env.cfg.useRealms = true;
        env.realms.present = false;
        expect(!g.isProtected(new Location(WORLD, 600, 64, 600)), "guard: without Realms only spawn counts");
        env.cfg.protectedWorlds = List.of("world");
        expect(g.isProtected(new Location(WORLD, 600, 64, 600)), "guard: a wholly protected world");
    }

    // ------------------------------------------------------------------ mood reports

    private static void heartbeat() {
        Kit.reset();
        Kit.TestPlugin plugin = new Kit.TestPlugin();
        HeartbeatBridge.report(plugin, "CONFLICT", -5, "x");
        expect(Bukkit.DISPATCHED.isEmpty(), "heartbeat: nothing sent when NexusHeartbeat is not installed");
        org.bukkit.plugin.PluginManager.ENABLED.add("NexusHeartbeat");
        HeartbeatBridge.report(plugin, "CONFLICT", -5, "line one\nline two");
        expect(Bukkit.DISPATCHED.size() == 1 && Bukkit.DISPATCHED.get(0).equals("heartbeat report CONFLICT -5 line one line two"), "heartbeat: command shaped right, newlines flattened");
        HeartbeatBridge.report(plugin, " ", 1, "blank category");
        expect(Bukkit.DISPATCHED.get(1).startsWith("heartbeat report MISC 1"), "heartbeat: blank category becomes MISC");
        int n = Bukkit.DISPATCHED.size();
        HeartbeatBridge.report(null, "A", 1, "x");
        HeartbeatBridge.report(plugin, "A", 1, null);
        HeartbeatBridge.report(plugin, "A", 1, "  ");
        expect(Bukkit.DISPATCHED.size() == n, "heartbeat: null plugin or empty summary sends nothing");
    }

    // ------------------------------------------------------------------ sunlight

    private static void reflect() {
        final class Burny extends Zombie {
            boolean burns = true;

            public void setShouldBurnInDay(boolean b) {
                burns = b;
            }
        }
        Burny b = new Burny();
        Reflect.noSunBurn(b);
        expect(!b.burns, "reflect: sunlight burning is switched off where supported");
        Reflect.noSunBurn(new Zombie());
        expect(true, "reflect: a server without the method is fine");
        final class Angry extends Zombie {
            public void setShouldBurnInDay(boolean b) {
                throw new IllegalStateException("no");
            }
        }
        Reflect.noSunBurn(new Angry());
        expect(true, "reflect: a method that throws is swallowed");
        Reflect.noSunBurn(null);
        expect(true, "reflect: null is ignored");
    }
}
