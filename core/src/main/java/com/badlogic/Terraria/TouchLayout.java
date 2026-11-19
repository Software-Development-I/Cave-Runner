package com.badlogic.Terraria;

/**
 * Pure geometry for the on-screen touch controls, in SCREEN PIXELS with a bottom-left origin.
 * Everything scales with screen height so buttons keep the same physical proportion on a 720p window and a
 * 1080p / 1440p phone. No GL here, so the layout is unit-tested across resolutions.
 */
public final class TouchLayout {
    private TouchLayout() {}

    public static final int PAD = 0, JUMP = 1, ATTACK = 2, MINE = 3, PLACE = 4, PAUSE = 5, BAG = 6, COUNT = 7;

    // base sizes, in "units" (1 unit = 1 pixel at 720p)
    public static final float BTN = 110f, PAD_SIZE = 210f, SMALL_W = 120f, SMALL_H = 56f, MARGIN = 30f, GAP = 16f;

    public static float scale(int screenH) { return Math.max(0.8f, Math.min(2.5f, screenH / 720f)); }

    /** @return one {x, y, w, h} rect per control, indexed by the constants above */
    public static float[][] compute(int sw, int sh) {
        float u = scale(sh);
        float[][] r = new float[COUNT][];
        r[PAD]    = new float[]{MARGIN * u, MARGIN * u, PAD_SIZE * u, PAD_SIZE * u};
        r[JUMP]   = new float[]{sw - (BTN + MARGIN) * u, MARGIN * u, BTN * u, BTN * u};
        r[ATTACK] = new float[]{sw - (2 * BTN + MARGIN + GAP + 4f) * u, MARGIN * u, BTN * u, BTN * u};
        r[MINE]   = new float[]{sw - (BTN + MARGIN) * u, (MARGIN + BTN + GAP) * u, BTN * u, BTN * u};
        r[PLACE]  = new float[]{sw - (2 * BTN + MARGIN + GAP + 4f) * u, (MARGIN + BTN + GAP) * u, BTN * u, BTN * u};
        r[PAUSE]  = new float[]{sw - (SMALL_W + 10f) * u, sh - (SMALL_H + 10f) * u, SMALL_W * u, SMALL_H * u};
        r[BAG]    = new float[]{sw - (2 * SMALL_W + 20f) * u, sh - (SMALL_H + 10f) * u, SMALL_W * u, SMALL_H * u};
        return r;
    }
}
