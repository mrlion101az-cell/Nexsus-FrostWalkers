package com.nexuscraft.nexusrime;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The dragonglass shard and the gear made from it. Ordinary items tagged in item data, so they work
 * the same on Java and Bedrock, and no renamed lookalike can pass for one.
 */
final class RimeItems {

    static final String SHARD = "shard";
    static final String BLADE = "blade";
    static final String DAGGER = "dagger";
    static final String SPEAR = "spear";
    static final String ARROW = "arrow";

    private final Keys keys;
    private final RimeConfig cfg;

    RimeItems(Keys keys, RimeConfig cfg) {
        this.keys = keys;
        this.cfg = cfg;
    }

    /** A stack of the piece with this id, or null for an id that does not exist. */
    ItemStack make(String id, int amount) {
        if (!RimeConfig.GEAR_IDS.contains(id)) {
            return null;
        }
        Material m = Material.matchMaterial(cfg.materialOf(id));
        if (m == null || m == Material.AIR) {
            m = defaultMaterial(id);
        }
        ItemStack item = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((SHARD.equals(id) ? "§8§l" : "§b§l") + cfg.nameOf(id));
        meta.setLore(lore(id));
        meta.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack shard(int amount) {
        return make(SHARD, amount);
    }

    ItemStack blade(int amount) {
        return make(BLADE, amount);
    }

    ItemStack dagger(int amount) {
        return make(DAGGER, amount);
    }

    ItemStack spear(int amount) {
        return make(SPEAR, amount);
    }

    ItemStack arrow(int amount) {
        return make(ARROW, amount);
    }

    private static Material defaultMaterial(String id) {
        return switch (id) {
            case SHARD -> Material.FLINT;
            case DAGGER -> Material.IRON_SWORD;
            case SPEAR -> Material.TRIDENT;
            case ARROW -> Material.ARROW;
            default -> Material.DIAMOND_SWORD;
        };
    }

    private List<String> lore(String id) {
        String walker = cfg.walkerName;
        String wight = cfg.wightName;
        return switch (id) {
            case SHARD -> List.of(
                    "§7A sliver of black glass, sharper than steel.",
                    "§7Craft it into a blade, dagger, spear or arrows",
                    "§7to hurt the §f" + walker + "§7.",
                    "§8Cannot be used in ordinary recipes.");
            case BLADE -> List.of(
                    "§7Forged from black glass and diamond.",
                    "§7The surest weapon against the §f" + walker + "§7,",
                    "§7and it shatters the §f" + wight + " §7in a single blow.",
                    "§8Fire hurts them too.");
            case DAGGER -> List.of(
                    "§7A short blade of black glass.",
                    "§7It wounds the §f" + walker + " §7and shatters the §f" + wight + "§7.",
                    "§8Lighter than the " + cfg.weaponName + ".");
            case SPEAR -> List.of(
                    "§7A spear tipped with black glass.",
                    "§7Thrust it or throw it: it wounds the §f" + walker + "§7.",
                    "§8Shatters the " + wight + " in one blow.");
            default -> List.of(
                    "§7An arrow tipped with black glass.",
                    "§7It wounds the §f" + walker + " §7and shatters the §f" + wight + "§7.",
                    "§8Fire hurts them too.");
        };
    }

    /** Which piece of dragonglass gear this is ("shard", "blade", ...), or null if it is not any. */
    String kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(keys.itemKind, PersistentDataType.STRING);
        return tag != null && RimeConfig.GEAR_IDS.contains(tag) ? tag : null;
    }

    boolean isShard(ItemStack item) {
        return SHARD.equals(kind(item));
    }

    /** A melee weapon of dragonglass (blade, dagger or spear), as held in the hand. */
    boolean isForgedWeapon(ItemStack item) {
        String k = kind(item);
        return BLADE.equals(k) || DAGGER.equals(k) || SPEAR.equals(k);
    }

    boolean isForgedArrow(ItemStack item) {
        return ARROW.equals(kind(item));
    }

    /** Kept for the blade checks of earlier code: is this item the blade? */
    boolean isBlade(ItemStack item) {
        return BLADE.equals(kind(item));
    }
}
