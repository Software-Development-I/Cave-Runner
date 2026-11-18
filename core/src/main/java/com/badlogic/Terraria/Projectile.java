package com.badlogic.Terraria;

import com.badlogic.gdx.math.Rectangle;

/** An arrow fired by an arrow trap. */
public class Projectile {
    public float x, y, vx;
    public float vy;
    public final float w = 10, h = 3;
    public boolean dead;
    public final int damage;
    private final Rectangle rect = new Rectangle();

    public Projectile(float x, float y, float vx, int damage) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.damage = damage;
    }

    public Projectile(float x, float y, float vx, float vy, int damage) {
        this(x, y, vx, damage);
        this.vy = vy;
    }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        x += vx * dt;
        y += vy * dt;
        int tx = (int) Math.floor((vx > 0 ? x + w : x) / World.TILE_SIZE);
        int ty = (int) Math.floor((y + h / 2f) / World.TILE_SIZE);
        if (world.isSolid(tx, ty)) {
            dead = true;
            return;
        }
        if (!player.dead && bounds().overlaps(player.getBounds())) {
            if (player.damage(damage, Math.signum(vx) * 120f, 120f, false)) {
                dead = true;
            }
        }
    }

    public Rectangle bounds() {
        rect.set(x, y, w, h);
        return rect;
    }
}
