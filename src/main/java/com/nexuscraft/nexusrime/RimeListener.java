package com.nexuscraft.nexusrime;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Everything the world tells us about: damage (where the walker's weakness lives), deaths, targeting
 * and creatures loading in from disk. The decisions themselves are made by the pure rule classes.
 */
final class RimeListener implements Listener {

    private final RimeConfig cfg;
    private final HordeDirector director;
    private final RimeItems items;
    private final ZoneGuard zones;
    private final Keys keys;

    RimeListener(RimeConfig cfg, HordeDirector director, RimeItems items, ZoneGuard zones, Keys keys) {
        this.cfg = cfg;
        this.director = director;
        this.items = items;
        this.zones = zones;
        this.keys = keys;
    }

    /** Runs late so other plugins' changes are in before the walker's weakness is applied. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        String role = director.roleOf(victim);
        if (role != null) {
            hurtCreature(event, role);
            return;
        }
        if (victim instanceof Player player && event instanceof EntityDamageByEntityEvent byEntity) {
            Entity source = byEntity.getDamager();
            if (source instanceof Projectile shot && shot.getShooter() instanceof Entity shooter) {
                source = shooter;
            }
            if (director.roleOf(source) == null) {
                return;
            }
            // The cold cannot hurt anyone standing on safe ground.
            if (zones.isProtected(player.getLocation())) {
                event.setCancelled(true);
                return;
            }
            Horde horde = director.hordeOf(source);
            if (horde != null) {
                director.noteHit(player, horde);
                if (cfg.wightHitFrostTicks > 0) {
                    player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + cfg.wightHitFrostTicks));
                }
            }
        }
    }

    private void hurtCreature(EntityDamageEvent event, String role) {
        DamageCause cause = event.getCause();
        if (cause == DamageCause.FREEZE) {
            event.setCancelled(true); // born of the cold
            return;
        }
        String gear = null; // which piece of dragonglass gear dealt this blow, if any
        boolean melee = cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
        boolean shot = cause == DamageCause.PROJECTILE;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (melee && damager instanceof Player p) {
                String held = items.kind(p.getInventory().getItemInMainHand());
                if (RimeItems.BLADE.equals(held) || RimeItems.DAGGER.equals(held) || RimeItems.SPEAR.equals(held)) {
                    gear = held;
                }
            } else if (shot && damager instanceof Projectile arrowOrSpear) {
                String mark = arrowOrSpear.getPersistentDataContainer().get(keys.projectileKind, PersistentDataType.STRING);
                if (RimeItems.ARROW.equals(mark) || RimeItems.SPEAR.equals(mark)) {
                    gear = mark;
                }
            }
        }
        DamageKind kind = DamageRules.classify(cause, gear != null && melee, gear != null && shot);
        double potency = kind == DamageKind.FORGED ? cfg.potency(gear) : 1.0;
        double base = event.getDamage();
        double result = "walker".equals(role)
                ? DamageRules.walkerDamage(cfg, kind, base, potency)
                : DamageRules.wightDamage(cfg, kind, base);
        event.setDamage(result);
    }

    /** An arrow fired from a bow or crossbow keeps the mark of the ammunition it was made from. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!items.isForgedArrow(event.getConsumable())) {
            return;
        }
        Entity shot = event.getProjectile();
        if (shot instanceof Arrow) {
            shot.getPersistentDataContainer().set(keys.projectileKind, PersistentDataType.STRING, RimeItems.ARROW);
        }
    }

    /** A trident thrown while holding the dragonglass spear is the spear. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile shot = event.getEntity();
        if (!(shot instanceof Trident) || !(shot.getShooter() instanceof Player thrower)) {
            return;
        }
        if (RimeItems.SPEAR.equals(items.kind(thrower.getInventory().getItemInMainHand()))
                || RimeItems.SPEAR.equals(items.kind(thrower.getInventory().getItemInOffHand()))) {
            shot.getPersistentDataContainer().set(keys.projectileKind, PersistentDataType.STRING, RimeItems.SPEAR);
        }
    }

    /**
     * Shards only go into our own gear recipes: otherwise one could be spent as the plain item it is
     * based on in a vanilla recipe, or fed back into the shard recipe to multiply them.
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory grid = event.getInventory();
        boolean hasShard = false;
        for (ItemStack stack : grid.getMatrix()) {
            if (stack != null && items.isShard(stack)) {
                hasShard = true;
                break;
            }
        }
        if (!hasShard) {
            return;
        }
        Recipe recipe = event.getRecipe();
        NamespacedKey key = recipe instanceof Keyed keyed ? keyed.getKey() : null;
        boolean ours = keys.isOurRecipe(key);
        boolean makesShard = ours && RimeItems.SHARD.equals(keys.recipeId(key));
        if (CraftGuard.blocked(true, ours, makesShard)) {
            grid.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        String role = director.roleOf(event.getEntity());
        if (role == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Horde horde = director.hordeOf(event.getEntity());
        if (horde == null) {
            return; // a left-over from a horde that is already over
        }
        if ("walker".equals(role)) {
            director.onWalkerDeath(horde, event, event.getEntity().getKiller());
        } else {
            director.onWightDeath(horde, event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        director.onPlayerDeath(event.getEntity());
    }

    /** Horde creatures never lock on to someone standing on safe ground. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (director.roleOf(event.getEntity()) == null || event.getTarget() == null) {
            return;
        }
        if (zones.isProtected(event.getTarget().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Creatures of a horde that ended while their chunk was unloaded crumble when it loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        director.sweepOrphans(event.getEntities());
    }
}
