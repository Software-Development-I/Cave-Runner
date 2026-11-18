package com.badlogic.Terraria;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Pure movement and collision for the player. */
public class Player {
    public static class Controls {
        public float moveX;
        public boolean jumpPressed;
        public boolean attackPressed;

        public void reset() {
            moveX = 0;
            jumpPressed = false;
            attackPressed = false;
        }
    }

    public final Vector2 position = new Vector2();
    public final Vector2 velocity = new Vector2();
    public final float width = Config.PLAYER_W;
    public final float height = Config.PLAYER_H;

    public boolean grounded;
    public boolean touchingWallLeft;
    public boolean touchingWallRight;
    public boolean facingRight = true;
    public boolean justLanded;
    public float lastLandSpeed;

    // ---- Inventory-owned tool tiers ----
    public final Inventory inventory = new Inventory();

    public float miningPower() {
        return 1f + inventory.pickTier() * 0.9f;
    }

    public int attackDamage() {
        return 5 + inventory.swordTier() * 8;
    }

    private boolean wasGrounded;
    private final Rectangle bounds = new Rectangle();
    private final Vector2 spawnPoint = new Vector2();
    private final TextureRegion texture;

    public int health = Config.MAX_HEALTH;
    public int lives = Config.START_LIVES;
    public int score = 0;
    public int coins = 0;
    public int comboMultiplier = 1;
    public float comboTimer = 0f;
    public boolean dead = false;
    public float invincible = 0f;
    public float attackCooldown = 0f;
    public float attackAnim = 0f;
    public boolean inLava;
    public boolean tookDamageThisFrame;
    private float lavaDamageAccum;
    private final Rectangle attackBox = new Rectangle();

    public Player(float x, float y, TextureRegion texture) {
        this.texture = texture;
        setSpawn(x, y);
        position.set(spawnPoint);
    }

    public void setSpawn(float x, float y) {
        spawnPoint.set(x, y);
    }

    public Vector2 getSpawn() {
        return spawnPoint;
    }

    public void respawnAtSpawn() {
        position.set(spawnPoint);
        velocity.setZero();
        health = Config.MAX_HEALTH;
        dead = false;
        invincible = 1.5f;
        inLava = false;
    }

    public void update(float dt, World world, Controls controls) {
        justLanded = false;
        tookDamageThisFrame = false;
        if (dead) return;
        dt = Math.min(dt, 1 / 30f);

        if (invincible > 0) invincible -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;
        if (attackAnim > 0) attackAnim -= dt;
        if (comboTimer > 0) {
            comboTimer -= dt;
            if (comboTimer <= 0) comboMultiplier = 1;
        }

        handleInput(controls);
        applyForces(dt);

        position.x += velocity.x * dt * (inLava ? Config.LAVA_SLOW : 1f);
        bounds.set(position.x, position.y, width, height);
        collideX(world);

        float vyBefore = velocity.y;
        position.y += velocity.y * dt * (inLava ? Config.LAVA_SLOW : 1f);
        bounds.set(position.x, position.y, width, height);
        collideY(world, vyBefore);

        checkHazards(world, dt);
        position.x = MathUtils.clamp(position.x, 0,
            World.WIDTH * World.TILE_SIZE - width);
    }

    private void handleInput(Controls controls) {
        velocity.x = controls.moveX * Config.MOVE_SPEED;
        if (controls.moveX > 0.1f) {
            facingRight = true;
        } else if (controls.moveX < -0.1f) {
            facingRight = false;
        }

        if (controls.jumpPressed) {
            if (grounded) {
                velocity.y = Config.JUMP_VEL;
                grounded = false;
            } else if (touchingWallLeft) {
                velocity.y = Config.WALL_JUMP_Y;
                velocity.x = Config.WALL_JUMP_X;
                facingRight = true;
            } else if (touchingWallRight) {
                velocity.y = Config.WALL_JUMP_Y;
                velocity.x = -Config.WALL_JUMP_X;
                facingRight = false;
            }
        }
    }

    private void applyForces(float dt) {
        velocity.y += Config.GRAVITY * dt;
        if (inLava) velocity.y = Math.max(velocity.y, -90f);
        if (!grounded && (touchingWallLeft || touchingWallRight)
            && velocity.y < Config.WALL_SLIDE_MAX) {
            velocity.y = Config.WALL_SLIDE_MAX;
        }
        if (velocity.y < Config.MAX_FALL) {
            velocity.y = Config.MAX_FALL;
        }
    }

    private void collideX(World world) {
        touchingWallLeft = false;
        touchingWallRight = false;
        int ts = World.TILE_SIZE;
        int startY = MathUtils.floor(bounds.y / ts);
        int endY = MathUtils.floor((bounds.y + bounds.height - 0.01f) / ts);

        if (velocity.x != 0) {
            int startX = MathUtils.floor(bounds.x / ts);
            int endX = MathUtils.floor((bounds.x + bounds.width - 0.01f) / ts);
            outer:
            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    if (world.isSolid(x, y)) {
                        if (velocity.x > 0) {
                            position.x = x * ts - bounds.width;
                        } else {
                            position.x = (x + 1) * ts;
                        }
                        velocity.x = 0;
                        bounds.setPosition(position.x, position.y);
                        break outer;
                    }
                }
            }
        }

        int leftTile = MathUtils.floor((bounds.x - 1f) / ts);
        int rightTile = MathUtils.floor((bounds.x + bounds.width + 0.5f) / ts);
        for (int y = startY; y <= endY; y++) {
            if (world.isSolid(leftTile, y)) {
                touchingWallLeft = true;
            }
            if (world.isSolid(rightTile, y)) {
                touchingWallRight = true;
            }
        }
    }

    private void collideY(World world, float vyBefore) {
        wasGrounded = grounded;
        grounded = false;
        int ts = World.TILE_SIZE;
        int startX = MathUtils.floor(bounds.x / ts);
        int endX = MathUtils.floor((bounds.x + bounds.width - 0.01f) / ts);
        int startY = MathUtils.floor(bounds.y / ts);
        int endY = MathUtils.floor((bounds.y + bounds.height - 0.01f) / ts);

        if (velocity.y <= 0) {
            for (int y = endY; y >= startY; y--) {
                for (int x = startX; x <= endX; x++) {
                    if (world.isSolid(x, y)) {
                        position.y = (y + 1) * ts;
                        velocity.y = 0;
                        grounded = true;
                        bounds.setPosition(position.x, position.y);
                        if (!wasGrounded) {
                            justLanded = true;
                            lastLandSpeed = -vyBefore;
                            applyFallDamage(-vyBefore);
                        }
                        return;
                    }
                }
            }
        } else {
            for (int y = startY; y <= endY; y++) {
                for (int x = startX; x <= endX; x++) {
                    if (world.isSolid(x, y)) {
                        position.y = y * ts - bounds.height;
                        velocity.y = 0;
                        bounds.setPosition(position.x, position.y);
                        return;
                    }
                }
            }
        }
    }

    private void applyFallDamage(float speed) {
        if (speed > Config.FALL_DAMAGE_SPEED) {
            int damage = Math.round((speed - Config.FALL_DAMAGE_SPEED)
                * Config.FALL_DAMAGE_PER_SPEED) + 5;
            damage(damage, 0, 0, true);
        }
    }

    private void checkHazards(World world, float dt) {
        int ts = World.TILE_SIZE;
        int x0 = MathUtils.floor((position.x + 1f) / ts);
        int x1 = MathUtils.floor((position.x + width - 1f) / ts);
        int y0 = MathUtils.floor((position.y + 1f) / ts);
        int y1 = MathUtils.floor((position.y + height - 1f) / ts);
        boolean lava = false;
        boolean spikes = false;

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                int tile = world.get(x, y);
                if (tile == Tiles.LAVA) lava = true;
                else if (tile == Tiles.SPIKES) spikes = true;
            }
        }

        inLava = lava;
        if (lava) {
            lavaDamageAccum += Config.LAVA_DPS * dt;
            if (lavaDamageAccum >= 1f) {
                int damage = (int) lavaDamageAccum;
                lavaDamageAccum -= damage;
                damage(damage, 0, 0, true);
            }
        } else {
            lavaDamageAccum = 0f;
        }

        if (spikes && invincible <= 0f) {
            damage(Config.SPIKE_DAMAGE, 0, 0, false);
            velocity.y = 200f;
            grounded = false;
        }
    }

    public boolean damage(int amount, float knockX, float knockY, boolean ignoreInvincible) {
        if (dead || amount <= 0) return false;
        if (!ignoreInvincible && invincible > 0f) return false;

        health -= amount;
        tookDamageThisFrame = true;
        if (!ignoreInvincible || amount >= 5) {
            invincible = Math.max(invincible, Config.INVINCIBLE_TIME);
        }
        if (knockX != 0 || knockY != 0) {
            velocity.x = knockX;
            velocity.y = knockY;
            grounded = false;
        }
        comboMultiplier = 1;
        comboTimer = 0;
        if (health <= 0) {
            health = 0;
            dead = true;
        }
        return true;
    }

    public void heal(int amount) {
        health = Math.min(Config.MAX_HEALTH, health + amount);
    }

    public boolean loseLifeAndRespawn() {
        lives--;
        if (lives <= 0) {
            lives = 0;
            return false;
        }
        respawnAtSpawn();
        return true;
    }

    public void addScore(int base) {
        score += base * comboMultiplier;
        comboMultiplier = Math.min(Config.COMBO_MAX,
            comboMultiplier + (comboTimer > 0 ? 1 : 0));
        comboTimer = Config.COMBO_WINDOW;
    }

    public Rectangle tryAttack(Controls controls) {
        if (dead || !controls.attackPressed || attackCooldown > 0f) return null;
        attackCooldown = Config.ATTACK_COOLDOWN;
        attackAnim = 0.18f;
        float reach = Config.ATTACK_RANGE + inventory.swordTier() * 4f;
        float attackX = facingRight ? position.x + width : position.x - reach;
        attackBox.set(attackX, position.y + 2f, reach, height - 4f);
        return attackBox;
    }

    public Rectangle getBounds() {
        bounds.set(position.x, position.y, width, height);
        return bounds;
    }

    public float centerX() {
        return position.x + width / 2f;
    }

    public float centerY() {
        return position.y + height / 2f;
    }

    public void render(SpriteBatch batch) {
        if (dead) return;
        if (invincible > 0f && ((int) (invincible * 14f)) % 2 == 0) return;
        float drawH = height * 1.30f;
        float drawW = drawH * (texture.getRegionWidth()
            / (float) texture.getRegionHeight());
        float dx = position.x + width / 2f - drawW / 2f;
        float dy = position.y - 1f;
        if (facingRight) {
            batch.draw(texture, dx, dy, drawW, drawH);
        } else {
            batch.draw(texture, dx + drawW, dy, -drawW, drawH);
        }
    }
}
