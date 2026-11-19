package com.badlogic.Terraria;

import com.badlogic.gdx.math.Rectangle;

/** An arrow fired by a trap. Destroyed by solid tiles, damages the player on contact. */
public class Projectile {
    public float x, y, vx;
    public final float w = 10, h = 3;
    public boolean dead;
    public final int damage;
    private final Rectangle rect = new Rectangle();

    public Projectile(float x, float y, float vx, int damage) { this.x = x; this.y = y; this.vx = vx; this.damage = damage; }

    public void update(float dt, World world, Player p) {
        if (dead) return;
        x += vx * dt;
        int tx = (int) Math.floor((vx > 0 ? x + w : x) / World.TILE_SIZE);
        int ty = (int) Math.floor((y + h / 2f) / World.TILE_SIZE);
        if (world.isSolid(tx, ty)) { dead = true; return; }
        if (!p.dead && bounds().overlaps(p.getBounds())) {
            if (p.damage(damage, Math.signum(vx) * 120f, 120f, false)) { dead = true; }
        }
    }

    public Rectangle bounds() { rect.set(x, y, w, h); return rect; }
}
