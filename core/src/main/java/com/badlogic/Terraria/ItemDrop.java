package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;

/** A dropped block, coin or heart that falls, then is pulled toward the player when close. */
public class ItemDrop {
    public static final int COIN = -1, HEART = -2;

    public float x, y, vx, vy;
    public final int id;         // tile/item id, or COIN / HEART
    public final int amount;
    public float age;
    public boolean collected;
    private static final float SIZE = 8f;

    public ItemDrop(int id, int amount, float x, float y) {
        this.id = id; this.amount = amount; this.x = x; this.y = y;
        this.vx = MathUtils.random(-40f, 40f); this.vy = MathUtils.random(60f, 130f);
    }

    public float size() { return SIZE; }

    public void update(float dt, World world, Player p) {
        age += dt;
        float dx = p.centerX() - (x + SIZE / 2), dy = p.centerY() - (y + SIZE / 2);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (!p.dead && age > 0.35f && dist < 56f) {          // magnet
            float pull = 240f;
            vx = dx / Math.max(dist, 1f) * pull; vy = dy / Math.max(dist, 1f) * pull;
            x += vx * dt; y += vy * dt;
        } else {
            vy += Config.GRAVITY * dt; if (vy < -300f) vy = -300f;
            x += vx * dt;
            if (world.isSolid(MathUtils.floor((x + SIZE / 2) / World.TILE_SIZE), MathUtils.floor((y + SIZE / 2) / World.TILE_SIZE))) { x -= vx * dt; vx = 0; }
            y += vy * dt;
            int tx = MathUtils.floor((x + SIZE / 2) / World.TILE_SIZE), ty = MathUtils.floor(y / World.TILE_SIZE);
            if (vy <= 0 && world.isSolid(tx, ty)) { y = (ty + 1) * World.TILE_SIZE; vy = 0; vx *= 0.6f; }
            if (world.get(tx, MathUtils.floor((y + 2) / World.TILE_SIZE)) == Tiles.LAVA) collected = true; // burnt
        }
        if (dist < 9f && age > 0.15f) collected = true;
        if (y < -32f) collected = true;
    }
}
