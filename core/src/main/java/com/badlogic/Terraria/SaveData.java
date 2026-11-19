package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Persistence: the world/player is a compact binary file (Gdx.files.local, works on desktop and Android),
 * high scores + settings use libGDX Preferences.
 */
public class SaveData {
    private static final String FILE = "savegame.bin";
    private static final int MAGIC = 0xCA7E0001;
    private static final String PREFS = "cave_runner";

    // ------------------------------------------------------------------ world save

    public static boolean exists() {
        try { return Gdx.files != null && Gdx.files.local(FILE).exists(); } catch (Exception e) { return false; }
    }

    public static void delete() {
        try { if (exists()) Gdx.files.local(FILE).delete(); } catch (Exception ignored) { }
    }

    public static boolean save(GameWorld g) {
        try {
            FileHandle fh = Gdx.files.local(FILE);
            write(fh.write(false), g);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void write(OutputStream os, GameWorld g) throws java.io.IOException {
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(os))) {
            out.writeInt(MAGIC);
            out.writeLong(g.world.seed);
            out.writeInt(g.level);
            out.writeFloat(g.timeOfDay);
            out.writeFloat(g.playTime);
            out.writeBoolean(g.bossSpawned);
            out.writeBoolean(g.bossDefeated);
            Player p = g.player;
            out.writeFloat(p.position.x); out.writeFloat(p.position.y);
            out.writeFloat(p.getSpawn().x); out.writeFloat(p.getSpawn().y);
            out.writeInt(p.health); out.writeInt(p.lives); out.writeInt(p.score); out.writeInt(p.coins);
            out.writeInt(p.inventory.selected);
            for (int i = 0; i < Inventory.SIZE; i++) { out.writeInt(p.inventory.ids[i]); out.writeInt(p.inventory.counts[i]); }
            // tiles: run-length encoded (worlds are mostly long runs)
            int[][] t = g.world.rawTiles();
            int run = 0, cur = -1;
            java.io.ByteArrayOutputStream tmp = new java.io.ByteArrayOutputStream();
            DataOutputStream rl = new DataOutputStream(tmp);
            int pairs = 0;
            for (int x = 0; x < World.WIDTH; x++) for (int y = 0; y < World.HEIGHT; y++) {
                int v = t[x][y];
                if (v == cur && run < 65535) run++;
                else { if (run > 0) { rl.writeShort(run); rl.writeByte(cur); pairs++; } cur = v; run = 1; }
            }
            if (run > 0) { rl.writeShort(run); rl.writeByte(cur); pairs++; }
            out.writeInt(pairs);
            out.write(tmp.toByteArray());
        }
    }

    /** Loads a saved game. Returns null if the file is missing or corrupt (never throws). */
    public static GameWorld load(com.badlogic.gdx.graphics.g2d.TextureRegion playerTex) {
        try {
            if (!exists()) return null;
            return read(Gdx.files.local(FILE).read(), playerTex);
        } catch (Exception e) {
            return null;
        }
    }

    public static GameWorld read(InputStream is, com.badlogic.gdx.graphics.g2d.TextureRegion playerTex) throws java.io.IOException {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(is))) {
            if (in.readInt() != MAGIC) throw new java.io.IOException("bad save file");
            long seed = in.readLong();
            int level = in.readInt();
            if (level < 1 || level > Config.MAX_LEVEL) throw new java.io.IOException("bad level");
            float tod = in.readFloat(), playTime = in.readFloat();
            boolean bossSpawned = in.readBoolean(), bossDefeated = in.readBoolean();
            float px = in.readFloat(), py = in.readFloat(), sx = in.readFloat(), sy = in.readFloat();
            int hp = in.readInt(), lives = in.readInt(), score = in.readInt(), coins = in.readInt(), sel = in.readInt();
            if (sel < 0 || sel >= Inventory.HOTBAR) throw new java.io.IOException("bad selected slot");
            if (hp < 0 || lives < 0 || score < 0 || coins < 0) throw new java.io.IOException("bad player stats");
            int[] ids = new int[Inventory.SIZE], counts = new int[Inventory.SIZE];
            for (int i = 0; i < Inventory.SIZE; i++) {
                ids[i] = in.readInt(); counts[i] = in.readInt();
                if (ids[i] < 0 || ids[i] >= Tiles.COUNT && ids[i] < 100) throw new java.io.IOException("bad inventory id");
                if (ids[i] == 0 && counts[i] != 0) throw new java.io.IOException("bad inventory count");
                if (ids[i] != 0 && (counts[i] < 1 || counts[i] > Inventory.MAX_STACK)) throw new java.io.IOException("bad inventory count");
            }
            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(sx) || !Float.isFinite(sy)
                || !Float.isFinite(tod) || !Float.isFinite(playTime)) throw new java.io.IOException("bad position");
            int pairs = in.readInt();
            if (pairs < 1 || pairs > World.WIDTH * World.HEIGHT) throw new java.io.IOException("bad tile data");
            int[][] tiles = new int[World.WIDTH][World.HEIGHT];
            int idx = 0;
            for (int i = 0; i < pairs; i++) {
                int run = in.readUnsignedShort(), v = in.readUnsignedByte();
                if (v >= Tiles.COUNT) throw new java.io.IOException("bad tile id");
                for (int k = 0; k < run; k++) {
                    if (idx >= World.WIDTH * World.HEIGHT) throw new java.io.IOException("tile overflow");
                    tiles[idx / World.HEIGHT][idx % World.HEIGHT] = v; idx++;
                }
            }
            if (idx != World.WIDTH * World.HEIGHT) throw new java.io.IOException("tile underflow");

            GameWorld g = new GameWorld(playerTex, seed, level);
            g.world.loadTiles(tiles);
            g.timeOfDay = tod; g.playTime = playTime; g.bossSpawned = bossSpawned; g.bossDefeated = bossDefeated;
            Player p = g.player;
            p.position.set(px, py); p.setSpawn(sx, sy);
            p.health = Math.max(1, Math.min(Config.MAX_HEALTH, hp)); p.lives = Math.max(1, lives);
            p.score = score; p.coins = coins; p.velocity.setZero(); p.invincible = 2f;
            p.inventory.clear();
            for (int i = 0; i < Inventory.SIZE; i++) { p.inventory.ids[i] = ids[i]; p.inventory.counts[i] = counts[i]; }
            p.inventory.select(sel);
            return g;
        }
    }

    // ------------------------------------------------------------------ high scores

    private static Preferences prefs() { return Gdx.app.getPreferences(PREFS); }

    public static int[] highScores() {
        int[] s = new int[5];
        try { Preferences p = prefs(); for (int i = 0; i < 5; i++) s[i] = p.getInteger("hs" + i, 0); } catch (Exception ignored) { }
        return s;
    }

    /** Inserts a score; returns its rank (0-based) or -1 if it did not make the top 5. */
    public static int submitScore(int score) {
        if (score <= 0) return -1;
        int[] s = highScores();
        return insert(s, score, true);
    }

    public static int insert(int[] s, int score, boolean persist) {
        int rank = -1;
        for (int i = 0; i < s.length; i++) if (score > s[i]) { rank = i; break; }
        if (rank < 0) return -1;
        for (int i = s.length - 1; i > rank; i--) s[i] = s[i - 1];
        s[rank] = score;
        if (persist) {
            try { Preferences p = prefs(); for (int i = 0; i < s.length; i++) p.putInteger("hs" + i, s[i]); p.flush(); } catch (Exception ignored) { }
        }
        return rank;
    }

    public static boolean musicOn() { try { return prefs().getBoolean("music", true); } catch (Exception e) { return true; } }
    public static boolean sfxOn() { try { return prefs().getBoolean("sfx", true); } catch (Exception e) { return true; } }
    public static void setMusic(boolean on) { try { Preferences p = prefs(); p.putBoolean("music", on); p.flush(); } catch (Exception ignored) { } }
    public static void setSfx(boolean on) { try { Preferences p = prefs(); p.putBoolean("sfx", on); p.flush(); } catch (Exception ignored) { } }
}
