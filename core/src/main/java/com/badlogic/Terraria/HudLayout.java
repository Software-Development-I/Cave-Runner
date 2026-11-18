package com.badlogic.Terraria;

/** Pure geometry for the in-game hotbar. */
public final class HudLayout {
    private HudLayout() {}

    public static final float SLOT = 46f, GAP = 4f, HOTBAR_Y = 10f, MIN_SLOT = 30f, SIDE_MARGIN = 8f;

    public static float[] hotbar(int sw, float u) {
        float slot = SLOT * u, gap = GAP * u;
        float free = sw - 2f * SIDE_MARGIN * u;
        int n = Inventory.HOTBAR;
        float need = n * slot + (n - 1) * gap;
        if (need > free) {
            slot = Math.max(MIN_SLOT * u, (free - (n - 1) * gap) / n);
            need = n * slot + (n - 1) * gap;
        }
        float x = (sw - need) / 2f;
        return new float[]{x, HOTBAR_Y * u, need, slot};
    }

    public static float[] hotbarSlotAndGap(int sw, float u) {
        float[] h = hotbar(sw, u);
        int n = Inventory.HOTBAR;
        float slot = h[3];
        float gap = n > 1 ? (h[2] - n * slot) / (n - 1) : 0f;
        return new float[]{slot, gap};
    }
}
