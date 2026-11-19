package com.badlogic.Terraria;

/** Tile type ids and their static properties. */
public final class Tiles {
    private Tiles() {}

    public static final int AIR = 0, DIRT = 1, STONE = 2, BEDROCK = 3, SAND = 4,
        SPIKES = 5, LAVA = 6, CRUMBLE = 7, ARROW_TRAP = 8, COAL = 9, IRON = 10, GOLD = 11,
        WOOD = 12, LEAVES = 13, PLANKS = 14, TORCH = 15, FLAG = 16, PORTAL = 17;
    public static final int COUNT = 18;

    /** Blocks the player/enemies cannot walk through. */
    public static boolean isSolid(int t) {
        switch (t) {
            case AIR: case SPIKES: case LAVA: case TORCH: case FLAG: case PORTAL:
            case LEAVES: case WOOD:
                return false;
            default:
                return true;
        }
    }

    /** Solid tiles that can be mined. */
    public static boolean isBreakable(int t) {
        return t != AIR && t != BEDROCK && t != PORTAL && t != FLAG && t != LAVA;
    }

    /** Seconds of mining needed with a bare hand (divided by the pickaxe power). */
    public static float hardness(int t) {
        switch (t) {
            case DIRT: case SAND: case LEAVES: return 0.25f;
            case WOOD: case PLANKS: case TORCH: return 0.4f;
            case STONE: case CRUMBLE: case ARROW_TRAP: case SPIKES: return 0.7f;
            case COAL: return 0.9f;
            case IRON: return 1.2f;
            case GOLD: return 1.5f;
            default: return 0.6f;
        }
    }

    /** Minimum pickaxe tier required. 0 = anything. */
    public static int requiredTier(int t) {
        switch (t) {
            case IRON: return 2;
            case GOLD: return 3;
            default: return 0;
        }
    }

    public static boolean isOre(int t) { return t == COAL || t == IRON || t == GOLD; }

    public static boolean isHarmful(int t) { return t == SPIKES || t == LAVA; }

    /** Item id that this tile drops when broken (0 = nothing). Item ids == tile ids. */
    public static int drop(int t) {
        switch (t) {
            case LEAVES: case ARROW_TRAP: case CRUMBLE: return 0;
            case SPIKES: return 0;
            default: return isBreakable(t) ? t : 0;
        }
    }

    public static int scoreFor(int t) {
        if (isOre(t)) return Config.SCORE_ORE;
        if (t == STONE) return Config.SCORE_STONE;
        return Config.SCORE_DIRT;
    }

    public static String name(int t) {
        switch (t) {
            case DIRT: return "Dirt"; case STONE: return "Stone"; case SAND: return "Sand";
            case COAL: return "Coal"; case IRON: return "Iron"; case GOLD: return "Gold";
            case WOOD: return "Wood"; case PLANKS: return "Planks"; case TORCH: return "Torch";
            case LEAVES: return "Leaves"; case BEDROCK: return "Bedrock";
            default: return "Block";
        }
    }

    /** Texture file for a tile id (null = not drawn). */
    public static String texture(int t) {
        switch (t) {
            case DIRT: return "dirt"; case STONE: return "stone"; case BEDROCK: return "bedrock";
            case SAND: return "sand"; case SPIKES: return "spikes"; case LAVA: return "lava";
            case CRUMBLE: return "crumble"; case ARROW_TRAP: return "arrowtrap";
            case COAL: return "coal"; case IRON: return "iron"; case GOLD: return "gold";
            case WOOD: return "wood"; case LEAVES: return "leaves"; case PLANKS: return "planks";
            case TORCH: return "torch"; case FLAG: return "flag"; case PORTAL: return "portal";
            default: return null;
        }
    }
}
