package com.badlogic.Terraria;

/**
 * Pure geometry for the in-game HUD (screen pixels, bottom-left origin, scaled by u = ui scale).
 * Hud draws from these rects and Main hit-tests against them, so what you see is what you can tap.
 */
public final class HudLayout {
    private HudLayout() {}

    public static final float SLOT = 46f, GAP = 4f, HOTBAR_Y = 10f, MIN_SLOT = 30f, SIDE_MARGIN = 8f;
    /** Horizontal space kept free on both sides of centred banners: covers the left stats block and the top-right buttons. */
    public static final float SIDE_RESERVE = 270f;
    public static final float LEFT_X = 13f, LEFT_W = 244f, LEFT_H = 153f;

    /** Top-left stats block: HP bar, hearts, score, coins, depth, combo and the night row. */
    public static float[] leftBlock(int sh, float u) { return new float[]{LEFT_X * u, sh - (LEFT_H + 13f) * u, LEFT_W * u, LEFT_H * u}; }

    /**
     * Hotbar rect {x, y, w, h}. The slot size shrinks (never below MIN_SLOT) so the bar always fits in the free
     * strip between the left touch pad and the right-hand button cluster. On wide screens this is a no-op.
     */
    public static float[] hotbar(int sw, float u) { return hotbar(sw, 0, u); }

    /** @param sh screen height, used to find where the touch controls sit; pass 0 to ignore touch controls. */
    public static float[] hotbar(int sw, int sh, float u) {
        float slot = SLOT * u, gap = GAP * u, minSlot = MIN_SLOT * u;
        float free = sw - 2f * SIDE_MARGIN * u;                       // never touch the very screen edges
        if (sh > 0) {
            float[][] t = TouchLayout.compute(sw, sh);
            float padRight = t[TouchLayout.PAD][0] + t[TouchLayout.PAD][2];
            float clusterLeft = Math.min(t[TouchLayout.ATTACK][0], t[TouchLayout.PLACE][0]);
            // only matters if the controls reach up to the hotbar's row; if they clear it entirely, keep full size
            float barTop = HOTBAR_Y * u + slot;
            boolean padCollides = t[TouchLayout.PAD][1] < barTop, clusterCollides = Math.min(t[TouchLayout.ATTACK][1], t[TouchLayout.PLACE][1]) < barTop;
            float left = padCollides ? padRight + SIDE_MARGIN * u : SIDE_MARGIN * u;
            float right = clusterCollides ? clusterLeft - SIDE_MARGIN * u : sw - SIDE_MARGIN * u;
            free = Math.max(0f, right - left);
        }
        float n = Inventory.HOTBAR;
        float need = n * slot + (n - 1) * gap;
        if (need > free) {
            slot = Math.max(minSlot, (free - (n - 1) * gap) / n);
            need = n * slot + (n - 1) * gap;
            if (need > free) { gap = Math.max(1f, (free - n * slot) / (n - 1)); need = n * slot + (n - 1) * gap; }
        }
        float x, hbLeft = SIDE_MARGIN * u;
        if (sh > 0) {
            float[][] t = TouchLayout.compute(sw, sh);
            float barTop = HOTBAR_Y * u + SLOT * u;
            boolean padCollides = t[TouchLayout.PAD][1] < barTop, clusterCollides = Math.min(t[TouchLayout.ATTACK][1], t[TouchLayout.PLACE][1]) < barTop;
            float left = padCollides ? t[TouchLayout.PAD][0] + t[TouchLayout.PAD][2] + SIDE_MARGIN * u : SIDE_MARGIN * u;
            float right = clusterCollides ? Math.min(t[TouchLayout.ATTACK][0], t[TouchLayout.PLACE][0]) - SIDE_MARGIN * u : sw - SIDE_MARGIN * u;
            x = left + (right - left - need) / 2f;                      // centred in the free strip
        } else x = (sw - need) / 2f;
        return new float[]{x, HOTBAR_Y * u, need, slot};
    }

    /** Slot edge length and gap actually used by {@link #hotbar(int, int, float)} (read by Hud so draw == hit-test). */
    public static float[] hotbarSlotAndGap(int sw, int sh, float u) {
        float[] h = hotbar(sw, sh, u);
        float n = Inventory.HOTBAR, slot = h[3];
        float gap = n > 1 ? (h[2] - n * slot) / (n - 1) : 0f;
        return new float[]{slot, gap};
    }

    /** Outer rect (including its 3px frame) of the boss health bar, kept clear of the side reserves. */
    public static float[] bossBar(int sw, int sh, float u) {
        float bw = Math.max(120f * u, Math.min(Math.min(sw * 0.5f, 520f * u), sw - 2f * SIDE_RESERVE * u));
        return new float[]{(sw - bw) / 2f - 3f, sh - 40f * u - 3f, bw + 6f, 16f * u + 6f};
    }

    /**
     * Tutorial strip. Width follows the text but never reaches under the stats block or the top-right buttons;
     * height follows the WRAPPED text so long prompts on narrow screens never spill out of the box.
     */
    public static float[] tutorialRect(int sw, int sh, float u, float textWidth, float textHeight) {
        float maxW = Math.max(240f * u, sw - 2f * SIDE_RESERVE * u);
        float w = Math.min(maxW, Math.max(360f * u, textWidth + 40f * u));
        float h = textHeight + 46f * u;
        return new float[]{(sw - w) / 2f, sh - 70f * u - h, w, h};
    }

    public static boolean overlaps(float[] a, float[] b) {
        return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] && a[1] < b[1] + b[3] && a[1] + a[3] > b[1];
    }
}
