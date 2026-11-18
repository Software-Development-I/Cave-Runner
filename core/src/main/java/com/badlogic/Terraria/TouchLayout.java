package com.badlogic.Terraria;

/** Pure screen geometry for the on-screen touch controls. */
public final class TouchLayout {
    private TouchLayout() {}

    public static final int PAD = 0, JUMP = 1, ATTACK = 2, MINE = 3, PLACE = 4, BAG = 5, COUNT = 6;
    public static final float BTN = 110f, PAD_SIZE = 210f, SMALL_W = 120f, SMALL_H = 56f, MARGIN = 30f, GAP = 16f;

    public static float scale(int screenH) {
        return Math.max(0.8f, Math.min(2.5f, screenH / 720f));
    }

    public static float[][] compute(int sw, int sh) {
        float u = scale(sh);
        float[][] r = new float[COUNT][];
        r[PAD]    = new float[]{MARGIN * u, MARGIN * u, PAD_SIZE * u, PAD_SIZE * u};
        r[JUMP]   = new float[]{sw - (BTN + MARGIN) * u, MARGIN * u, BTN * u, BTN * u};
        r[ATTACK] = new float[]{sw - (2 * BTN + MARGIN + GAP + 4f) * u, MARGIN * u, BTN * u, BTN * u};
        r[MINE]   = new float[]{sw - (BTN + MARGIN) * u, (MARGIN + BTN + GAP) * u, BTN * u, BTN * u};
        r[PLACE]  = new float[]{sw - (2 * BTN + MARGIN + GAP + 4f) * u, (MARGIN + BTN + GAP) * u, BTN * u, BTN * u};
        r[BAG]    = new float[]{sw - (SMALL_W + 10f) * u, sh - (SMALL_H + 10f) * u, SMALL_W * u, SMALL_H * u};
        return r;
    }
}
