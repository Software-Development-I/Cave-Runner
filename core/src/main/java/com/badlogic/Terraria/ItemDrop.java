package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;

/** A dropped block that pops out, falls, and magnetizes toward the player. */
public class ItemDrop {
    public float x, y, vx, vy;
    public final int id;
    public final int amount;
    public float age;
    public boolean collected;
    private static final float SIZE = 8f;

    public ItemDrop(int id, int amount, float x, float y) {
        this.id = id;
        this.amount = amount;
        this.x = x;
        this.y = y;
        vx = MathUtils.random(-40f, 40f);
        vy = MathUtils.random(60f, 130f);
    }

    public float size() {
        return SIZE;
    }

    public void update(float dt, World world, Player player) {
        age += dt;
        float dx = player.centerX() - (x + SIZE / 2f);
        float dy = player.centerY() - (y + SIZE / 2f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (age > 0.35f && dist < 56f) {
            float pull = 240f;
            vx = dx / Math.max(dist, 1f) * pull;
            vy = dy / Math.max(dist, 1f) * pull;
            x += vx * dt;
            y += vy * dt;
        } else {
            vy += Config.GRAVITY * dt;
            if (vy < -300f) {
                vy = -300f;
            }
            x += vx * dt;
            int horizontalTileX = MathUtils.floor((x + SIZE / 2f) / World.TILE_SIZE);
            int horizontalTileY = MathUtils.floor((y + SIZE / 2f) / World.TILE_SIZE);
            if (world.isSolid(horizontalTileX, horizontalTileY)) {
                x -= vx * dt;
                vx = 0;
            }
            y += vy * dt;
            int tx = MathUtils.floor((x + SIZE / 2f) / World.TILE_SIZE);
            int ty = MathUtils.floor(y / World.TILE_SIZE);
            if (vy <= 0 && world.isSolid(tx, ty)) {
                y = (ty + 1) * World.TILE_SIZE;
                vy = 0;
                vx *= 0.6f;
            }
        }

        if (dist < 9f && age > 0.15f) {
            collected = true;
        }
        if (y < -32f) {
            collected = true;
        }
    }
}
