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

        public void reset() {
            moveX = 0;
            jumpPressed = false;
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

    // ---- new this module: a hardcoded tier-1 pickaxe ----
    public int pickTier = 1;

    public float miningPower() {
        return 1f + (pickTier - 1) * 0.9f;
    }

    private boolean wasGrounded;
    private final Rectangle bounds = new Rectangle();
    private final Vector2 spawnPoint = new Vector2();
    private final TextureRegion texture;

    public Player(float x, float y, TextureRegion texture) {
        this.texture = texture;
        setSpawn(x, y);
        position.set(spawnPoint);
    }

    public void setSpawn(float x, float y) {
        spawnPoint.set(x, y);
    }

    public void respawnAtSpawn() {
        position.set(spawnPoint);
        velocity.setZero();
    }

    public void update(float dt, World world, Controls controls) {
        justLanded = false;
        dt = Math.min(dt, 1 / 30f);

        handleInput(controls);
        applyForces(dt);

        position.x += velocity.x * dt;
        bounds.set(position.x, position.y, width, height);
        collideX(world);

        float vyBefore = velocity.y;
        position.y += velocity.y * dt;
        bounds.set(position.x, position.y, width, height);
        collideY(world, vyBefore);

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
