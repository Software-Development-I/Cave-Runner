package com.badlogic.Terraria;

public class WorldPreview {
    public static void main(String[] args) {
        World w = new World(12345L, 1);
        System.out.println("spawn=(" + w.spawnX + "," + w.spawnY
            + ") portal=(" + w.portalX + "," + w.portalY + ")");

        for (int y = World.HEIGHT - 1; y >= 0; y--) {
            int t = w.get(w.spawnX, y);
            char c = switch (t) {
                case Tiles.AIR -> '.';
                case Tiles.DIRT -> 'd';
                case Tiles.STONE -> '#';
                case Tiles.BEDROCK -> 'B';
                case Tiles.SAND -> 's';
                case Tiles.LAVA -> '~';
                default -> '?';
            };
            System.out.println(y + "\t" + c);
        }
    }
}
