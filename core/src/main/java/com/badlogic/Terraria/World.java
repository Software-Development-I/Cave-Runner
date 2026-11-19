package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure tile data + procedural generation. No rendering or GL here, so it can be
 * unit-tested and saved/loaded easily. Row 0 is the bottom (bedrock), rows grow upward.
 */
public class World {
    public static final int TILE_SIZE = Config.TILE;
    public static final int WIDTH = Config.WORLD_W;
    public static final int HEIGHT = Config.WORLD_H;

    private final int[][] tiles = new int[WIDTH][HEIGHT];
    private final float[][] mining = new float[WIDTH][HEIGHT]; // crack progress 0..1

    public final int level;
    public final Theme theme;
    public final long seed;
    public int spawnX = Config.SPAWN_X;
    public int spawnY;
    public int portalX = -1, portalY = -1;

    /** Locations recorded by the generator so game systems can create entities. */
    public final List<int[]> arrowTraps = new ArrayList<>();
    public final List<int[]> fallingRocks = new ArrayList<>(); // {x, y} of the stone that can drop
    public final List<int[]> checkpoints = new ArrayList<>();

    private final int[] surface = new int[WIDTH];

    public World(long seed, int level) {
        this.seed = seed;
        this.level = MathUtils.clamp(level, 1, Config.MAX_LEVEL);
        this.theme = Theme.forLevel(this.level);
        generate();
    }

    // ------------------------------------------------------------------ generation

    private static float hash(long s, int x, int y) {
        long h = s * 374761393L + x * 668265263L + y * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= (h >>> 16);
        return ((h & 0xFFFFFFL) / (float) 0xFFFFFF);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    /** 1-D value noise, ~[0,1). */
    private float noise1(float x, int salt) {
        int x0 = (int) Math.floor(x);
        float f = smooth(x - x0);
        return MathUtils.lerp(hash(seed + salt, x0, 0), hash(seed + salt, x0 + 1, 0), f);
    }

    /** 2-D value noise, ~[0,1). */
    private float noise2(float x, float y, int salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = smooth(x - x0), fy = smooth(y - y0);
        float a = hash(seed + salt, x0, y0), b = hash(seed + salt, x0 + 1, y0);
        float c = hash(seed + salt, x0, y0 + 1), d = hash(seed + salt, x0 + 1, y0 + 1);
        return MathUtils.lerp(MathUtils.lerp(a, b, fx), MathUtils.lerp(c, d, fx), fy);
    }

    private void generate() {
        Random rng = new Random(seed * 31 + level);

        // 1) Surface height from layered noise, flattened near spawn
        for (int x = 0; x < WIDTH; x++) {
            float n = noise1(x * 0.04f, 1) * 10f + noise1(x * 0.11f, 2) * 4f + noise1(x * 0.3f, 3) * 1.5f;
            int h = Config.SURFACE_BASE + (int) (n - 7f);
            int dist = Math.abs(x - Config.SPAWN_X);
            if (dist < Config.SPAWN_SAFE_RADIUS + 6) {
                float w = 1f - dist / (float) (Config.SPAWN_SAFE_RADIUS + 6);
                h = Math.round(MathUtils.lerp(h, Config.SURFACE_BASE, smooth(w)));
            }
            surface[x] = MathUtils.clamp(h, 30, HEIGHT - 12);
        }

        // 2) Fill layers
        for (int x = 0; x < WIDTH; x++) {
            int s = surface[x];
            for (int y = 0; y < HEIGHT; y++) {
                int t;
                if (y == 0) t = Tiles.BEDROCK;
                else if (y < s - 4) t = Tiles.STONE;
                else if (y <= s) t = (noise1(x * 0.07f, 9) > theme.sandBias && y >= s - 1) ? Tiles.SAND : Tiles.DIRT;
                else t = Tiles.AIR;
                tiles[x][y] = t;
            }
        }

        // 3) Caves: 2-D noise threshold below the surface layer, wider deeper down
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 3; y < surface[x] - 6; y++) {
                float n = noise2(x * 0.08f, y * 0.08f, 20) * 0.7f + noise2(x * 0.2f, y * 0.2f, 21) * 0.3f;
                float depthBias = 1f - (y / (float) surface[x]);         // deeper = more open
                float threshold = 0.66f - depthBias * 0.10f - level * 0.008f;
                if (n > threshold) tiles[x][y] = Tiles.AIR;
            }
        }

        // 4) Ores by depth
        for (int x = 1; x < WIDTH - 1; x++) {
            for (int y = 2; y < surface[x] - 4; y++) {
                if (tiles[x][y] != Tiles.STONE) continue;
                float depth = 1f - y / (float) surface[x];
                float r = hash(seed + 40, x, y);
                if (depth > 0.55f && r < 0.010f) tiles[x][y] = Tiles.GOLD;
                else if (depth > 0.30f && r > 0.010f && r < 0.030f) tiles[x][y] = Tiles.IRON;
                else if (r > 0.030f && r < 0.060f) tiles[x][y] = Tiles.COAL;
            }
        }

        // 5) Lava: shallow pools that only sit on genuinely FLAT cave floor. A pool tile needs solid ground
        //    beneath it and open air above and on the same row for at least its width, so lava never
        //    trickles down slopes or clings to walls. Pools live in 6-wide bands with dry margins.
        for (int x = 2; x < WIDTH - 2; x++) {
            int band = x / 6;
            boolean poolBand = hash(seed + 60, band, 0) < theme.lavaBandChance;
            int inBand = x % 6;
            if (!poolBand || inBand < 1 || inBand > 4) continue;
            int depthCap = 1 + (hash(seed + 61, band, 1) < 0.35f ? 1 : 0);
            for (int y = 2; y < surface[x] * 0.35f; y++) {
                if (tiles[x][y] != Tiles.AIR || tiles[x][y - 1] == Tiles.AIR || tiles[x][y - 1] == Tiles.LAVA) continue;
                // flat-floor test: the floor tile under this one and under both horizontal neighbours must be solid,
                // and the neighbours at this row must be open (a pool, not a wall crack)
                boolean flat = Tiles.isSolid(tiles[x - 1][y - 1]) && Tiles.isSolid(tiles[x + 1][y - 1]) && Tiles.isSolid(tiles[x][y - 1]);
                boolean open = tiles[x - 1][y] == Tiles.AIR || tiles[x - 1][y] == Tiles.LAVA;
                open &= tiles[x + 1][y] == Tiles.AIR || tiles[x + 1][y] == Tiles.LAVA;
                boolean headroom = tiles[x][y + 1 + depthCap] == Tiles.AIR && tiles[x][y + 2 + depthCap] == Tiles.AIR;
                if (!flat || !open || !headroom) continue;
                for (int k = 0; k < depthCap && tiles[x][y + k] == Tiles.AIR; k++) tiles[x][y + k] = Tiles.LAVA;
            }
        }

        // 6) Trees on the surface (away from spawn)
        for (int x = 4; x < WIDTH - 4; x++) {
            if (Math.abs(x - Config.SPAWN_X) < Config.SPAWN_SAFE_RADIUS) continue;
            if (tiles[x][surface[x]] == Tiles.DIRT && rng.nextFloat() < theme.treeChance) placeTree(x, surface[x] + 1, rng);
        }

        // 7) Spawn point
        spawnY = surface[Config.SPAWN_X] + 1;
        for (int y = spawnY; y < spawnY + 4; y++) tiles[Config.SPAWN_X][y] = Tiles.AIR;

        // 8) Traps
        placeTraps(rng);

        // 9) Goal portal (far end) and checkpoints
        int px = (Config.SPAWN_X < WIDTH / 2) ? WIDTH - 12 : 11;
        portalX = px;
        portalY = surface[px] + 1;
        for (int y = portalY; y < portalY + 3; y++) tiles[px][y] = (y == portalY + 1) ? Tiles.PORTAL : Tiles.AIR;
        tiles[px][portalY] = Tiles.PORTAL;
        tiles[px][portalY + 1] = Tiles.PORTAL;
        for (int i = 1; i <= 2; i++) {
            int cx = Config.SPAWN_X + (px > Config.SPAWN_X ? 1 : -1) * (WIDTH / 6) * i;
            cx = MathUtils.clamp(cx, 6, WIDTH - 7);
            int cy = surface[cx] + 1;
            tiles[cx][cy] = Tiles.FLAG;
            checkpoints.add(new int[]{cx, cy});
        }
    }

    private void placeTree(int x, int baseY, Random rng) {
        int h = 4 + rng.nextInt(3);
        for (int i = 0; i < h; i++) if (inBounds(x, baseY + i)) tiles[x][baseY + i] = Tiles.WOOD;
        int top = baseY + h;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                if (Math.abs(dx) == 2 && Math.abs(dy) == 2) continue;
                int lx = x + dx, ly = top + dy;
                if (inBounds(lx, ly) && tiles[lx][ly] == Tiles.AIR) tiles[lx][ly] = Tiles.LEAVES;
            }
        }
    }

    private boolean farFromSpawn(int x) { return Math.abs(x - Config.SPAWN_X) > Config.SPAWN_SAFE_RADIUS; }

    private void placeTraps(Random rng) {
        float density = 0.6f + level * 0.25f;

        // Spikes: on cave floors. Pick random depths per column and scan down to the first floor.
        int spikeTarget = 10 + level * 6;
        int placed = 0;
        for (int attempt = 0; attempt < 4000 && placed < spikeTarget; attempt++) {
            int x = 3 + rng.nextInt(WIDTH - 6);
            if (!farFromSpawn(x)) continue;
            int y0 = 6 + rng.nextInt(Math.max(1, surface[x] - 10));
            int y = findFloor(x, y0);
            if (y > 3 && tiles[x][y] == Tiles.AIR && tiles[x][y - 1] != Tiles.LAVA
                && Tiles.isSolid(tiles[x][y - 1]) && tiles[x][y - 1] != Tiles.CRUMBLE) {
                // a short strip of 1-3 spikes
                int len = 1 + rng.nextInt(3);
                for (int k = 0; k < len; k++) {
                    int sx = x + k;
                    if (inBounds(sx, y) && farFromSpawn(sx) && tiles[sx][y] == Tiles.AIR && Tiles.isSolid(tiles[sx][y - 1])
                        && tiles[sx][y - 1] != Tiles.CRUMBLE) tiles[sx][y] = Tiles.SPIKES;
                }
                placed++;
            }
        }
        // Surface spikes so the early game has some danger too (never in a 1-wide gap you can't jump)
        for (int x = 6; x < WIDTH - 6; x++) {
            if (!farFromSpawn(x) || rng.nextFloat() > 0.012f * density) continue;
            int y = surface[x] + 1;
            if (tiles[x][y] == Tiles.AIR && Tiles.isSolid(tiles[x][surface[x]]) && tiles[x][surface[x]] != Tiles.SAND) tiles[x][y] = Tiles.SPIKES;
        }
        // Crumbling platforms: short ledges over cave gaps
        for (int i = 0; i < 12 + level * 3; i++) {
            int x = 6 + rng.nextInt(WIDTH - 12);
            if (!farFromSpawn(x)) continue;
            int y = 8 + rng.nextInt(Math.max(1, surface[x] - 16));
            if (tiles[x][y] == Tiles.AIR && tiles[x][y - 1] == Tiles.AIR) {
                int len = 2 + rng.nextInt(3);
                for (int k = 0; k < len; k++) if (inBounds(x + k, y) && farFromSpawn(x + k) && tiles[x + k][y] == Tiles.AIR) tiles[x + k][y] = Tiles.CRUMBLE;
            }
        }
        // Arrow traps: embedded in cave walls, facing an open corridor
        for (int i = 0; i < 300 && arrowTraps.size() < 5 + level * 2; i++) {
            int x = 6 + rng.nextInt(WIDTH - 12);
            if (!farFromSpawn(x)) continue;
            int y = 6 + rng.nextInt(Math.max(1, surface[x] - 14));
            if (tiles[x][y] != Tiles.STONE) continue;
            int dir = 0;
            if (tiles[x + 1][y] == Tiles.AIR && tiles[x + 2][y] == Tiles.AIR && tiles[x + 3][y] == Tiles.AIR) dir = 1;
            else if (tiles[x - 1][y] == Tiles.AIR && tiles[x - 2][y] == Tiles.AIR && tiles[x - 3][y] == Tiles.AIR) dir = -1;
            if (dir == 0) continue;
            tiles[x][y] = Tiles.ARROW_TRAP;
            arrowTraps.add(new int[]{x, y, dir});
        }
        // Falling rocks: a stone with air directly below, in a passable tunnel
        for (int i = 0; i < 400 && fallingRocks.size() < 6 + level * 2; i++) {
            int x = 6 + rng.nextInt(WIDTH - 12);
            if (!farFromSpawn(x)) continue;
            int y = 8 + rng.nextInt(Math.max(1, surface[x] - 18));
            if (tiles[x][y] == Tiles.STONE && tiles[x][y - 1] == Tiles.AIR && tiles[x][y - 2] == Tiles.AIR) {
                fallingRocks.add(new int[]{x, y});
            }
        }
    }

    /** Scan downward from startY to the first air tile that sits on a solid tile. */
    private int findFloor(int x, int startY) {
        for (int y = Math.min(startY, HEIGHT - 2); y > 2; y--) {
            if (tiles[x][y] == Tiles.AIR && Tiles.isSolid(tiles[x][y - 1])) return y;
        }
        return -1;
    }

    // ------------------------------------------------------------------ access

    public boolean inBounds(int x, int y) { return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT; }

    public int get(int x, int y) { return inBounds(x, y) ? tiles[x][y] : Tiles.AIR; }

    public void set(int x, int y, int type) {
        if (!inBounds(x, y)) return;
        tiles[x][y] = type;
        mining[x][y] = 0f;
    }

    public void remove(int x, int y) { set(x, y, Tiles.AIR); }

    /** Legacy-compatible alias. */
    public int getBlockType(int x, int y) { return get(x, y); }

    public boolean isSolid(int x, int y) {
        if (y < 0) return true;                       // bottom of the world
        if (x < 0 || x >= WIDTH) return true;         // side walls
        if (y >= HEIGHT) return false;
        return Tiles.isSolid(tiles[x][y]);
    }

    public int surfaceY(int x) { return surface[MathUtils.clamp(x, 0, WIDTH - 1)]; }

    public float getMining(int x, int y) { return inBounds(x, y) ? mining[x][y] : 0f; }
    public void setMining(int x, int y, float v) { if (inBounds(x, y)) mining[x][y] = v; }

    public int[][] rawTiles() { return tiles; }

    /** Copies a full tile array in (used by load). */
    public void loadTiles(int[][] src) {
        for (int x = 0; x < WIDTH; x++) System.arraycopy(src[x], 0, tiles[x], 0, HEIGHT);
    }
}
