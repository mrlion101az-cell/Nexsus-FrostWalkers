package com.nexuscraft.nexusrime;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every data tag this plugin writes, in one place. */
final class Keys {
    /** On every horde creature: the id of the horde that raised it. */
    final NamespacedKey mobHorde;
    /** On every horde creature: "walker" or "wight". */
    final NamespacedKey mobRole;
    /** On every piece of dragonglass gear: which one ("shard", "blade", ...). */
    final NamespacedKey itemKind;
    /** On a projectile fired with dragonglass gear: which one ("arrow" or "spear"). */
    final NamespacedKey projectileKind;
    private final Map<String, NamespacedKey> recipes = new LinkedHashMap<>();

    Keys(Plugin plugin) {
        mobHorde = new NamespacedKey(plugin, "horde_id");
        mobRole = new NamespacedKey(plugin, "horde_role");
        itemKind = new NamespacedKey(plugin, "item_kind");
        projectileKind = new NamespacedKey(plugin, "projectile_kind");
        for (String id : RimeConfig.GEAR_IDS) {
            recipes.put(id, new NamespacedKey(plugin, "recipe_" + id));
        }
    }

    NamespacedKey recipe(String id) {
        return recipes.get(id);
    }

    /** Is this one of our own recipe keys? */
    boolean isOurRecipe(NamespacedKey key) {
        return key != null && recipes.containsValue(key);
    }

    /** The recipe id ("shard", "blade", ...) a key belongs to, or null. */
    String recipeId(NamespacedKey key) {
        for (Map.Entry<String, NamespacedKey> e : recipes.entrySet()) {
            if (e.getValue().equals(key)) {
                return e.getKey();
            }
        }
        return null;
    }
}
