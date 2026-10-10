package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Stray;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.nexuscraft.nexusrime.Kit.WORLD;
import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** Obsidian drops, the Frost Knight's spawn and rewards, bridge bookkeeping, and the Knight at war with a stub world. */
public final class KnightTest {

    static final int OX = 600;
    static final int OZ = 600;

    public static void main(String[] args) {
        obsidianRolls();
        obsidianListener();
        config();
        spawning();
        reinforcements();
        rewards();
        command();
        ledger();
        ledgerFile();
        ledgerTiming();
        terrain();
        Kit.finish();
    }

    // ------------------------------------------------------------------ obsidian

    private static void obsidianRolls() {
        RimeConfig c = new RimeConfig();
        expect(c.obsidianDrop && near(c.obsidianChance, 10.0) && c.obsidianMin == 1 && c.obsidianMax == 2, "obsidian: defaults are 10% for one or two shards");
        expect(ObsidianDrop.shards(c, 0.0, 0.0) == 1, "obsidian: the luckiest roll, lowest amount");
        expect(ObsidianDrop.shards(c, 9.99, 0.0) == 1, "obsidian: just under the chance drops");
        expect(ObsidianDrop.shards(c, 10.0, 0.0) == 0, "obsidian: exactly the chance does not");
        expect(ObsidianDrop.shards(c, 55.0, 0.0) == 0, "obsidian: most blocks give nothing");
        expect(ObsidianDrop.shards(c, 1.0, 0.0) == 1 && ObsidianDrop.shards(c, 1.0, 0.49) == 1, "obsidian: the lower half of the spread is one");
        expect(ObsidianDrop.shards(c, 1.0, 0.5) == 2 && ObsidianDrop.shards(c, 1.0, 0.999999) == 2, "obsidian: the upper half is two");
        expect(ObsidianDrop.shards(c, 1.0, 1.0) == 2 && ObsidianDrop.shards(c, 1.0, 7.0) == 2, "obsidian: a silly roll is held at the maximum");
        expect(ObsidianDrop.shards(c, 1.0, -3.0) == 1, "obsidian: and a negative one at the minimum");
        c.obsidianMin = 3;
        c.obsidianMax = 3;
        expect(ObsidianDrop.shards(c, 1.0, 0.7) == 3, "obsidian: min equal to max is always that many");
        c.obsidianMin = 2;
        c.obsidianMax = 5;
        int[] seen = new int[7];
        for (int i = 0; i < 400; i++) {
            seen[ObsidianDrop.shards(c, 0.0, i / 400.0)]++;
        }
        expect(seen[2] > 0 && seen[3] > 0 && seen[4] > 0 && seen[5] > 0 && seen[1] == 0 && seen[6] == 0, "obsidian: every amount from min to max comes up, none outside");
        c.obsidianDrop = false;
        expect(ObsidianDrop.shards(c, 0.0, 0.0) == 0, "obsidian: switched off gives nothing");
        c.obsidianDrop = true;
        c.obsidianChance = 100.0;
        expect(ObsidianDrop.shards(c, 99.99, 0.0) >= 2, "obsidian: a 100% chance always drops");
        c.obsidianChance = 0.0;
        expect(ObsidianDrop.shards(c, 0.0, 0.0) == 0, "obsidian: a 0% chance never does");
    }

    /** A Random that answers from a script, so a test can say exactly what was rolled. */
    static final class Rolls extends Random {
        private static final long serialVersionUID = 1L;
        private final double[] values;
        private int next;

        Rolls(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            return values[next++ % values.length];
        }
    }

    private static BlockDropItemEvent mined(Kit.TestPlayer p, Material m, boolean dropped) {
        org.bukkit.block.Block b = WORLD.getBlockAt(OX, 40, OZ);
        b.setType(m);
        org.bukkit.block.BlockState state = b.getState();
        b.setType(Material.AIR);
        List<Item> items = new ArrayList<>();
        if (dropped) {
            items.add(new Item());
        }
        return new BlockDropItemEvent(b, state, p, items);
    }

    private static int shardsDropped(Env env) {
        int n = 0;
        for (ItemStack s : WORLD.stubDrops) {
            if (env.items.isShard(s)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static void obsidianListener() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        ObsidianListener lucky = new ObsidianListener(env.cfg, env.items, new Rolls(0.0, 0.9));
        lucky.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 2, "obsidian drop: a lucky block drops its shards (" + shardsDropped(env) + ")");
        expect(alex.heard("Dragonglass Shard") && alex.heard("x2"), "obsidian drop: and the miner is told");
        expect(WORLD.stubDrops.size() == 1 && WORLD.stubDrops.get(0).getType() == Material.FLINT, "obsidian drop: the shard is the tagged dragonglass item");
        Location fell = WORLD.stubDropAt.get(WORLD.stubDropAt.size() - 1);
        expect(near(fell.getX(), OX + 0.5) && near(fell.getY(), 40.5) && near(fell.getZ(), OZ + 0.5), "obsidian drop: it falls from the middle of the block (" + fell.getX() + "," + fell.getY() + "," + fell.getZ() + ")");

        WORLD.stubDrops.clear();
        alex.messages.clear();
        ObsidianListener unlucky = new ObsidianListener(env.cfg, env.items, new Rolls(0.5, 0.0));
        unlucky.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 0 && alex.messages.isEmpty(), "obsidian drop: an unlucky block drops nothing and says nothing");

        ObsidianListener always = new ObsidianListener(env.cfg, env.items, new Rolls(0.0, 0.0));
        always.onDrop(mined(alex, Material.CRYING_OBSIDIAN, true));
        always.onDrop(mined(alex, Material.STONE, true));
        always.onDrop(mined(alex, Material.DIAMOND_ORE, true));
        expect(shardsDropped(env) == 0, "obsidian drop: only plain obsidian counts");
        always.onDrop(mined(alex, Material.OBSIDIAN, false));
        expect(shardsDropped(env) == 0, "obsidian drop: obsidian that gave nothing (wrong pick) shakes loose nothing");
        alex.mode = GameMode.CREATIVE;
        always.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 0, "obsidian drop: not in creative");
        alex.mode = GameMode.SPECTATOR;
        always.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 0, "obsidian drop: not as a spectator");
        alex.mode = GameMode.ADVENTURE;
        always.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 1, "obsidian drop: adventure mode is fine");
        WORLD.stubDrops.clear();
        always.onDrop(mined(null, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 0, "obsidian drop: a block broken by no player pays nothing");
        env.cfg.obsidianDrop = false;
        always.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(shardsDropped(env) == 0, "obsidian drop: switched off");
        env.cfg.obsidianDrop = true;
        BlockDropItemEvent noState = new BlockDropItemEvent(WORLD.getBlockAt(OX, 40, OZ), null, alex, new ArrayList<>(List.of(new Item())));
        always.onDrop(noState);
        expect(shardsDropped(env) == 0, "obsidian drop: no block state, no drop");
        env.cfg.shardName = "Glass Shard";
        alex.messages.clear();
        env.cfg.obsidianMin = 1;
        env.cfg.obsidianMax = 1;
        always.onDrop(mined(alex, Material.OBSIDIAN, true));
        expect(alex.heard("Glass Shard") && !alex.heard("x1"), "obsidian drop: the message uses the configured name, and a single shard has no count");
    }

    // ------------------------------------------------------------------ config

    private static void config() {
        RimeConfig c = new RimeConfig();
        Kit.MapSection y = new Kit.MapSection();
        y.put("names.knight", "Ice King");
        y.put("obsidian-drop.enabled", false);
        y.put("obsidian-drop.chance-percent", 250.0);
        y.put("obsidian-drop.min", 0);
        y.put("obsidian-drop.max", -4);
        y.put("knight.chance-percent", -5.0);
        y.put("knight.health", 99999.0);
        y.put("knight.scale", 0.01);
        y.put("knight.reinforce-seconds", 0);
        y.put("knight.extra-wights", 5000);
        y.put("knight.rewards.xp", 7);
        y.put("knight.rewards.drops", List.of("DIAMOND:2", "nonsense:x", "EMERALD"));
        y.put("knight.tactics.plan-seconds", 0);
        y.put("knight.tactics.plan-nodes", 3);
        y.put("knight.tactics.plan-radius", 9999);
        y.put("knight.tactics.engage-distance", 1.0);
        y.put("knight.breach.crew", 0);
        y.put("knight.breach.door-strength", 0.0);
        y.put("knight.breach.iron-factor", 0.1);
        y.put("knight.stack.max-height", 99);
        y.put("knight.stack.enabled", false);
        y.put("knight.stack.glow", false);
        y.put("knight.stack.build-seconds", 0);
        y.put("knight.bridge.material", "blue_ice");
        y.put("knight.bridge.max-blocks", 100000);
        y.put("knight.bridge.lifetime-seconds", 1);
        y.put("messages.knight-bridge", "&bIce on the {liquid}");
        c.load(y);
        expect(c.knightName.equals("Ice King"), "config: the knight can be renamed");
        expect(!c.obsidianDrop && near(c.obsidianChance, 100.0), "config: obsidian chance is held to 0-100");
        expect(c.obsidianMin == 1 && c.obsidianMax == 1, "config: obsidian min is at least 1 and max is never below it");
        expect(near(c.knightChance, 0.0), "config: a negative knight chance is zero");
        expect(near(c.knightHealth, 2000.0) && near(c.knightScale, 0.5), "config: knight health and size are clamped");
        expect(c.knightReinforceSeconds == 5 && c.knightExtraWights == 200, "config: knight army settings are clamped");
        expect(c.knightRewardXp == 7, "config: knight xp");
        expect(c.knightRewardDrops.size() == 2 && c.knightRewardDrops.get(1).material().equals("EMERALD"), "config: unreadable knight drops are skipped");
        expect(c.knightPlanSeconds == 1 && c.knightPlanNodes == 500 && c.knightPlanRadius == 128, "config: planning limits are clamped");
        expect(near(c.knightEngageDistance, 4.0), "config: engage distance has a floor");
        expect(c.breachCrew == 1 && near(c.doorStrength, 1.0) && near(c.ironDoorFactor, 1.0), "config: siege numbers are clamped");
        expect(c.stackMaxHeight == 16 && !c.stackEnabled, "config: stack height is clamped and stacking can be turned off");
        expect(!c.stackGlow && c.stackBuildSeconds == 1, "config: the pile's glow can be turned off and its build time has a floor");
        expect(c.bridgeMaterial.equals("BLUE_ICE") && c.bridgeMaxBlocks == 128 && c.bridgeLifetimeSeconds == 10, "config: bridge settings are tidied");
        expect(c.msgKnightBridge.contains("{liquid}"), "config: knight messages are read");
        String line = c.fill("{knight} {walker} {wight}", "Alex", "world", "Somebody");
        expect(line.equals("Ice King Somebody Rimebound"), "config: {walker} stands for whoever leads, {knight} for the knight");
        expect(c.fill("{walker}", "Alex", "world").equals("Rimewalker"), "config: the older three-argument fill still means the walker");

        RimeConfig d = new RimeConfig();
        d.load(new Kit.MapSection());
        expect(near(d.knightChance, 25.0) && near(d.knightHealth, 320.0) && d.knightName.equals("Frost Knight"), "config: defaults when nothing is set");
        expect(d.breachEnabled && d.stackEnabled && d.bridgeEnabled && d.bridgeMaterial.equals("PACKED_ICE"), "config: every trick is on by default");
        expect(d.knightRewardDrops.size() == 4 && d.knightRewardXp == 1000, "config: the knight pays better");
        expect(d.stackGlow && d.stackBuildSeconds == 5 && d.stackPerSecond == 5, "config: the pile glows and takes five seconds to build by default");
    }

    // ------------------------------------------------------------------ the knight appears

    static HordeDirector.StartResult start(Env env, Kit.TestPlayer target, Boolean knight) {
        return env.director.tryStart(WORLD, target, true, knight);
    }

    private static void spawning() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        env.cfg.knightChance = 100.0;
        Horde h = start(env, alex, null).horde();
        expect(h != null && h.knight, "knight: with a 100% chance every horde is led by one");
        Stray k = (Stray) env.walkerOf(h);
        expect("walker".equals(env.director.roleOf(k)), "knight: he is a walker to every rule (dragonglass, fire, death)");
        expect(near(k.getHealth(), 320.0) && near(k.getAttribute(Attribute.MAX_HEALTH).getValue(), 320.0), "knight: 320 health");
        expect(near(k.getAttribute(Attribute.SCALE).getValue(), 1.5), "knight: larger than the walker");
        expect(near(k.getAttribute(Attribute.MOVEMENT_SPEED).getValue(), 0.24), "knight: speed");
        expect(near(k.getAttribute(Attribute.KNOCKBACK_RESISTANCE).getValue(), 0.9), "knight: hard to shove");
        expect(near(k.getAttribute(Attribute.ATTACK_DAMAGE).getValue(), 11.0), "knight: hits harder");
        expect(k.getCustomName().contains("Frost Knight") && !k.getCustomName().contains("Rimewalker"), "knight: his name is shown");
        expect(h.bar != null && h.bar.getTitle().contains("Frost Knight"), "knight: the boss bar names him");
        expect(env.director.leaderName(h).equals("Frost Knight"), "knight: leaderName");
        expect(alex.heard("The Frost Knight is coming for you"), "knight: the target is warned by name");
        expect(alex.heard("and it thinks"), "knight: the announcement is the knight's");
        expect(Env.dispatched("CONFLICT -8 Frost Knight marches on Alex"), "knight: the mood system hears a heavier conflict than a walker's");

        env.director.stopAll();
        env.cfg.knightChance = 0.0;
        Horde plain = start(env, alex, null).horde();
        expect(plain != null && !plain.knight, "knight: with a 0% chance never");
        expect(env.walkerOf(plain).getCustomName().contains("Rimewalker"), "knight: and the plain walker keeps his name");
        expect(env.director.leaderName(plain).equals("Rimewalker"), "knight: leaderName of a plain horde");
        env.director.stopAll();

        Horde forcedKnight = start(env, alex, Boolean.TRUE).horde();
        expect(forcedKnight.knight, "knight: an admin can force one even at 0%");
        env.director.stopAll();
        env.cfg.knightChance = 100.0;
        Horde forcedPlain = start(env, alex, Boolean.FALSE).horde();
        expect(!forcedPlain.knight, "knight: or force a plain walker even at 100%");
        env.director.stopAll();

        // the dice: 25 percent means a draw under 25 is a knight
        Env dice = new Env();
        dice.cfg.knightChance = 25.0;
        Kit.TestPlayer bea = dice.player("Bea", OX, OZ);
        int knights = 0;
        for (int i = 0; i < 40; i++) {
            Horde x = start(dice, bea, null).horde();
            knights += x.knight ? 1 : 0;
            dice.director.stopAll();
        }
        expect(knights > 2 && knights < 25, "knight: at 25% some hordes are his and most are not (" + knights + " of 40)");
    }

    private static void reinforcements() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        env.cfg.wightsBase = 0;
        env.cfg.wightsPerPlayer = 0;
        Horde plain = start(env, alex, Boolean.FALSE).horde();
        Horde knight = null;
        env.director.stopAll();
        knight = start(env, alex, Boolean.TRUE).horde();
        env.tick(11);
        expect(knight.wights.size() == 0, "reinforce: nothing arrives before the knight's twelve seconds");
        env.tick(1);
        expect(knight.wights.size() == 4, "reinforce: the knight raises four after twelve seconds (" + knight.wights.size() + ")");
        env.tick(11);
        expect(knight.wights.size() == 4, "reinforce: and keeps to his own pace for the next batch");
        env.tick(1);
        expect(knight.wights.size() == 8, "reinforce: another four twelve seconds on (" + knight.wights.size() + ")");
        env.director.stopAll();
        Horde p = start(env, alex, Boolean.FALSE).horde();
        env.tick(12);
        expect(p.wights.size() == 0, "reinforce: a plain walker is slower");
        env.tick(8);
        expect(p.wights.size() == 3, "reinforce: and brings three after twenty seconds");
        env.director.stopAll();

        env.cfg.wightsMaxAlive = 5;
        env.cfg.wightsStartMax = 5;
        env.cfg.knightExtraWights = 3;
        env.cfg.reinforceSeconds = 5;
        env.cfg.knightReinforceSeconds = 5;
        Horde k2 = start(env, alex, Boolean.TRUE).horde();
        env.tick(60);
        expect(k2.wights.size() == 8, "reinforce: the knight may keep extra-wights more alive than the normal limit (" + k2.wights.size() + ")");
        env.director.stopAll();
        Horde p2 = start(env, alex, Boolean.FALSE).horde();
        env.tick(60);
        expect(p2.wights.size() == 5, "reinforce: a plain walker stops at the normal limit (" + p2.wights.size() + ")");
        env.director.stopAll();
        expect(HordeScale.reinforcements(4, 6, 8) == 2 && HordeScale.reinforcements(4, 8, 8) == 0 && HordeScale.reinforcements(4, 20, 8) == 0 && HordeScale.reinforcements(4, 0, 8) == 4,
                "reinforce: the batch is cut to fit under the cap");
        RimeConfig c = new RimeConfig();
        expect(HordeScale.reinforcements(c, 39) == 1 && HordeScale.reinforcements(c, 0) == 3, "reinforce: the older two-argument form still works");
        env.cfg.knightNovaCooldownSeconds = 3;
        env.cfg.novaCooldownSeconds = 30;
        env.cfg.knightChance = 0.0;
        Horde nk = start(env, alex, Boolean.TRUE).horde();
        expect(nk.nextNovaTick == 3 * 20, "nova: the knight's first nova comes on his shorter cooldown");
        env.director.stopAll();
        Horde np = start(env, alex, Boolean.FALSE).horde();
        expect(np.nextNovaTick == 30 * 20, "nova: and the walker's on the longer one");
        env.director.stopAll();
    }

    private static void rewards() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", OX, OZ);
        Horde h = start(env, alex, Boolean.TRUE).horde();
        Stray k = (Stray) env.walkerOf(h);
        k.teleport(new Location(WORLD, OX, 64, OZ + 40));
        EntityDeathEvent death = new EntityDeathEvent(k);
        death.getDrops().add(new ItemStack(Material.BONE, 2));
        env.director.onWalkerDeath(h, death, alex);
        int blueIce = 0;
        int diamonds = 0;
        int echo = 0;
        int shards = 0;
        for (ItemStack s : death.getDrops()) {
            if (s.getType() == Material.BLUE_ICE) {
                blueIce += s.getAmount();
            } else if (s.getType() == Material.DIAMOND) {
                diamonds += s.getAmount();
            } else if (s.getType() == Material.ECHO_SHARD) {
                echo += s.getAmount();
            } else if (env.items.isShard(s)) {
                shards += s.getAmount();
            }
        }
        expect(blueIce == 16 && diamonds == 6 && echo == 2 && shards == 6, "knight reward: 16 blue ice, 6 diamond, 2 echo shard, 6 shards");
        expect(death.getDroppedExp() == 1000, "knight reward: 1000 xp");
        expect(alex.heard("Frost Knight has fallen"), "knight reward: the victory message names him");
        expect(env.director.hordes().isEmpty(), "knight reward: the horde ends");
        expect(Env.dispatched("COMMUNITY 9 Frost Knight was slain by Alex"), "knight reward: and the mood system hears a bigger victory");

        Horde p = start(env, alex, Boolean.FALSE).horde();
        EntityDeathEvent death2 = new EntityDeathEvent((Stray) env.walkerOf(p));
        env.director.onWalkerDeath(p, death2, alex);
        expect(death2.getDroppedExp() == 500, "knight reward: a plain walker still pays 500");
    }

    private static void command() {
        Env env = new Env();
        Kit.TestPlayer admin = env.player("Admin", OX, OZ);
        admin.perms.add("nexusrime.admin");
        RimeCommand cmd = new RimeCommand(env.cfg, env.director, env.items, new Env.FakeSeason(), env.realms, () -> env.now[0], () -> { });
        org.bukkit.command.Command c = new org.bukkit.command.Command() {
        };
        cmd.onCommand(admin, c, "rime", new String[] {"start", "knight"});
        expect(env.director.hordes().size() == 1 && env.director.hordes().get(0).knight, "command: /rime start knight forces a Frost Knight on the sender");
        expect(admin.heard("A Frost Knight rises"), "command: and says so");
        env.director.stopAll();
        admin.messages.clear();
        cmd.onCommand(admin, c, "rime", new String[] {"start", "walker"});
        expect(env.director.hordes().size() == 1 && !env.director.hordes().get(0).knight, "command: /rime start walker forces a plain one");
        expect(admin.heard("A Rimewalker rises"), "command: and names it");
        admin.messages.clear();
        cmd.onCommand(admin, c, "rime", new String[] {"status"});
        expect(admin.heard("Rimewalker") && admin.heard("is hunting"), "command: status names the leader");
        env.director.stopAll();
        env.player("Bea", OX + 5, OZ);
        admin.messages.clear();
        cmd.onCommand(admin, c, "rime", new String[] {"start", "Bea", "knight"});
        expect(env.director.hordes().size() == 1 && env.director.hordes().get(0).knight && env.director.hordes().get(0).targetName.equals("Bea"), "command: the player and the kind can be given in either order");
        env.director.stopAll();
        cmd.onCommand(admin, c, "rime", new String[] {"start", "knight", "Bea"});
        expect(env.director.hordes().size() == 1 && env.director.hordes().get(0).targetName.equals("Bea"), "command: ...either order");
        env.director.stopAll();
        admin.messages.clear();
        cmd.onCommand(admin, c, "rime", new String[] {"start", "Nobody"});
        expect(admin.heard("No player named Nobody"), "command: an unknown player is refused");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"start", "k"}).contains("knight"), "command: tab completes knight");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"start", "Bea", "w"}).contains("walker"), "command: and walker after a name");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"start", "Be"}).contains("Bea"), "command: and players");
        Kit.TestPlayer plain = env.player("Plain", OX, OZ + 5);
        plain.messages.clear();
        cmd.onCommand(plain, c, "rime", new String[] {"start", "knight"});
        expect(plain.heard("permission") && env.director.hordes().isEmpty(), "command: ordinary players cannot start one");
        admin.messages.clear();
        cmd.onCommand(admin, c, "rime", new String[] {"status"});
        expect(admin.heard("Frost Knight"), "command: an empty status mentions the knight too");
    }

    // ------------------------------------------------------------------ bridges

    private static void ledger() {
        Env env = new Env();
        long[] clock = {1_000_000L};
        BridgeLedger ledger = new BridgeLedger(null, w -> { }, () -> clock[0]);
        WORLD.stubSetBlock(OX, 63, OZ, Material.WATER);
        WORLD.stubSetBlock(OX + 1, 63, OZ, Material.LAVA);
        WORLD.stubSetBlock(OX + 2, 63, OZ, Material.STONE);
        expect(ledger.place(WORLD, OX, 63, OZ, Material.PACKED_ICE, 60_000L, "a"), "ledger: water freezes");
        expect(ledger.place(WORLD, OX + 1, 63, OZ, Material.PACKED_ICE, 60_000L, "a"), "ledger: lava freezes");
        expect(!ledger.place(WORLD, OX + 2, 63, OZ, Material.PACKED_ICE, 60_000L, "a"), "ledger: stone is left alone");
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.PACKED_ICE && WORLD.getBlockAt(OX + 1, 63, OZ).getType() == Material.PACKED_ICE, "ledger: the blocks are ice");
        expect(WORLD.getBlockAt(OX + 2, 63, OZ).getType() == Material.STONE, "ledger: stone is still stone");
        expect(ledger.size() == 2 && ledger.dirty(), "ledger: two entries, unsaved");
        expect(ledger.isBridge(WORLD, OX, 63, OZ) && !ledger.isBridge(WORLD, OX + 2, 63, OZ), "ledger: knows its own blocks");
        expect(ledger.restoreDue(b -> false) == 0, "ledger: nothing is due yet");
        clock[0] += 30_000L;
        expect(ledger.place(WORLD, OX, 63, OZ, Material.PACKED_ICE, 60_000L, "a"), "ledger: asking again extends a bridge block");
        expect(ledger.size() == 2, "ledger: without adding a second entry");
        clock[0] += 31_000L;
        expect(ledger.restoreDue(b -> false) == 1, "ledger: the older block thaws, the refreshed one does not");
        expect(WORLD.getBlockAt(OX + 1, 63, OZ).getType() == Material.LAVA, "ledger: lava comes back as lava");
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.PACKED_ICE, "ledger: the refreshed block is still ice");
        clock[0] += 40_000L;
        expect(ledger.restoreDue(b -> true) == 0, "ledger: a block someone is standing on is not thawed");
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.PACKED_ICE, "ledger: still ice under them");
        clock[0] += BridgeLedger.GRACE_MILLIS;
        expect(ledger.restoreDue(b -> true) == 1, "ledger: but not forever");
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.WATER && ledger.size() == 0, "ledger: water comes back as water");

        ledger.place(WORLD, OX, 63, OZ, Material.PACKED_ICE, 60_000L, "one");
        ledger.place(WORLD, OX + 1, 63, OZ, Material.PACKED_ICE, 60_000L, "two");
        expect(ledger.restoreHorde("one") == 1, "ledger: one horde's blocks go back, and only its own");
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.WATER && WORLD.getBlockAt(OX + 1, 63, OZ).getType() == Material.PACKED_ICE, "ledger: the other horde's bridge stands");
        WORLD.getBlockAt(OX + 1, 63, OZ).setType(Material.OAK_PLANKS); // someone built on it
        expect(ledger.restoreAll() == 0, "ledger: a block that was built over is not overwritten");
        expect(WORLD.getBlockAt(OX + 1, 63, OZ).getType() == Material.OAK_PLANKS && ledger.size() == 0, "ledger: it is theirs now, and forgotten");

        WORLD.stubSetBlock(OX, 63, OZ, Material.WATER);
        ledger.place(WORLD, OX, 63, OZ, Material.PACKED_ICE, 60_000L, "x");
        WORLD.getBlockAt(OX, 63, OZ).setType(Material.AIR);
        expect(!ledger.place(WORLD, OX, 63, OZ, Material.PACKED_ICE, 60_000L, "x"), "ledger: refreshing a block that was broken fails");
        ledger.restoreAll();
        expect(WORLD.getBlockAt(OX, 63, OZ).getType() == Material.AIR, "ledger: a broken bridge block is not refilled");

        BridgeLedger.Entry e = new BridgeLedger.Entry("world", -5, 63, 12, "LAVA", "PACKED_ICE", 123456789L, "abc");
        BridgeLedger.Entry back = BridgeLedger.Entry.decode(e.encode());
        expect(e.equals(back), "ledger: an entry survives being written and read");
        expect(BridgeLedger.Entry.decode(null) == null && BridgeLedger.Entry.decode("nope") == null && BridgeLedger.Entry.decode("w;1;2;x;A;B;5;h") == null
                && BridgeLedger.Entry.decode("w;1;2;3;A;B;five;h") == null, "ledger: junk in the file is refused");
        ledger.flush();
        expect(ledger.dirty(), "ledger: with no file to write, flush changes nothing and does not fail");
        BridgeLedger gone = new BridgeLedger(new java.io.File("/nonexistent/bridges.yml"), w -> { }, () -> 0L);
        gone.load();
        expect(gone.size() == 0, "ledger: a missing file is an empty ledger");
        WORLD.stubClearBlocks();
        expect(env.director.ledger() != null, "ledger: the director always has one");
    }

    // ------------------------------------------------------------------ reading the world

    private static java.io.File tempFile() {
        try {
            java.io.File f = java.io.File.createTempFile("ledger", ".yml");
            f.deleteOnExit();
            f.delete();
            return f;
        } catch (java.io.IOException cannot) {
            throw new IllegalStateException(cannot);
        }
    }

    private static void ledgerFile() {
        new Env();
        java.io.File file = tempFile();
        List<String> warnings = new ArrayList<>();
        long[] clock = {5_000_000L};
        WORLD.stubSetBlock(OX, 63, OZ + 5, Material.WATER);
        WORLD.stubSetBlock(OX + 1, 63, OZ + 5, Material.LAVA);
        BridgeLedger a = new BridgeLedger(file, warnings::add, () -> clock[0]);
        a.place(WORLD, OX, 63, OZ + 5, Material.PACKED_ICE, 60_000L, "h1");
        a.place(WORLD, OX + 1, 63, OZ + 5, Material.PACKED_ICE, 60_000L, "h2");
        expect(a.dirty() && !file.exists(), "ledger file: nothing is written until asked");
        a.flush();
        expect(file.isFile() && !a.dirty(), "ledger file: flush writes the file and the ledger is clean");

        BridgeLedger b = new BridgeLedger(file, warnings::add, () -> clock[0]);
        b.load();
        expect(b.size() == 2 && b.entries().equals(a.entries()), "ledger file: a restart reads back exactly what was written");
        expect(!b.dirty() && warnings.isEmpty(), "ledger file: and there is nothing to save or complain about");
        expect(b.isBridge(WORLD, OX, 63, OZ + 5) && b.isBridge(WORLD, OX + 1, 63, OZ + 5), "ledger file: the loaded blocks are known bridges");

        file.delete();
        b.flush();
        expect(!file.exists(), "ledger file: a clean ledger writes nothing");

        // a crash left ice behind: the new run thaws it all and records that
        expect(b.restoreAll() == 2, "ledger file: a restart thaws what the last run left");
        expect(WORLD.getBlockAt(OX, 63, OZ + 5).getType() == Material.WATER && WORLD.getBlockAt(OX + 1, 63, OZ + 5).getType() == Material.LAVA,
                "ledger file: water and lava are back");
        expect(b.dirty(), "ledger file: a thaw is a change to save");
        b.flush();
        BridgeLedger empty = new BridgeLedger(file, warnings::add, () -> clock[0]);
        empty.load();
        expect(file.isFile() && empty.size() == 0, "ledger file: and the saved file is empty again");

        // loading replaces what was in memory
        a.flush();
        WORLD.stubSetBlock(OX + 3, 63, OZ + 5, Material.WATER);
        BridgeLedger c = new BridgeLedger(file, warnings::add, () -> clock[0]);
        c.place(WORLD, OX + 3, 63, OZ + 5, Material.PACKED_ICE, 60_000L, "other");
        expect(c.size() == 1, "ledger file: (one block of its own)");
        c.load();
        expect(c.size() == 0, "ledger file: loading replaces what the ledger held (" + c.size() + ")");
        expect(!c.dirty(), "ledger file: and leaves it clean");

        // junk rows are skipped and reported, good ones kept
        try {
            java.nio.file.Files.write(file.toPath(), ("bridges:\n- garbage\n- world;1;2;3;WATER;PACKED_ICE;99;h\n- w;1;2;3;A;B;5;h;extra\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException cannot) {
            throw new IllegalStateException(cannot);
        }
        warnings.clear();
        BridgeLedger d = new BridgeLedger(file, warnings::add, () -> clock[0]);
        d.load();
        expect(d.size() == 1 && d.entries().get(0).x() == 1 && d.entries().get(0).horde().equals("h"), "ledger file: a damaged row is skipped, the good one kept (" + d.size() + ")");
        expect(warnings.size() == 2 && warnings.get(0).contains("garbage") && warnings.get(1).contains("extra"), "ledger file: each damaged row is reported (" + warnings + ")");
        expect(BridgeLedger.Entry.decode("w;1;2;3;A;B;5;h;extra") == null, "ledger file: a row with too many fields is refused");
        expect(BridgeLedger.Entry.decode("w;1;2;3;A;B;5") == null, "ledger file: and one with too few");

        // a world that no longer exists is simply forgotten
        try {
            java.nio.file.Files.write(file.toPath(), ("bridges:\n- ghost;1;2;3;WATER;PACKED_ICE;99;h\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException cannot) {
            throw new IllegalStateException(cannot);
        }
        BridgeLedger g = new BridgeLedger(file, warnings::add, () -> clock[0]);
        g.load();
        expect(g.size() == 1 && g.restoreAll() == 0 && g.size() == 0, "ledger file: ice in a world that is gone is forgotten without a fuss");

        // the same coordinates in two worlds, or at two heights, are different blocks
        World other = new World("other");
        Bukkit.WORLDS.put("other", other);
        WORLD.stubSetBlock(OX + 9, 63, OZ + 5, Material.WATER);
        WORLD.stubSetBlock(OX + 9, 62, OZ + 5, Material.WATER);
        other.stubSetBlock(OX + 9, 63, OZ + 5, Material.LAVA);
        BridgeLedger t = new BridgeLedger(null, warnings::add, () -> clock[0]);
        expect(t.place(WORLD, OX + 9, 63, OZ + 5, Material.PACKED_ICE, 60_000L, "h"), "ledger: ice in one world");
        expect(t.place(other, OX + 9, 63, OZ + 5, Material.PACKED_ICE, 60_000L, "h"), "ledger: and at the same spot in another");
        expect(t.place(WORLD, OX + 9, 62, OZ + 5, Material.PACKED_ICE, 60_000L, "h"), "ledger: and one block lower");
        expect(t.size() == 3 && t.isBridge(other, OX + 9, 63, OZ + 5) && t.isBridge(WORLD, OX + 9, 62, OZ + 5), "ledger: three separate entries");
        expect(t.restoreAll() == 3 && other.getBlockAt(OX + 9, 63, OZ + 5).getType() == Material.LAVA
                && WORLD.getBlockAt(OX + 9, 63, OZ + 5).getType() == Material.WATER, "ledger: each goes back to what it was, in its own world");
        Bukkit.WORLDS.remove("other");
    }

    private static void ledgerTiming() {
        new Env();
        long[] clock = {1_000_000L};
        BridgeLedger l = new BridgeLedger(null, w -> { }, () -> clock[0]);
        WORLD.stubSetBlock(OX, 63, OZ + 8, Material.WATER);
        l.place(WORLD, OX, 63, OZ + 8, Material.PACKED_ICE, 10_000L, "h");
        long expires = 1_010_000L;
        clock[0] = expires - 1;
        expect(l.restoreDue(b -> false) == 0, "ledger timing: a millisecond early is not yet");
        clock[0] = expires;
        expect(l.restoreDue(b -> true) == 0, "ledger timing: at the moment it is due, with someone on it, it waits");
        expect(l.size() == 1 && WORLD.getBlockAt(OX, 63, OZ + 8).getType() == Material.PACKED_ICE, "ledger timing: still ice");
        clock[0] = expires + BridgeLedger.GRACE_MILLIS - 1;
        expect(l.restoreDue(b -> true) == 0, "ledger timing: the wait lasts the whole grace period");
        clock[0] = expires + BridgeLedger.GRACE_MILLIS;
        expect(l.restoreDue(b -> true) == 1, "ledger timing: and ends the moment it is over");
        expect(WORLD.getBlockAt(OX, 63, OZ + 8).getType() == Material.WATER, "ledger timing: water again");

        l.place(WORLD, OX, 63, OZ + 8, Material.PACKED_ICE, 10_000L, "h");
        clock[0] += 10_000L;
        expect(l.restoreDue(b -> false) == 1, "ledger timing: with nobody on it, it thaws exactly when due");
    }

    private static void terrain() {
        Env env = new Env();
        WORLD.stubProvider = (x, y, z) -> y <= 63 ? Material.STONE : Material.AIR;
        WorldTerrain t = new WorldTerrain(WORLD, env.zones);
        expect(t.at(OX, 63, OZ) == Cell.SOLID && t.at(OX, 64, OZ) == Cell.AIR, "terrain: ground is solid, sky is air");
        expect(t.at(OX, -100, OZ) == Cell.SOLID && t.at(OX, 500, OZ) == Cell.AIR, "terrain: below the world is solid, above is air");
        WORLD.stubSetBlock(OX + 1, 64, OZ, Material.OAK_DOOR);
        WORLD.stubSetBlock(OX + 2, 63, OZ, Material.WATER);
        WORLD.stubSetBlock(OX + 3, 63, OZ, Material.LAVA);
        WORLD.stubSetBlock(OX + 4, 64, OZ, Material.FIRE);
        WORLD.stubSetBlock(OX + 5, 64, OZ, Material.FERN);
        WORLD.stubSetBlock(OX + 6, 64, OZ, Material.KELP);
        WorldTerrain fresh = new WorldTerrain(WORLD, env.zones);
        expect(fresh.at(OX + 1, 64, OZ) == Cell.DOOR, "terrain: a door");
        expect(fresh.at(OX + 2, 63, OZ) == Cell.WATER && fresh.at(OX + 3, 63, OZ) == Cell.LAVA, "terrain: still water and lava");
        expect(fresh.at(OX + 4, 64, OZ) == Cell.HAZARD && fresh.at(OX + 5, 64, OZ) == Cell.AIR && fresh.at(OX + 6, 64, OZ) == Cell.FLOWING_WATER, "terrain: fire, ferns, kelp");
        WORLD.stubSetBlock(OX + 1, 64, OZ, Material.AIR);
        expect(fresh.at(OX + 1, 64, OZ) == Cell.DOOR, "terrain: a plan reads each block once");
        expect(new WorldTerrain(WORLD, env.zones).at(OX + 1, 64, OZ) == Cell.AIR, "terrain: and a new plan reads the new world");
        WORLD.stubSetBlock(OX + 1, 64, OZ, Material.IRON_DOOR);
        env.realms.claimed = l -> l.getX() > OX + 0.9 && l.getX() < OX + 2;
        env.cfg.protectedWorlds.clear();
        expect(new WorldTerrain(WORLD, env.zones).at(OX + 1, 64, OZ) == Cell.SOLID, "terrain: a door on claimed land is a wall");
        env.realms.claimed = l -> false;
        expect(new WorldTerrain(WORLD, env.zones).at(OX + 1, 64, OZ) == Cell.DOOR, "terrain: and a door elsewhere is a door");
        WORLD.chunkLoaded = (cx, cz) -> false;
        expect(new WorldTerrain(WORLD, env.zones).at(OX, 64, OZ) == Cell.SOLID, "terrain: unloaded land is treated as a wall");
        WORLD.chunkLoaded = null;
    }
}
