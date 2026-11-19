package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** One class for all enemy kinds; behavior is switched on {@link #type}. */
public class Enemy {
    public static final int SLIME = 0, BAT = 1, SKELETON = 2, BOSS = 3;

    public final int type;
    public float x, y, vx, vy;
    public float w, h;
    public int hp, maxHp, damage;
    public boolean dead, active;
    /** True for slimes summoned by the boss, so the boss's own cap ignores wild slimes (and vice versa). */
    public boolean minion;
    public boolean facingRight = true;
    public float hurtTimer = 0f;
    public float aiTimer = 0f;
    public float phase;         // sine phase for bats
    public float baseY;
    private boolean grounded;

    // ---- boss state machine: CHASE -> WINDUP (telegraph) -> SLAM (shockwave) -> RECOVER ----
    public static final int B_CHASE = 0, B_WINDUP = 1, B_AIR = 2, B_RECOVER = 3;
    public int bossState = B_CHASE;
    public float bossTimer = 0f;
    public boolean enraged;
    /** Set for one frame when the boss lands a slam, so GameWorld can spawn the shockwave. */
    public boolean slamLanded;
    /** Set when the boss should spawn a minion this frame (GameWorld consumes it). */
    public boolean wantsMinion;
    private int slamsSinceMinion;
    /** Hops made since the last slam. The boss slams after HOPS_BEFORE_SLAM of them, so the rhythm is learnable and cannot stall. */
    private int hopsSinceSlam;
    /** How many hops the boss makes before its next slam (2 or 3), rolled once per cycle, never per hop. */
    private int hopsUntilSlam = 2;
    private final Rectangle rect = new Rectangle();

    public Enemy(int type, float x, float y) {
        this.type = type; this.x = x; this.y = y; this.baseY = y;
        this.phase = MathUtils.random(0f, 6.28f);
        switch (type) {
            case SLIME: w = 14; h = 10; hp = Config.SLIME_HP; damage = Config.SLIME_DMG; break;
            case BAT: w = 14; h = 9; hp = Config.BAT_HP; damage = Config.BAT_DMG; break;
            case SKELETON: w = 11; h = 27; hp = Config.SKELETON_HP; damage = Config.SKELETON_DMG; break;
            default: w = 46; h = 34; hp = Config.BOSS_HP; damage = Config.BOSS_DMG; break;
        }
        maxHp = hp;
    }

    public Rectangle bounds() { rect.set(x, y, w, h); return rect; }
    public float cx() { return x + w / 2f; }
    public float cy() { return y + h / 2f; }

    public void update(float dt, World world, Player p) {
        if (dead) return;
        if (hurtTimer > 0) hurtTimer -= dt;
        switch (type) {
            case SLIME: updateSlime(dt, world, p); break;
            case BAT: updateBat(dt, world, p); break;
            case SKELETON: updateSkeleton(dt, world, p); break;
            default: updateBoss(dt, world, p); break;
        }
    }

    // ---- behaviours ----

    private void updateSlime(float dt, World world, Player p) {
        aiTimer -= dt;
        if (grounded && aiTimer <= 0) {
            // hop toward the player when close, otherwise wander
            float dir = (Math.abs(p.centerX() - cx()) < 100 && !p.dead) ? Math.signum(p.centerX() - cx()) : (MathUtils.randomBoolean() ? 1 : -1);
            vx = dir * 45f; vy = 170f; facingRight = dir > 0; aiTimer = MathUtils.random(0.9f, 1.8f);
        }
        if (grounded && aiTimer > 0.5f && vy <= 0) vx *= 0.9f;
        gravityMove(dt, world);
    }

    private void updateBat(float dt, World world, Player p) {
        float dx = p.centerX() - cx(), dy = p.centerY() - cy();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        phase += dt * 6f;
        if (dist < 8 * World.TILE_SIZE && !p.dead) { // active: swoop toward the player
            float sp = 70f;
            vx = dx / Math.max(dist, 1f) * sp;
            vy = dy / Math.max(dist, 1f) * sp + MathUtils.sin(phase) * 45f;
            facingRight = vx > 0;
        } else {
            vx *= 0.9f; vy = MathUtils.sin(phase) * 20f;
        }
        // Bats fly, but still can't pass through solid tiles
        moveAxis(true, vx * dt, world);
        moveAxis(false, vy * dt, world);
    }

    private void updateSkeleton(float dt, World world, Player p) {
        float dx = p.centerX() - cx();
        boolean chase = Math.abs(dx) < 12 * World.TILE_SIZE && Math.abs(p.centerY() - cy()) < 5 * World.TILE_SIZE && !p.dead;
        float dir = chase ? Math.signum(dx) : (aiTimer > 0 ? (facingRight ? 1 : -1) : 0);
        aiTimer -= dt;
        if (!chase && aiTimer <= -1.5f) { aiTimer = MathUtils.random(1f, 2.5f); facingRight = MathUtils.randomBoolean(); }
        if (dir != 0) {
            // turn at ledges / walls: look one tile ahead and one tile below
            int aheadX = MathUtils.floor((cx() + dir * (w / 2f + 4f)) / World.TILE_SIZE);
            int footY = MathUtils.floor((y - 2f) / World.TILE_SIZE);
            boolean ledge = grounded && !world.isSolid(aheadX, footY);
            boolean wall = world.isSolid(aheadX, MathUtils.floor((y + 4f) / World.TILE_SIZE));
            if (ledge || wall) {
                if (wall && grounded && !world.isSolid(aheadX, MathUtils.floor((y + 4f) / World.TILE_SIZE) + 2)) vy = 190f; // hop a 1-tile step
                else { dir = 0; facingRight = !facingRight; aiTimer = 1f; }
            }
        }
        vx = dir * 52f;
        if (dir != 0) facingRight = dir > 0;
        gravityMove(dt, world);
    }

    private void updateBoss(float dt, World world, Player p) {
        slamLanded = false; wantsMinion = false;
        float dir = Math.signum(p.centerX() - cx());
        if (dir != 0) facingRight = dir > 0;
        if (!enraged && hp < maxHp / 2) { enraged = true; bossState = B_RECOVER; bossTimer = 0.8f; wantsMinion = true; }
        bossTimer -= dt;
        boolean wasGrounded = grounded;
        switch (bossState) {
            case B_CHASE:
                // Deterministic rhythm: hop, hop, (hop), SLAM. Closing distance also triggers it early, but never
                // a dice roll, so a player kiting at range still gets a slam every few seconds instead of a stall.
                if (grounded && bossTimer <= 0) {
                    boolean close = Math.abs(p.centerX() - cx()) < 96f;
                    if (hopsSinceSlam >= hopsUntilSlam || close) {
                        bossState = B_WINDUP; bossTimer = enraged ? 0.55f : 0.85f; vx = 0;
                    } else {
                        vx = dir * (enraged ? 95f : 65f); vy = enraged ? 210f : 180f;
                        bossTimer = enraged ? 0.55f : 0.85f;
                        hopsSinceSlam++;
                    }
                }
                if (grounded && bossTimer > 0.3f) vx *= 0.9f;
                break;
            case B_WINDUP:
                vx = 0;                                   // squat and flash: this is the player's warning
                if (bossTimer <= 0) {
                    bossState = B_AIR; vy = enraged ? 380f : 330f; vx = dir * (enraged ? 110f : 80f); grounded = false;
                }
                break;
            case B_AIR:
                if (grounded && vy <= 0) landSlam();
                break;
            default: // B_RECOVER: vulnerable pause, the player's opening to attack
                vx *= 0.8f;
                if (bossTimer <= 0) { bossState = B_CHASE; bossTimer = 0.2f; }
                break;
        }
        gravityMove(dt, world);
        // gravityMove may set 'grounded' after the switch above ran; catch that landing here, through the SAME routine
        if (bossState == B_AIR && grounded && !slamLanded && vy <= 0) landSlam();
    }

    /** The one and only place a slam lands: sets the shockwave flag, decides on a minion, starts the recovery. */
    private void landSlam() {
        slamLanded = true;
        hopsSinceSlam = 0; hopsUntilSlam = 2 + (MathUtils.randomBoolean() ? 1 : 0);
        slamsSinceMinion++;
        if (slamsSinceMinion >= (enraged ? 1 : 2)) { wantsMinion = true; slamsSinceMinion = 0; }
        bossState = B_RECOVER; bossTimer = enraged ? 0.5f : 0.9f; vx = 0;
    }

    /** 0..1 how "charged" the telegraph is, used by the renderer to squash/flash the boss. */
    public float windupFraction() {
        if (type != BOSS || bossState != B_WINDUP) return 0f;
        float total = enraged ? 0.55f : 0.85f;
        return MathUtils.clamp(1f - bossTimer / total, 0f, 1f);
    }

    // ---- physics ----

    private void gravityMove(float dt, World world) {
        vy += Config.GRAVITY * dt;
        if (vy < Config.MAX_FALL) vy = Config.MAX_FALL;
        moveAxis(true, vx * dt, world);
        grounded = false;
        moveAxis(false, vy * dt, world);
        if (y < 2) { y = 2; vy = 0; grounded = true; }
    }

    private void moveAxis(boolean horizontal, float amount, World world) {
        if (amount == 0) return;
        if (horizontal) x += amount; else y += amount;
        int ts = World.TILE_SIZE;
        int x0 = MathUtils.floor(x / ts), x1 = MathUtils.floor((x + w - 0.01f) / ts);
        int y0 = MathUtils.floor(y / ts), y1 = MathUtils.floor((y + h - 0.01f) / ts);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (!world.isSolid(tx, ty)) continue;
                if (horizontal) {
                    if (amount > 0) x = tx * ts - w; else x = (tx + 1) * ts;
                    vx = 0;
                } else {
                    if (amount < 0) { y = (ty + 1) * ts; grounded = true; } else y = ty * ts - h;
                    vy = 0;
                }
                return;
            }
        }
    }

    /** @return true if this hit killed the enemy. */
    public boolean hurt(int dmg, float knockX, float knockY) {
        hp -= dmg; hurtTimer = 0.2f;
        vx = knockX; vy = knockY; grounded = false;
        if (hp <= 0) { dead = true; return true; }
        return false;
    }

    public String textureName() {
        switch (type) { case SLIME: return "slime"; case BAT: return "bat"; case SKELETON: return "skeleton"; default: return "boss"; }
    }
}
