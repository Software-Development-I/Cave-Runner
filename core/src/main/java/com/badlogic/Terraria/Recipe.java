package com.badlogic.Terraria;

import java.util.ArrayList;
import java.util.List;

/** Data-driven crafting recipes. */
public class Recipe {
    public final int resultId, resultCount;
    public final int[] ingredientIds, ingredientCounts;

    private Recipe(int resultId, int resultCount, int[] ids, int[] counts) {
        this.resultId = resultId;
        this.resultCount = resultCount;
        this.ingredientIds = ids;
        this.ingredientCounts = counts;
    }

    private static Recipe r(int res, int n, int... pairs) {
        int k = pairs.length / 2;
        int[] ids = new int[k], counts = new int[k];
        for (int i = 0; i < k; i++) {
            ids[i] = pairs[2 * i];
            counts[i] = pairs[2 * i + 1];
        }
        return new Recipe(res, n, ids, counts);
    }

    public static final List<Recipe> ALL = new ArrayList<>();

    static {
        ALL.add(r(Tiles.PLANKS, 4, Tiles.WOOD, 1));
        ALL.add(r(Tiles.TORCH, 4, Tiles.COAL, 1, Tiles.WOOD, 1));
        ALL.add(r(Inventory.ITEM_WOOD_PICK, 1, Tiles.PLANKS, 8));
        ALL.add(r(Inventory.ITEM_STONE_PICK, 1, Tiles.PLANKS, 4, Tiles.STONE, 8));
        ALL.add(r(Inventory.ITEM_IRON_PICK, 1, Tiles.PLANKS, 4, Tiles.IRON, 6));
        ALL.add(r(Inventory.ITEM_WOOD_SWORD, 1, Tiles.PLANKS, 6));
        ALL.add(r(Inventory.ITEM_STONE_SWORD, 1, Tiles.PLANKS, 3, Tiles.STONE, 6));
        ALL.add(r(Inventory.ITEM_IRON_SWORD, 1, Tiles.PLANKS, 3, Tiles.IRON, 5));
    }

    public boolean canCraft(Inventory inv) {
        if (Inventory.isTool(resultId) && inv.count(resultId) > 0) return false;
        for (int i = 0; i < ingredientIds.length; i++) {
            if (!inv.has(ingredientIds[i], ingredientCounts[i])) return false;
        }
        return true;
    }

    public boolean craft(Inventory inv) {
        if (!canCraft(inv)) return false;
        for (int i = 0; i < ingredientIds.length; i++) {
            inv.remove(ingredientIds[i], ingredientCounts[i]);
        }
        int left = inv.add(resultId, resultCount);
        if (left > 0) {
            inv.remove(resultId, resultCount - left);
            for (int i = 0; i < ingredientIds.length; i++) {
                inv.add(ingredientIds[i], ingredientCounts[i]);
            }
            return false;
        }
        return true;
    }

    public String label() {
        StringBuilder sb = new StringBuilder(Inventory.name(resultId));
        if (resultCount > 1) sb.append(" x").append(resultCount);
        sb.append("  <-  ");
        for (int i = 0; i < ingredientIds.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ingredientCounts[i]).append(' ').append(Inventory.name(ingredientIds[i]));
        }
        return sb.toString();
    }
}
