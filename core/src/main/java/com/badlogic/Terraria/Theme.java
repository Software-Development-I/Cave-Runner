package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;

/** Per-level look and feel for generation and rendering. */
public final class Theme {
    public final String name;
    public final float[] skyDay, skyNight, cave;
    public final float ambientDark;
    public final float sandBias;
    public final float treeChance;
    public final float lavaBandChance;
    public final float[] dirtTint, stoneTint;

    private Theme(String name, float[] skyDay, float[] skyNight, float[] cave, float ambientDark,
                  float sandBias, float treeChance, float lavaBandChance,
                  float[] dirtTint, float[] stoneTint) {
        this.name = name;
        this.skyDay = skyDay;
        this.skyNight = skyNight;
        this.cave = cave;
        this.ambientDark = ambientDark;
        this.sandBias = sandBias;
        this.treeChance = treeChance;
        this.lavaBandChance = lavaBandChance;
        this.dirtTint = dirtTint;
        this.stoneTint = stoneTint;
    }

    private static float[] c(float r, float g, float b) {
        return new float[]{r, g, b};
    }

    private static final Theme[] ALL = {
        new Theme("Green Hills", c(.45f, .75f, .95f), c(.03f, .04f, .12f),
            c(.09f, .08f, .11f), .00f, .62f, .050f, .18f,
            c(1f, 1f, 1f), c(1f, 1f, 1f)),
        new Theme("Sunbaked Dunes", c(.98f, .78f, .52f), c(.10f, .05f, .10f),
            c(.14f, .09f, .07f), .00f, .18f, .008f, .20f,
            c(1.05f, .92f, .78f), c(1.05f, .93f, .82f)),
        new Theme("Frozen Deep", c(.70f, .86f, .97f), c(.04f, .07f, .16f),
            c(.07f, .10f, .16f), .05f, .90f, .020f, .14f,
            c(.80f, .92f, 1.10f), c(.80f, .92f, 1.15f)),
        new Theme("Ember Caverns", c(.55f, .32f, .30f), c(.10f, .02f, .03f),
            c(.15f, .06f, .05f), .08f, .70f, .010f, .34f,
            c(.85f, .55f, .50f), c(.95f, .62f, .58f)),
        new Theme("The Abyss", c(.20f, .16f, .34f), c(.02f, .01f, .06f),
            c(.06f, .04f, .12f), .14f, .80f, .006f, .30f,
            c(.60f, .48f, .85f), c(.62f, .55f, .95f))
    };

    /** Tint for shared dirt/stone textures under this theme. */
    public float[] tintFor(int tile) {
        switch (tile) {
            case Tiles.DIRT:
            case Tiles.SAND:
            case Tiles.LEAVES:
                return dirtTint;
            case Tiles.STONE:
            case Tiles.BEDROCK:
            case Tiles.CRUMBLE:
            case Tiles.ARROW_TRAP:
                return stoneTint;
            default:
                return null;
        }
    }

    public static Theme forLevel(int level) {
        return ALL[MathUtils.clamp(level - 1, 0, ALL.length - 1)];
    }

    public static int count() {
        return ALL.length;
    }

    /** Blend day/night sky by darkness (0..1). */
    public float[] sky(float dark, float[] out) {
        for (int i = 0; i < 3; i++) {
            out[i] = skyDay[i] + (skyNight[i] - skyDay[i]) * dark;
        }
        return out;
    }
}
