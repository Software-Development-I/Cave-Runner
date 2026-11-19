package com.badlogic.Terraria;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Fixed-size particle pool: zero allocations after construction. */
public class Particles {
    private static final int MAX = 600;
    private final float[] px = new float[MAX], py = new float[MAX], pvx = new float[MAX], pvy = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX], size = new float[MAX];
    private final float[] r = new float[MAX], g = new float[MAX], b = new float[MAX];
    private final boolean[] gravity = new boolean[MAX];
    private int next = 0;

    private int slot() { int s = next; next = (next + 1) % MAX; return s; }

    public void burst(float x, float y, int count, float speed, float lifeSec, float cr, float cg, float cb, boolean grav) {
        for (int i = 0; i < count; i++) {
            int s = slot();
            float ang = MathUtils.random(0f, MathUtils.PI2), sp = MathUtils.random(0.3f, 1f) * speed;
            px[s] = x; py[s] = y; pvx[s] = MathUtils.cos(ang) * sp; pvy[s] = MathUtils.sin(ang) * sp + (grav ? speed * 0.3f : 0);
            life[s] = maxLife[s] = lifeSec * MathUtils.random(0.6f, 1f);
            size[s] = MathUtils.random(1.5f, 3f);
            r[s] = MathUtils.clamp(cr + MathUtils.random(-0.1f, 0.1f), 0, 1);
            g[s] = MathUtils.clamp(cg + MathUtils.random(-0.1f, 0.1f), 0, 1);
            b[s] = MathUtils.clamp(cb + MathUtils.random(-0.1f, 0.1f), 0, 1);
            gravity[s] = grav;
        }
    }

    public void blockBreak(float x, float y, int tileType) {
        float[] c = tileColor(tileType);
        burst(x, y, 10, 70f, 0.6f, c[0], c[1], c[2], true);
    }

    public static float[] tileColor(int t) {
        switch (t) {
            case Tiles.DIRT: return new float[]{0.45f, 0.30f, 0.18f};
            case Tiles.SAND: return new float[]{0.87f, 0.78f, 0.5f};
            case Tiles.STONE: case Tiles.CRUMBLE: case Tiles.ARROW_TRAP: return new float[]{0.55f, 0.55f, 0.58f};
            case Tiles.COAL: return new float[]{0.15f, 0.15f, 0.15f};
            case Tiles.IRON: return new float[]{0.84f, 0.63f, 0.47f};
            case Tiles.GOLD: return new float[]{0.98f, 0.84f, 0.24f};
            case Tiles.WOOD: case Tiles.PLANKS: return new float[]{0.6f, 0.4f, 0.2f};
            case Tiles.LEAVES: return new float[]{0.2f, 0.6f, 0.25f};
            default: return new float[]{0.7f, 0.7f, 0.7f};
        }
    }

    public void update(float dt) {
        for (int i = 0; i < MAX; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            if (gravity[i]) pvy[i] += Config.GRAVITY * 0.5f * dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
        }
    }

    public int aliveCount() { int n = 0; for (int i = 0; i < MAX; i++) if (life[i] > 0) n++; return n; }

    public void render(SpriteBatch batch, TextureRegion pixel) {
        Color old = batch.getColor();
        float oldPacked = old.toFloatBits();
        for (int i = 0; i < MAX; i++) {
            if (life[i] <= 0) continue;
            float a = MathUtils.clamp(life[i] / maxLife[i], 0f, 1f);
            batch.setColor(r[i], g[i], b[i], a);
            batch.draw(pixel, px[i], py[i], size[i], size[i]);
        }
        batch.setPackedColor(oldPacked);
    }
}
