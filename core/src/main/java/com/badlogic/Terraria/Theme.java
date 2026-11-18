package com.badlogic.Terraria;

/** Per-level visual and generation tuning. */
public class Theme {
    public final String name;
    public final float sandBias;
    public final float lavaBandChance;
    public final float treeChance;

    private Theme(String name, float sandBias, float lavaBandChance, float treeChance) {
        this.name = name;
        this.sandBias = sandBias;
        this.lavaBandChance = lavaBandChance;
        this.treeChance = treeChance;
    }

    public static Theme forLevel(int level) {
        switch (level) {
            case 1:
                return new Theme("Green Hills", 0.9f, 0.05f, 0.12f);
            case 2:
                return new Theme("Sunbaked Dunes", 0.55f, 0.08f, 0.03f);
            case 3:
                return new Theme("Frozen Deep", 0.95f, 0.02f, 0.01f);
            case 4:
                return new Theme("Ember Caverns", 0.85f, 0.22f, 0.02f);
            default:
                return new Theme("The Abyss", 0.9f, 0.30f, 0.0f);
        }
    }
}
