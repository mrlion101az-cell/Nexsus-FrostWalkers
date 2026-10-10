package com.nexuscraft.nexusrime;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.nexuscraft.nexusrime.Kit.WORLD;
import static com.nexuscraft.nexusrime.Kit.expect;
import static com.nexuscraft.nexusrime.Kit.near;

/** Dragonglass: the shard, the gear made from it, how it is crafted, and how it hits. */
public final class GearTest {

    public static void main(String[] args) {
        specs();
        configLoading();
        items();
        recipes();
        craftGuard();
        crafting();
        tagging();
        damage();
        rewards();
        command();
        Kit.finish();
    }

    private static EntityDamageEvent hit(Entity victim, Entity damager, DamageCause cause, double damage) {
        EntityDamageEvent e = new EntityDamageByEntityEvent(damager, victim, cause);
        e.setDamage(damage);
        return e;
    }

    // ------------------------------------------------------------------ recipe descriptions

    private static void specs() {
        RecipeSpec ok = RecipeSpec.shaped(List.of(" S ", "STS"), Map.of('S', "DRAGONGLASS", 'T', "STICK"), 1);
        expect(ok.valid() && ok.isShaped() && ok.usesShard(), "spec: a normal shaped recipe is valid and uses the shard");
        expect(ok.usedIngredients().containsAll(List.of("DRAGONGLASS", "STICK")), "spec: used ingredients are listed");
        expect(!RecipeSpec.shaped(List.of("SS", "S"), Map.of('S', "STICK"), 1).valid(), "spec: a ragged shape is invalid");
        expect(!RecipeSpec.shaped(List.of("S", "S", "S", "S"), Map.of('S', "STICK"), 1).valid(), "spec: four rows is invalid");
        expect(!RecipeSpec.shaped(List.of("SSSS"), Map.of('S', "STICK"), 1).valid(), "spec: four columns is invalid");
        expect(!RecipeSpec.shaped(List.of("ST"), Map.of('S', "STICK"), 1).valid(), "spec: a letter with no ingredient is invalid");
        expect(!RecipeSpec.shaped(List.of("   "), Map.of('S', "STICK"), 1).valid(), "spec: an empty grid is invalid");
        expect(!RecipeSpec.shaped(List.of("S"), Map.of('S', "STICK"), 0).valid() && !RecipeSpec.shaped(List.of("S"), Map.of('S', "STICK"), 65).valid(),
                "spec: amount must be 1-64");
        RecipeSpec loose = RecipeSpec.shapeless(List.of("OBSIDIAN"), 2);
        expect(loose.valid() && !loose.isShaped() && !loose.usesShard(), "spec: a shapeless recipe is valid");
        expect(!RecipeSpec.shapeless(List.of(), 1).valid(), "spec: no ingredients is invalid");
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("STICK");
        }
        expect(!RecipeSpec.shapeless(ten, 1).valid(), "spec: ten ingredients is too many");
        expect(ok.withAmount(4).amount == 4 && ok.withAmount(4).shape.equals(ok.shape), "spec: withAmount keeps the rest");
    }

    // ------------------------------------------------------------------ config

    private static void configLoading() {
        RimeConfig d = new RimeConfig();
        expect(d.gear.keySet().equals(new java.util.LinkedHashSet<>(RimeConfig.GEAR_IDS)), "config: every piece of gear has an entry");
        expect(d.gear.get("shard").recipe.amount == 2 && !d.gear.get("shard").recipe.isShaped(), "config: the shard is a shapeless recipe making two");
        expect(d.gear.get("arrow").recipe.amount == 4 && d.gear.get("arrow").recipe.shape.equals(List.of("STF")), "config: the arrow recipe is a single row");
        expect(near(d.potency("blade"), 1.0) && d.potency("dagger") < 1.0 && d.potency("spear") < 1.0 && d.potency("arrow") < d.potency("dagger"), "config: potencies are ordered");
        expect(d.potency("nonsense") == 1.0 && d.materialOf("nonsense").equals("AIR") && d.nameOf("nonsense").equals("nonsense"), "config: unknown ids are harmless");

        Kit.MapSection s = new Kit.MapSection();
        s.put("dragonglass.recipe.amount", 3);
        s.put("dragonglass.recipe.ingredients", List.of("crying_obsidian", "flint"));
        s.put("dagger.recipe-enabled", false);
        s.put("dagger.potency", 0.01);
        s.put("spear.recipe.shape", List.of("S", "T"));
        s.put("spear.recipe.ingredients.S", "dragonglass");
        s.put("spear.recipe.ingredients.T", "blaze_rod");
        s.put("arrow.recipe.shape", List.of("SSSS"));
        s.put("arrow.recipe.ingredients.S", "dragonglass");
        s.put("names.shard", "Black Glass");
        s.put("names.spear", "Glass Spear");
        RimeConfig c = new RimeConfig();
        c.load(s);
        expect(c.gear.get("shard").recipe.amount == 3 && c.gear.get("shard").recipe.loose.equals(List.of("CRYING_OBSIDIAN", "FLINT")), "config: the shard recipe is replaceable");
        expect(!c.gear.get("dagger").recipeEnabled, "config: a recipe can be switched off");
        expect(near(c.potency("dagger"), 0.1), "config: potency has a floor");
        expect(c.gear.get("spear").recipe.shape.equals(List.of("S", "T")) && c.gear.get("spear").recipe.ingredients.get('S').equals("DRAGONGLASS")
                && c.gear.get("spear").recipe.ingredients.get('T').equals("BLAZE_ROD"), "config: a shaped recipe is replaceable and upper-cased");
        expect(c.gear.get("arrow").recipe.shape.equals(List.of("STF")) && c.gear.get("arrow").recipe.amount == 4, "config: a recipe that does not fit falls back to the default");
        expect(c.shardName.equals("Black Glass") && c.nameOf("spear").equals("Glass Spear") && c.nameOf("shard").equals("Black Glass"), "config: names are replaceable");

        RimeConfig again = new RimeConfig();
        again.load(s);
        again.load(new Kit.MapSection());
        expect(again.gear.get("dagger").recipeEnabled && again.gear.get("shard").recipe.amount == 2, "config: a reload with nothing set returns to the defaults");
    }

    // ------------------------------------------------------------------ the items

    private static void items() {
        Env env = new Env();
        for (String id : RimeConfig.GEAR_IDS) {
            ItemStack item = env.items.make(id, 3);
            expect(item != null && item.getAmount() == 3 && id.equals(env.items.kind(item)), "items: " + id + " is made, counted and tagged");
            expect(item.getItemMeta().getDisplayName().contains(env.cfg.nameOf(id)), "items: " + id + " carries its configured name");
            expect(!item.getItemMeta().getLore().isEmpty(), "items: " + id + " has lore");
        }
        expect(env.items.make("nonsense", 1) == null, "items: an unknown id makes nothing");
        expect(env.items.make("shard", 0).getAmount() == 1, "items: at least one is made");
        expect(env.items.shard(1).getType() == Material.FLINT && env.items.dagger(1).getType() == Material.IRON_SWORD
                && env.items.spear(1).getType() == Material.TRIDENT && env.items.arrow(1).getType() == Material.ARROW
                && env.items.blade(1).getType() == Material.DIAMOND_SWORD, "items: default materials");
        expect(env.items.isShard(env.items.shard(1)) && !env.items.isShard(env.items.blade(1)) && !env.items.isShard(new ItemStack(Material.FLINT)),
                "items: a plain flint is not a shard");
        expect(env.items.isForgedWeapon(env.items.blade(1)) && env.items.isForgedWeapon(env.items.dagger(1)) && env.items.isForgedWeapon(env.items.spear(1))
                && !env.items.isForgedWeapon(env.items.shard(1)) && !env.items.isForgedWeapon(env.items.arrow(1)) && !env.items.isForgedWeapon(null),
                "items: blade, dagger and spear are weapons; shard and arrow are not");
        expect(env.items.isForgedArrow(env.items.arrow(1)) && !env.items.isForgedArrow(new ItemStack(Material.ARROW)) && !env.items.isForgedArrow(null), "items: forged arrows");
        ItemStack bogus = new ItemStack(Material.FLINT);
        org.bukkit.inventory.meta.ItemMeta m = bogus.getItemMeta();
        m.getPersistentDataContainer().set(env.keys.itemKind, PersistentDataType.STRING, "excalibur");
        bogus.setItemMeta(m);
        expect(env.items.kind(bogus) == null, "items: an unknown tag is not gear");
        env.cfg.gear.get("shard").material = "BANANA";
        expect(env.items.shard(1).getType() == Material.FLINT, "items: an unknown material falls back to the default");
        env.cfg.gear.get("spear").material = "NETHERITE_SWORD";
        expect(env.items.spear(1).getType() == Material.NETHERITE_SWORD, "items: materials are configurable");
    }

    // ------------------------------------------------------------------ the recipes

    private static void recipes() {
        Env env = new Env();
        List<String> warnings = new ArrayList<>();
        List<Recipe> all = RecipeBook.build(env.cfg, env.items, env.keys, warnings::add);
        expect(all.size() == 5 && warnings.isEmpty(), "recipes: all five are built without complaint");
        Recipe shard = all.get(0);
        expect(shard instanceof ShapelessRecipe && env.items.isShard(shard.getResult()) && shard.getResult().getAmount() == 2, "recipes: obsidian makes two shards");
        expect(((ShapelessRecipe) shard).getKey().equals(env.keys.recipe("shard")), "recipes: the shard recipe has its own key");
        ShapelessRecipe sr = (ShapelessRecipe) shard;
        RecipeChoice obs = sr.getChoiceList().get(0);
        expect(obs.test(new ItemStack(Material.OBSIDIAN)) && !obs.test(new ItemStack(Material.STONE)), "recipes: the shard recipe wants obsidian");

        ShapedRecipe blade = (ShapedRecipe) all.get(1);
        RecipeChoice s = blade.getChoiceMap().get('S');
        expect(s instanceof RecipeChoice.ExactChoice && s.test(env.items.shard(1)) && !s.test(new ItemStack(Material.FLINT)),
                "recipes: the shard slot accepts only a real shard, not plain flint");
        expect(blade.getChoiceMap().get('D').test(new ItemStack(Material.DIAMOND)), "recipes: the other slots are ordinary");
        expect(env.items.isForgedWeapon(blade.getResult()) && env.items.isBlade(blade.getResult()), "recipes: the blade recipe makes the blade");
        ShapedRecipe arrow = (ShapedRecipe) all.get(4);
        expect(arrow.getResult().getAmount() == 4 && env.items.isForgedArrow(arrow.getResult()) && arrow.getShape().length == 1, "recipes: four dragonglass arrows from one row");
        for (Recipe r : all) {
            Bukkit.addRecipe(r);
        }
        expect(Bukkit.getRecipe(env.keys.recipe("dagger")) != null, "recipes: they register under our keys");

        // A recipe that wants plain flint must refuse a shard.
        Env flint = new Env();
        flint.cfg.gear.get("dagger").recipe = RecipeSpec.shaped(List.of("F", "T"), Map.of('F', "FLINT", 'T', "STICK"), 1);
        ShapedRecipe viaFlint = (ShapedRecipe) RecipeBook.build(flint.cfg, flint.items, flint.keys, w -> { }).get(2);
        RecipeChoice f = viaFlint.getChoiceMap().get('F');
        expect(f.test(new ItemStack(Material.FLINT)) && !f.test(flint.items.shard(1)), "recipes: where plain flint is asked for, a shard is refused");
        flint.cfg.gear.get("shard").material = "STICK";
        expect(RecipeBook.choice(flint.cfg, flint.items, "STICK") instanceof RecipeChoice.ExactChoice, "recipes: a plain item of the shard's base stays exact whichever base is chosen");

        // Disabled, broken and unknown-material recipes
        Env bad = new Env();
        bad.cfg.gear.get("spear").recipeEnabled = false;
        bad.cfg.gear.get("dagger").recipe = RecipeSpec.shaped(List.of("S", "T"), Map.of('S', "DRAGONGLASS", 'T', "BANANA"), 1);
        bad.cfg.gear.get("arrow").recipe = RecipeSpec.shaped(List.of("SS", "S"), Map.of('S', "DRAGONGLASS"), 1);
        List<String> warn = new ArrayList<>();
        List<Recipe> some = RecipeBook.build(bad.cfg, bad.items, bad.keys, warn::add);
        expect(some.size() == 2, "recipes: disabled, unknown-material and invalid recipes are skipped");
        expect(warn.size() == 2 && warn.stream().anyMatch(w -> w.contains("BANANA")) && warn.stream().anyMatch(w -> w.contains("arrow")), "recipes: each problem is reported");
        bad.cfg.gear.get("shard").recipe = RecipeSpec.shapeless(List.of("BANANA"), 1);
        expect(RecipeBook.build(bad.cfg, bad.items, bad.keys, w -> { }).size() == 1, "recipes: a shapeless recipe with an unknown material is skipped");
        expect(RecipeBook.choice(bad.cfg, bad.items, "AIR") == null && RecipeBook.choice(bad.cfg, bad.items, "nonsense") == null, "recipes: air and nonsense are not ingredients");
        expect(RecipeBook.choice(bad.cfg, bad.items, "dragonglass") instanceof RecipeChoice.ExactChoice, "recipes: DRAGONGLASS in any case means a shard");

        expect(env.keys.isOurRecipe(env.keys.recipe("blade")) && !env.keys.isOurRecipe(new NamespacedKey(env.plugin, "other")) && !env.keys.isOurRecipe(null),
                "keys: only our own recipe keys count");
        expect("blade".equals(env.keys.recipeId(env.keys.recipe("blade"))) && env.keys.recipeId(new NamespacedKey(env.plugin, "other")) == null, "keys: a key maps back to its piece");
    }

    // ------------------------------------------------------------------ the shard guard

    private static void craftGuard() {
        expect(!CraftGuard.blocked(false, false, false), "guard: no shard, vanilla recipe: fine");
        expect(!CraftGuard.blocked(false, true, true), "guard: no shard, our recipe: fine");
        expect(CraftGuard.blocked(true, false, false), "guard: shard in a vanilla recipe: refused");
        expect(!CraftGuard.blocked(true, true, false), "guard: shard in our gear recipe: fine");
        expect(CraftGuard.blocked(true, true, true), "guard: shard fed to the shard recipe: refused");
    }

    private static PrepareItemCraftEvent grid(ItemStack[] matrix, Recipe recipe) {
        CraftingInventory inv = new CraftingInventory();
        inv.setMatrix(matrix);
        inv.setRecipe(recipe);
        inv.setResult(new ItemStack(Material.STICK));
        return new PrepareItemCraftEvent(inv, new InventoryView(), false);
    }

    private static void crafting() {
        Env env = new Env();
        List<Recipe> all = RecipeBook.build(env.cfg, env.items, env.keys, w -> { });
        ItemStack shard = env.items.shard(1);

        PrepareItemCraftEvent e = grid(new ItemStack[] {null, shard, null, shard, new ItemStack(Material.DIAMOND), shard, null, new ItemStack(Material.STICK), null}, all.get(1));
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() != null, "crafting: shards in the blade recipe are fine");

        ShapedRecipe vanilla = new ShapedRecipe(new NamespacedKey(env.plugin, "vanilla_flint_thing"), new ItemStack(Material.ARROW));
        e = grid(new ItemStack[] {shard, new ItemStack(Material.STICK), new ItemStack(Material.FEATHER)}, vanilla);
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() == null, "crafting: a shard in a vanilla recipe produces nothing");

        e = grid(new ItemStack[] {shard}, all.get(0));
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() == null, "crafting: a shard cannot be crafted into more shards");

        e = grid(new ItemStack[] {new ItemStack(Material.OBSIDIAN)}, all.get(0));
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() != null, "crafting: obsidian makes shards as normal");

        e = grid(new ItemStack[] {new ItemStack(Material.FLINT), new ItemStack(Material.STICK), new ItemStack(Material.FEATHER)}, vanilla);
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() != null, "crafting: plain flint still works in vanilla recipes");

        e = grid(new ItemStack[] {shard, new ItemStack(Material.STICK)}, null);
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() == null, "crafting: a shard in a grid matching no recipe has no result");

        e = grid(new ItemStack[] {null, null, null}, null);
        env.listener.onPrepareCraft(e);
        expect(e.getInventory().getResult() != null, "crafting: an empty grid is left alone");
    }

    // ------------------------------------------------------------------ marking projectiles

    private static void tagging() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);

        Arrow forged = new Arrow(alex);
        EntityShootBowEvent shot = new EntityShootBowEvent(alex, new ItemStack(Material.CROSSBOW), env.items.arrow(1), forged, 1.0f);
        env.listener.onShoot(shot);
        expect("arrow".equals(forged.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING)), "shoot: an arrow fired from dragonglass ammunition is marked");

        Arrow plain = new Arrow(alex);
        env.listener.onShoot(new EntityShootBowEvent(alex, new ItemStack(Material.CROSSBOW), new ItemStack(Material.ARROW), plain, 1.0f));
        expect(plain.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "shoot: an ordinary arrow is not");
        Arrow noAmmo = new Arrow(alex);
        env.listener.onShoot(new EntityShootBowEvent(alex, new ItemStack(Material.BOW), null, noAmmo, 1.0f));
        expect(noAmmo.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "shoot: no ammunition is no mark");
        Projectile odd = new Projectile(alex);
        env.listener.onShoot(new EntityShootBowEvent(alex, new ItemStack(Material.BOW), env.items.arrow(1), odd, 1.0f));
        expect(odd.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "shoot: only arrows are marked");

        Trident thrown = new Trident(alex);
        alex.getInventory().setItemInMainHand(env.items.spear(1));
        env.listener.onLaunch(new ProjectileLaunchEvent(thrown));
        expect("spear".equals(thrown.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING)), "launch: a trident thrown with the dragonglass spear is the spear");

        Trident offhand = new Trident(alex);
        alex.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        alex.getInventory().setItemInOffHand(env.items.spear(1));
        env.listener.onLaunch(new ProjectileLaunchEvent(offhand));
        expect("spear".equals(offhand.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING)), "launch: thrown from the off hand counts too");

        Trident ordinary = new Trident(alex);
        alex.getInventory().setItemInOffHand(null);
        alex.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        env.listener.onLaunch(new ProjectileLaunchEvent(ordinary));
        expect(ordinary.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "launch: an ordinary trident is not marked");

        Trident sword = new Trident(alex);
        alex.getInventory().setItemInMainHand(env.items.blade(1));
        env.listener.onLaunch(new ProjectileLaunchEvent(sword));
        expect(sword.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "launch: only the spear marks a trident");

        alex.getInventory().setItemInMainHand(env.items.spear(1));
        Arrow notTrident = new Arrow(alex);
        env.listener.onLaunch(new ProjectileLaunchEvent(notTrident));
        expect(notTrident.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "launch: an arrow launched near a spear is not the spear");
        Trident mobThrown = new Trident(WORLD.spawn(new org.bukkit.Location(WORLD, 1, 64, 1), Zombie.class));
        env.listener.onLaunch(new ProjectileLaunchEvent(mobThrown));
        expect(mobThrown.getPersistentDataContainer().get(env.keys.projectileKind, PersistentDataType.STRING) == null, "launch: a trident thrown by a mob is not the spear");
    }

    // ------------------------------------------------------------------ how hard each piece hits

    private static void damage() {
        Env env = new Env();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = env.director.tryStart(WORLD, alex, true).horde();
        Stray walker = (Stray) env.walkerOf(h);
        Zombie wight = (Zombie) Bukkit.getEntity(h.wights.iterator().next());

        alex.getInventory().setItemInMainHand(env.items.dagger(1));
        EntityDamageEvent e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 7.0 * 2.0 * 0.7), "damage: the dagger does 70% of the blade to the walker");
        e = hit(wight, alex, DamageCause.ENTITY_ATTACK, 4.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 24.0), "damage: but shatters a wight like the blade does");

        alex.getInventory().setItemInMainHand(env.items.spear(1));
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 7.0 * 2.0 * 0.75), "damage: the spear in melee");

        alex.getInventory().setItemInMainHand(env.items.blade(1));
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 7.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 14.0), "damage: the blade is unchanged");

        alex.getInventory().setItemInMainHand(env.items.shard(1));
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.05), "damage: punching with a shard is nothing special");
        alex.getInventory().setItemInMainHand(env.items.arrow(1));
        e = hit(walker, alex, DamageCause.ENTITY_ATTACK, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.05), "damage: nor is stabbing with an arrow");

        alex.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
        Arrow forged = new Arrow(alex);
        forged.getPersistentDataContainer().set(env.keys.projectileKind, PersistentDataType.STRING, "arrow");
        e = hit(walker, forged, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 6.0 * 2.0 * 0.6), "damage: a dragonglass arrow wounds the walker");
        e = hit(wight, forged, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 36.0), "damage: and shatters a wight");

        Trident spear = new Trident(alex);
        spear.getPersistentDataContainer().set(env.keys.projectileKind, PersistentDataType.STRING, "spear");
        e = hit(walker, spear, DamageCause.PROJECTILE, 8.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 8.0 * 2.0 * 0.75), "damage: a thrown spear wounds the walker");

        Arrow plain = new Arrow(alex);
        e = hit(walker, plain, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.3), "damage: an ordinary arrow is still only a scratch");

        Arrow lying = new Arrow(alex);
        lying.getPersistentDataContainer().set(env.keys.projectileKind, PersistentDataType.STRING, "blade");
        e = hit(walker, lying, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.3), "damage: a projectile marked with anything but arrow or spear is ordinary");

        // The mark only counts as a shot, never as a swing.
        e = hit(walker, forged, DamageCause.ENTITY_ATTACK, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 0.3), "damage: a marked projectile that somehow melees is ordinary");

        env.cfg.gear.get("arrow").potency = 3.0;
        e = hit(walker, forged, DamageCause.PROJECTILE, 6.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 36.0), "damage: potency is read live from the config");

        // Fire is still fire, even holding a shard.
        e = hit(walker, null, DamageCause.FIRE_TICK, 1.0);
        env.listener.onDamage(e);
        expect(near(e.getDamage(), 3.0), "damage: fire is unaffected");
        expect(DamageRules.walkerDamage(env.cfg, DamageKind.FORGED, 10, 0.5) == 10.0, "rules: potency 0.5 halves the forged multiplier");
        expect(DamageRules.walkerDamage(env.cfg, DamageKind.FORGED, 10, -1) == 0.0, "rules: negative potency never heals");
        expect(DamageRules.walkerDamage(env.cfg, DamageKind.FORGED, 10) == 20.0, "rules: the old signature means potency 1");
        expect(DamageRules.classify(DamageCause.PROJECTILE, true, false) == DamageKind.ORDINARY && DamageRules.classify(DamageCause.PROJECTILE, false, true) == DamageKind.FORGED,
                "rules: a shot is forged only if the projectile was");
        expect(DamageRules.classify(DamageCause.ENTITY_ATTACK, true, true) == DamageKind.FORGED && DamageRules.classify(DamageCause.PROJECTILE, true) == DamageKind.ORDINARY,
                "rules: the two-argument form never counts a shot");
    }

    // ------------------------------------------------------------------ the walker's reward

    private static void rewards() {
        Env env = new Env();
        env.cfg.rewardDrops = new ArrayList<>(List.of(new RimeConfig.Drop("DRAGONGLASS", 5), new RimeConfig.Drop("DIAMOND", 1)));
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Horde h = env.director.tryStart(WORLD, alex, true).horde();
        Stray walker = (Stray) env.walkerOf(h);
        EntityDeathEvent death = new EntityDeathEvent(walker);
        env.listener.onDeath(death);
        boolean shards = false;
        boolean diamond = false;
        for (ItemStack s : death.getDrops()) {
            shards |= env.items.isShard(s) && s.getAmount() == 5;
            diamond |= s.getType() == Material.DIAMOND && !env.items.isShard(s);
        }
        expect(shards && diamond, "rewards: a DRAGONGLASS drop pays out real shards, others as normal");
    }

    // ------------------------------------------------------------------ /rime give and /rime recipe

    private static void command() {
        Env env = new Env();
        RimeCommand cmd = new RimeCommand(env.cfg, env.director, env.items, env.season, env.realms, () -> env.now[0], () -> { });
        Command c = new Command();
        Kit.TestPlayer alex = env.player("Alex", 600, 600);
        Kit.TestPlayer admin = env.player("Admin", 640, 600);
        admin.perms.add("nexusrime.admin");

        for (String id : RimeConfig.GEAR_IDS) {
            cmd.onCommand(alex, c, "rime", new String[] {"recipe", id});
            expect(alex.heard(env.cfg.nameOf(id)), "command: recipe " + id + " shows the item");
        }
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "shard"});
        expect(alex.heard("Anywhere in the grid") && alex.heard("Obsidian") && alex.heard("makes 2"), "command: the shard recipe is shapeless and makes two");
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "arrow"});
        expect(alex.heard("[Dragonglass Shard] [Stick] [Feather]") && alex.heard("makes 4"), "command: the arrow recipe is drawn as a row");
        int before = alex.messages.size();
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "dragonglass"});
        expect(alex.messages.size() > before && alex.messages.get(before).contains("Dragonglass Shard") && alex.messages.get(before).contains("makes 2"), "command: 'dragonglass' means the shard");
        before = alex.messages.size();
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "sword"});
        expect(alex.messages.size() > before && alex.messages.get(before).contains("Dragonglass Blade"), "command: 'sword' means the blade");
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "banana"});
        expect(alex.heard("No such item"), "command: an unknown item is explained");
        env.cfg.gear.get("dagger").recipeEnabled = false;
        cmd.onCommand(alex, c, "rime", new String[] {"recipe", "dagger"});
        expect(alex.heard("cannot be crafted"), "command: a switched-off recipe is explained");
        cmd.onCommand(alex, c, "rime", new String[] {"recipe"});
        expect(alex.heard("no recipe on this server"), "command: the list marks pieces without a recipe");

        cmd.onCommand(admin, c, "rime", new String[] {"give", "Alex", "arrow", "16"});
        int arrows = 0;
        for (ItemStack s : alex.getInventory().getContents()) {
            if (s != null && env.items.isForgedArrow(s)) {
                arrows += s.getAmount();
            }
        }
        expect(arrows == 16 && admin.heard("Gave 16 x Dragonglass Arrow to Alex"), "command: give takes an item and an amount");
        cmd.onCommand(admin, c, "rime", new String[] {"give", "shard", "Alex", "99"});
        int shards = 0;
        for (ItemStack s : alex.getInventory().getContents()) {
            if (s != null && env.items.isShard(s)) {
                shards += s.getAmount();
            }
        }
        expect(shards == 64, "command: any order works, and the amount is capped at a stack");
        cmd.onCommand(admin, c, "rime", new String[] {"give", "spear"});
        boolean spear = false;
        for (ItemStack s : admin.getInventory().getContents()) {
            spear |= s != null && RimeItems.SPEAR.equals(env.items.kind(s));
        }
        expect(spear, "command: give with no name gives to the sender");
        cmd.onCommand(admin, c, "rime", new String[] {"give", "Nobody", "dagger"});
        expect(admin.heard("No player named Nobody"), "command: an unknown player is named");

        expect(cmd.onTabComplete(alex, c, "rime", new String[] {"recipe", "s"}).equals(List.of("shard", "spear")), "tab: recipe completes item names");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"give", "ar"}).equals(List.of("arrow")), "tab: give completes item names");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"give", "al"}).equals(List.of("Alex")), "tab: and player names");
        expect(cmd.onTabComplete(admin, c, "rime", new String[] {"give", "Alex", "d"}).equals(List.of("dagger")), "tab: items after the name, without more names");
        expect(cmd.onTabComplete(alex, c, "rime", new String[] {"give", "ar"}).isEmpty(), "tab: players get no give completions");
    }
}
