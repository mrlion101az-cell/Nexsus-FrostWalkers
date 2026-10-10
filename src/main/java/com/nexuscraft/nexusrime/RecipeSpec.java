package com.nexuscraft.nexusrime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One crafting recipe as plain data: either a shaped grid (rows of up to three characters, each
 * character naming an ingredient) or a shapeless list, plus how many items it makes. An ingredient
 * is a material name, or {@code DRAGONGLASS} for the forged shard.
 */
final class RecipeSpec {

    /** The pseudo-material that stands for the Dragonglass Shard in a recipe or a drop list. */
    static final String SHARD = "DRAGONGLASS";

    /** Rows of the grid, or null for a shapeless recipe. */
    final List<String> shape;
    /** Ingredient per grid character (shaped recipes). */
    final Map<Character, String> ingredients;
    /** Ingredient names (shapeless recipes). */
    final List<String> loose;
    final int amount;

    private RecipeSpec(List<String> shape, Map<Character, String> ingredients, List<String> loose, int amount) {
        this.shape = shape;
        this.ingredients = ingredients;
        this.loose = loose;
        this.amount = amount;
    }

    static RecipeSpec shaped(List<String> shape, Map<Character, String> ingredients, int amount) {
        return new RecipeSpec(new ArrayList<>(shape), new LinkedHashMap<>(ingredients), List.of(), amount);
    }

    static RecipeSpec shapeless(List<String> items, int amount) {
        return new RecipeSpec(null, Map.of(), new ArrayList<>(items), amount);
    }

    boolean isShaped() {
        return shape != null;
    }

    RecipeSpec withAmount(int newAmount) {
        return new RecipeSpec(shape, ingredients, loose, newAmount);
    }

    /** True when the recipe could really be registered: a rectangle of 1-3 by 1-3, every character
     *  used has an ingredient, or 1-9 loose ingredients; a sensible amount. */
    boolean valid() {
        if (amount < 1 || amount > 64) {
            return false;
        }
        if (!isShaped()) {
            if (loose.isEmpty() || loose.size() > 9) {
                return false;
            }
            for (String s : loose) {
                if (s == null || s.isBlank()) {
                    return false;
                }
            }
            return true;
        }
        if (shape.isEmpty() || shape.size() > 3) {
            return false;
        }
        int width = shape.get(0).length();
        if (width < 1 || width > 3) {
            return false;
        }
        boolean any = false;
        for (String row : shape) {
            if (row.length() != width) {
                return false;
            }
            for (char ch : row.toCharArray()) {
                if (ch != ' ') {
                    any = true;
                    String ing = ingredients.get(ch);
                    if (ing == null || ing.isBlank()) {
                        return false;
                    }
                }
            }
        }
        return any;
    }

    /** Every ingredient name this recipe uses, in order of first appearance. */
    List<String> usedIngredients() {
        List<String> out = new ArrayList<>();
        if (!isShaped()) {
            for (String s : loose) {
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
            return out;
        }
        for (String row : shape) {
            for (char ch : row.toCharArray()) {
                String ing = ingredients.get(ch);
                if (ch != ' ' && ing != null && !out.contains(ing)) {
                    out.add(ing);
                }
            }
        }
        return out;
    }

    /** Does the recipe use the forged shard as an ingredient? */
    boolean usesShard() {
        return usedIngredients().contains(SHARD);
    }
}
