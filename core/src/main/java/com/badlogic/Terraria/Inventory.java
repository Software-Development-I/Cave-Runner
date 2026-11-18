package com.badlogic.Terraria;

/** 9-slot hotbar + 27-slot backpack. */
public class Inventory {
    public static final int HOTBAR = 9, SIZE = 36, MAX_STACK = 99;

    public static final int ITEM_WOOD_PICK = 100, ITEM_STONE_PICK = 101, ITEM_IRON_PICK = 102;
    public static final int ITEM_WOOD_SWORD = 110, ITEM_STONE_SWORD = 111, ITEM_IRON_SWORD = 112;

    public final int[] ids = new int[SIZE];
    public final int[] counts = new int[SIZE];
    public int selected = 0;

    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            ids[i] = 0;
            counts[i] = 0;
        }
    }

    public static boolean isPlaceable(int id) {
        return id > 0 && id < Tiles.COUNT && Tiles.isBreakable(id) && id != Tiles.SPIKES;
    }

    public static boolean isTool(int id) {
        return id >= 100;
    }

    public static String toolTexture(int id) {
        switch (id) {
            case ITEM_WOOD_PICK:
            case ITEM_STONE_PICK:
            case ITEM_IRON_PICK:
                return "pickaxe";
            case ITEM_WOOD_SWORD:
            case ITEM_STONE_SWORD:
            case ITEM_IRON_SWORD:
                return "sword";
            default:
                return null;
        }
    }

    public static String name(int id) {
        switch (id) {
            case ITEM_WOOD_PICK: return "Wood Pickaxe";
            case ITEM_STONE_PICK: return "Stone Pickaxe";
            case ITEM_IRON_PICK: return "Iron Pickaxe";
            case ITEM_WOOD_SWORD: return "Wood Sword";
            case ITEM_STONE_SWORD: return "Stone Sword";
            case ITEM_IRON_SWORD: return "Iron Sword";
            default: return Tiles.name(id);
        }
    }

    /** Adds items, filling existing stacks first. Returns the amount that did not fit. */
    public int add(int id, int amount) {
        if (id <= 0 || amount <= 0) return 0;
        if (!isTool(id)) {
            for (int i = 0; i < SIZE && amount > 0; i++) {
                if (ids[i] == id && counts[i] < MAX_STACK) {
                    int put = Math.min(amount, MAX_STACK - counts[i]);
                    counts[i] += put;
                    amount -= put;
                }
            }
        }
        for (int i = 0; i < SIZE && amount > 0; i++) {
            if (ids[i] == 0) {
                int put = isTool(id) ? 1 : Math.min(amount, MAX_STACK);
                ids[i] = id;
                counts[i] = put;
                amount -= put;
                if (isTool(id)) break;
            }
        }
        return amount;
    }

    public int count(int id) {
        int n = 0;
        for (int i = 0; i < SIZE; i++) {
            if (ids[i] == id) n += counts[i];
        }
        return n;
    }

    public boolean has(int id, int amount) {
        return count(id) >= amount;
    }

    /** Removes items across stacks. Returns true only if the full amount was removed. */
    public boolean remove(int id, int amount) {
        if (!has(id, amount)) return false;
        for (int i = SIZE - 1; i >= 0 && amount > 0; i--) {
            if (ids[i] == id) {
                int take = Math.min(amount, counts[i]);
                counts[i] -= take;
                amount -= take;
                if (counts[i] == 0) ids[i] = 0;
            }
        }
        return true;
    }

    public int selectedId() {
        return ids[selected];
    }

    public void useSelected() {
        if (isTool(ids[selected])) return;
        if (--counts[selected] <= 0) {
            counts[selected] = 0;
            ids[selected] = 0;
        }
    }

    public void select(int slot) {
        selected = ((slot % HOTBAR) + HOTBAR) % HOTBAR;
    }

    public int pickTier() {
        if (count(ITEM_IRON_PICK) > 0) return 3;
        if (count(ITEM_STONE_PICK) > 0) return 2;
        if (count(ITEM_WOOD_PICK) > 0) return 1;
        return 0;
    }

    public int swordTier() {
        if (count(ITEM_IRON_SWORD) > 0) return 3;
        if (count(ITEM_STONE_SWORD) > 0) return 2;
        if (count(ITEM_WOOD_SWORD) > 0) return 1;
        return 0;
    }
}
