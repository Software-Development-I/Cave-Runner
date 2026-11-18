package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Ordinary enemy behavior selected by type. */
public class Enemy {
    public static final int SLIME = 0, BAT = 1, SKELETON = 2, BOSS = 3;

    public final int type;
    public float x, y, vx, vy, w, h;
    public int hp, maxHp, damage;
    public boolean dead;
    public boolean facingRight = true;
    public float hurtTimer, aiTimer, phase;
    public int bossPhase = 1;
    public boolean bossDefeated;
    private boolean grounded;
    private final Rectangle rect = new Rectangle();
    private int level = 1;
    private float bossShotTimer;
    private float bossChargeTimer;
    private float bossJumpTimer;
    private boolean bossCharging;
    public final List<float[]> bossShots = new ArrayList<>();

    public Enemy(int type, float x, float y) {
        this(type, x, y, 1);
    }

    public Enemy(int type, float x, float y, int level) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.level = level;
        phase = MathUtils.random(0f, 6.28f);
        switch (type) {
            case SLIME:
                w = 14; h = 10; hp = Config.SLIME_HP; damage = Config.SLIME_DMG;
                break;
            case BAT:
                w = 14; h = 9; hp = Config.BAT_HP; damage = Config.BAT_DMG;
                break;
            case SKELETON:
                w = 11; h = 27; hp = Config.SKELETON_HP; damage = Config.SKELETON_DMG;
                break;
            default:
                w = 28; h = 34;
                hp = Config.BOSS_BASE_HP + (level - 1) * 45;
                damage = Config.BOSS_BASE_DAMAGE + (level - 1) * 5;
                bossShotTimer = Config.BOSS_SHOT_INTERVAL;
                break;
        }
        maxHp = hp;
    }

    public Rectangle bounds() { rect.set(x, y, w, h); return rect; }
    public float cx() { return x + w / 2f; }
    public float cy() { return y + h / 2f; }

    public void update(float dt, World world, Player player) {
        if (dead) return;
        if (hurtTimer > 0) hurtTimer -= dt;
        switch (type) {
            case SLIME: updateSlime(dt, world, player); break;
            case BAT: updateBat(dt, world, player); break;
            case SKELETON: updateSkeleton(dt, world, player); break;
            case BOSS: updateBoss(dt, world, player); break;
        }
    }

    private void updateSlime(float dt, World world, Player player) {
        aiTimer -= dt;
        if (grounded && aiTimer <= 0) {
            float dir = Math.abs(player.centerX() - cx()) < 100 && !player.dead
                ? Math.signum(player.centerX() - cx())
                : (MathUtils.randomBoolean() ? 1 : -1);
            vx = dir * 45f;
            vy = 170f;
            facingRight = dir > 0;
            aiTimer = MathUtils.random(0.9f, 1.8f);
        }
        if (grounded && aiTimer > 0.5f && vy <= 0) vx *= 0.9f;
        gravityMove(dt, world);
    }

    private void updateBat(float dt, World world, Player player) {
        float dx = player.centerX() - cx();
        float dy = player.centerY() - cy();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        phase += dt * 6f;
        if (dist < 8 * World.TILE_SIZE && !player.dead) {
            float speed = 70f;
            vx = dx / Math.max(dist, 1f) * speed;
            vy = dy / Math.max(dist, 1f) * speed + MathUtils.sin(phase) * 45f;
            facingRight = vx > 0;
        } else {
            vx *= 0.9f;
            vy = MathUtils.sin(phase) * 20f;
        }
        moveAxis(true, vx * dt, world);
        moveAxis(false, vy * dt, world);
    }

    private void updateSkeleton(float dt, World world, Player player) {
        float dx = player.centerX() - cx();
        boolean chase = Math.abs(dx) < 12 * World.TILE_SIZE
            && Math.abs(player.centerY() - cy()) < 5 * World.TILE_SIZE
            && !player.dead;
        float dir = chase ? Math.signum(dx) : (aiTimer > 0 ? (facingRight ? 1 : -1) : 0);
        aiTimer -= dt;
        if (!chase && aiTimer <= -1.5f) {
            aiTimer = MathUtils.random(1f, 2.5f);
            facingRight = MathUtils.randomBoolean();
        }
        if (dir != 0) {
            int aheadX = MathUtils.floor((cx() + dir * (w / 2f + 4f)) / World.TILE_SIZE);
            int footY = MathUtils.floor((y - 2f) / World.TILE_SIZE);
            boolean ledge = grounded && !world.isSolid(aheadX, footY);
            int bodyY = MathUtils.floor((y + 4f) / World.TILE_SIZE);
            boolean wall = world.isSolid(aheadX, bodyY);
            if (ledge || wall) {
                if (wall && grounded && !world.isSolid(aheadX, bodyY + 2)) {
                    vy = 190f;
                } else {
                    dir = 0;
                    facingRight = !facingRight;
                    aiTimer = 1f;
                }
            }
        }
        vx = dir * 52f;
        if (dir != 0) facingRight = dir > 0;
        gravityMove(dt, world);
    }

    private void updateBoss(float dt, World world, Player player) {
        float hpFraction = hp / (float) maxHp;
        int nextPhase = hpFraction <= 1f / 3f ? 3 : hpFraction <= 2f / 3f ? 2 : 1;
        if (nextPhase != bossPhase) {
            bossPhase = nextPhase;
            bossCharging = false;
            bossJumpTimer = 0.35f;
            bossShotTimer = 0.25f;
        }

        float dx = player.centerX() - cx();
        if (!player.dead) facingRight = dx >= 0;
        bossShotTimer -= dt;
        bossJumpTimer -= dt;
        bossChargeTimer -= dt;

        if (!bossCharging && bossPhase == 1 && grounded) {
            vx = Math.signum(dx) * 58f;
        }

        if (bossPhase >= 2 && bossJumpTimer <= 0 && grounded) {
            vy = 210f + bossPhase * 20f;
            vx = Math.signum(dx) * (90f + bossPhase * 15f);
            bossJumpTimer = bossPhase == 3 ? 1.2f : 1.8f;
            grounded = false;
        }

        if (bossPhase >= 2 && bossChargeTimer <= 0 && grounded) {
            bossCharging = true;
            vx = Math.signum(dx) * Config.BOSS_CHARGE_SPEED;
            bossChargeTimer = bossPhase == 3 ? 1.25f : 1.8f;
        }
        if (bossCharging) {
            vx = Math.signum(facingRight ? 1 : -1) * Config.BOSS_CHARGE_SPEED;
            if (bossChargeTimer < (bossPhase == 3 ? 0.55f : 0.85f)) bossCharging = false;
        }

        if (bossShotTimer <= 0 && !player.dead) {
            float direction = Math.signum(dx);
            float px = cx() + direction * 12f;
            float py = cy();
            bossShots.add(new float[]{px, py, direction * Config.BOSS_PROJECTILE_SPEED,
                (player.centerY() - py) * 0.45f, damage});
            if (bossPhase == 3) {
                bossShots.add(new float[]{px, py, direction * Config.BOSS_PROJECTILE_SPEED,
                    55f, damage});
                bossShots.add(new float[]{px, py, direction * Config.BOSS_PROJECTILE_SPEED,
                    -55f, damage});
            }
            bossShotTimer = bossPhase == 3 ? Config.BOSS_SHOT_INTERVAL * 0.65f
                : bossPhase == 2 ? Config.BOSS_SHOT_INTERVAL * 0.82f
                : Config.BOSS_SHOT_INTERVAL;
        }

        gravityMove(dt, world);
    }

    private void gravityMove(float dt, World world) {
        vy += Config.GRAVITY * dt;
        if (vy < Config.MAX_FALL) vy = Config.MAX_FALL;
        moveAxis(true, vx * dt, world);
        grounded = false;
        moveAxis(false, vy * dt, world);
        if (y < 2) {
            y = 2;
            vy = 0;
            grounded = true;
        }
    }

    private void moveAxis(boolean horizontal, float amount, World world) {
        if (amount == 0) return;
        if (horizontal) x += amount;
        else y += amount;

        int ts = World.TILE_SIZE;
        int x0 = MathUtils.floor(x / ts), x1 = MathUtils.floor((x + w - 0.01f) / ts);
        int y0 = MathUtils.floor(y / ts), y1 = MathUtils.floor((y + h - 0.01f) / ts);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (!world.isSolid(tx, ty)) continue;
                if (horizontal) {
                    if (amount > 0) x = tx * ts - w;
                    else x = (tx + 1) * ts;
                    vx = 0;
                } else {
                    if (amount < 0) {
                        y = (ty + 1) * ts;
                        grounded = true;
                    } else {
                        y = ty * ts - h;
                    }
                    vy = 0;
                }
                return;
            }
        }
    }

    public boolean hurt(int damageAmount, float knockX, float knockY) {
        hp -= damageAmount;
        hurtTimer = 0.2f;
        vx = knockX;
        vy = knockY;
        grounded = false;
        if (hp <= 0) {
            hp = 0;
            dead = true;
            bossDefeated = type == BOSS;
            return true;
        }
        if (type == BOSS) {
            bossPhase = hp / (float) maxHp <= 1f / 3f ? 3
                : hp / (float) maxHp <= 2f / 3f ? 2 : 1;
        }
        return false;
    }

    public String textureName() {
        switch (type) {
            case SLIME: return "slime";
            case BAT: return "bat";
            case SKELETON: return "skeleton";
            case BOSS: return "boss";
            default: return "dirt";
        }
    }
}
