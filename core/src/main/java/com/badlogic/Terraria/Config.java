package com.badlogic.Terraria;

/** Every gameplay balance number lives here so it is easy to tune. */
public final class Config {
    private Config() {}

    public static final String TITLE = "Cave Runner";

    public static final int TILE = 16;
    public static final int WORLD_W = 300;
    public static final int WORLD_H = 120;
    public static final int SURFACE_BASE = 70;
    public static final int SPAWN_X = WORLD_W / 2;
    public static final int SPAWN_SAFE_RADIUS = 8;

    public static final float PLAYER_W = TILE * 0.75f;
    public static final float PLAYER_H = TILE * 1.75f;
    public static final float MOVE_SPEED = 120f;
    public static final float JUMP_VEL = 240f;
    public static final float WALL_JUMP_Y = 220f;
    public static final float WALL_JUMP_X = 160f;
    public static final float GRAVITY = -600f;
    public static final float MAX_FALL = -400f;
    public static final float WALL_SLIDE_MAX = -100f;
    public static final int MAX_HEALTH = 100;
    public static final int START_LIVES = 3;
    public static final float INVINCIBLE_TIME = 1.0f;
    public static final float FALL_DAMAGE_SPEED = 300f;
    public static final float FALL_DAMAGE_PER_SPEED = 0.35f;
    public static final int HEART_HEAL = 25;

    public static final float ATTACK_COOLDOWN = 0.35f;
    public static final float ATTACK_RANGE = 26f;
    public static final int BASE_ATTACK_DAMAGE = 10;
    public static final float KNOCKBACK = 180f;

    public static final int SPIKE_DAMAGE = 20;
    public static final float LAVA_DPS = 25f;
    public static final float LAVA_SLOW = 0.5f;
    public static final float CRUMBLE_DELAY = 0.6f;
    public static final float CRUMBLE_RESPAWN = 5f;
    public static final float ARROW_INTERVAL = 2.5f;
    public static final float ARROW_SPEED = 200f;
    public static final int ARROW_DAMAGE = 15;
    public static final float ARROW_RANGE_TILES = 14f;
    public static final int ROCK_DAMAGE = 25;

    public static final int SLIME_HP = 20, SLIME_DMG = 10;
    public static final int BAT_HP = 12, BAT_DMG = 8;
    public static final int SKELETON_HP = 40, SKELETON_DMG = 15;
    public static final float ENEMY_TOUCH_KNOCK = 150f;
    public static final int ENEMY_CAP = 14;
    public static final float SPAWN_INTERVAL_DAY = 6f;
    public static final float SPAWN_INTERVAL_NIGHT = 2.5f;

    public static final int SCORE_DIRT = 10, SCORE_STONE = 25, SCORE_ORE = 50;
    public static final int SCORE_ENEMY = 100, SCORE_COIN = 20;
    public static final float COMBO_WINDOW = 3f;
    public static final int COMBO_MAX = 5;

    public static final float DAY_LENGTH = 120f;
    public static final float AUTOSAVE_INTERVAL = 60f;
    public static final int MAX_LEVEL = 5;
    public static final int REACH_TILES = 3;

    public static final int VIEW_W_TILES = 32;
    public static final int VIEW_H_TILES = 18;
    public static final float CAMERA_SMOOTH = 0.15f;
}
