package com.nexuscraft.nexusrime;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns the recipe settings into real recipes. The shard is matched by exact item data, so a plain
 * lump of the same base material cannot stand in for it; and a plain lump is matched exactly too
 * wherever it is called for, so a shard can never be fed back in as one.
 */
final class RecipeBook {

    private RecipeBook() {
    }

    /** Every enabled, valid recipe, shard first. Problems are reported to {@code warn} and skipped. */
    static List<Recipe> build(RimeConfig cfg, RimeItems items, Keys keys, Consumer<String> warn) {
        List<Recipe> out = new ArrayList<>();
        for (String id : RimeConfig.GEAR_IDS) {
            RimeConfig.Gear g = cfg.gear.get(id);
            if (g == null || !g.recipeEnabled) {
                continue;
            }
            if (!g.recipe.valid()) {
                warn.accept("The " + id + " recipe in config.yml is not a valid recipe -- skipped.");
                continue;
            }
            Recipe r = make(cfg, items, keys, id, g.recipe, warn);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    private static Recipe make(RimeConfig cfg, RimeItems items, Keys keys, String id, RecipeSpec spec, Consumer<String> warn) {
        ItemStack result = items.make(id, spec.amount);
        if (result == null) {
            return null;
        }
        if (spec.isShaped()) {
            ShapedRecipe recipe = new ShapedRecipe(keys.recipe(id), result);
            recipe.shape(spec.shape.toArray(new String[0]));
            for (java.util.Map.Entry<Character, String> e : spec.ingredients.entrySet()) {
                RecipeChoice choice = choice(cfg, items, e.getValue());
                if (choice == null) {
                    warn.accept("The " + id + " recipe uses '" + e.getValue() + "', which is not a material -- skipped.");
                    return null;
                }
                recipe.setIngredient(e.getKey(), choice);
            }
            return recipe;
        }
        ShapelessRecipe recipe = new ShapelessRecipe(keys.recipe(id), result);
        for (String name : spec.loose) {
            RecipeChoice choice = choice(cfg, items, name);
            if (choice == null) {
                warn.accept("The " + id + " recipe uses '" + name + "', which is not a material -- skipped.");
                return null;
            }
            recipe.addIngredient(choice);
        }
        return recipe;
    }

    /** The match rule for one ingredient name, or null if it names nothing real. */
    static RecipeChoice choice(RimeConfig cfg, RimeItems items, String name) {
        if (RecipeSpec.SHARD.equalsIgnoreCase(name)) {
            return new RecipeChoice.ExactChoice(items.shard(1));
        }
        Material m = Material.matchMaterial(name);
        if (m == null || m == Material.AIR) {
            return null;
        }
        Material shardBase = Material.matchMaterial(cfg.materialOf(RimeItems.SHARD));
        if (m == shardBase) {
            return new RecipeChoice.ExactChoice(new ItemStack(m));
        }
        return new RecipeChoice.MaterialChoice(m);
    }
}
