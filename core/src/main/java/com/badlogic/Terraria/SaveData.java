package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.io.*;

public class SaveData {
    private static final String FILE = "savegame.bin";
    private static final int MAGIC = 0xCA7E0001;
    private static final String PREFS = "cave_runner";

    public static boolean exists() {
        try {
            return Gdx.files != null && Gdx.files.local(FILE).exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static void delete() {
        try {
            if (exists()) Gdx.files.local(FILE).delete();
        } catch (Exception ignored) {
        }
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

    public static void write(OutputStream os, GameWorld g) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(os))) {
            out.writeInt(MAGIC);
            out.writeLong(g.world.seed);
            out.writeInt(g.level);
            out.writeFloat(g.timeOfDay);
            out.writeFloat(g.playTime);
            out.writeBoolean(g.bossSpawned);
            out.writeBoolean(g.bossDefeated);

            Player p = g.player;
            out.writeFloat(p.position.x);
            out.writeFloat(p.position.y);
            out.writeFloat(p.getSpawn().x);
            out.writeFloat(p.getSpawn().y);
            out.writeInt(p.health);
            out.writeInt(p.lives);
            out.writeInt(p.score);
            out.writeInt(p.coins);
            out.writeInt(p.inventory.selected);
            for (int i = 0; i < Inventory.SIZE; i++) {
                out.writeInt(p.inventory.ids[i]);
                out.writeInt(p.inventory.counts[i]);
            }

            int[][] t = g.world.rawTiles();
            int run = 0, cur = -1, pairs = 0;
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            DataOutputStream rl = new DataOutputStream(tmp);
            for (int x = 0; x < World.WIDTH; x++) {
                for (int y = 0; y < World.HEIGHT; y++) {
                    int v = t[x][y];
                    if (v == cur && run < 65535) {
                        run++;
                    } else {
                        if (run > 0) {
                            rl.writeShort(run);
                            rl.writeByte(cur);
                            pairs++;
                        }
                        cur = v;
                        run = 1;
                    }
                }
            }
            if (run > 0) {
                rl.writeShort(run);
                rl.writeByte(cur);
                pairs++;
            }
            out.writeInt(pairs);
            out.write(tmp.toByteArray());
        }
    }

    public static GameWorld load(TextureRegion playerTex) {
        try {
            if (!exists()) return null;
            return read(Gdx.files.local(FILE).read(), playerTex);
        } catch (Exception e) {
            return null;
        }
    }

    public static GameWorld read(InputStream is, TextureRegion playerTex) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(is))) {
            if (in.readInt() != MAGIC) throw new IOException("bad save file");
            long seed = in.readLong();
            int level = in.readInt();
            if (level < 1 || level > Config.MAX_LEVEL) throw new IOException("bad level");
            float tod = in.readFloat();
            float playTime = in.readFloat();
            boolean bossSpawned = in.readBoolean();
            boolean bossDefeated = in.readBoolean();
            float px = in.readFloat(), py = in.readFloat();
            float sx = in.readFloat(), sy = in.readFloat();
            int hp = in.readInt(), lives = in.readInt();
            int score = in.readInt(), coins = in.readInt(), sel = in.readInt();
            if (sel < 0 || sel >= Inventory.HOTBAR) throw new IOException("bad selected slot");
            if (hp < 0 || lives < 0 || score < 0 || coins < 0) throw new IOException("bad player stats");

            int[] ids = new int[Inventory.SIZE];
            int[] counts = new int[Inventory.SIZE];
            for (int i = 0; i < Inventory.SIZE; i++) {
                ids[i] = in.readInt();
                counts[i] = in.readInt();
                if (ids[i] < 0 || ids[i] >= Tiles.COUNT && ids[i] < 100) throw new IOException("bad inventory id");
                if (ids[i] == 0 && counts[i] != 0) throw new IOException("bad inventory count");
                if (ids[i] != 0 && (counts[i] < 1 || counts[i] > Inventory.MAX_STACK)) throw new IOException("bad inventory count");
            }

            int pairs = in.readInt();
            if (pairs < 1 || pairs > World.WIDTH * World.HEIGHT) throw new IOException("bad tile data");
            int[][] tiles = new int[World.WIDTH][World.HEIGHT];
            int idx = 0;
            for (int i = 0; i < pairs; i++) {
                int run = in.readUnsignedShort();
                int v = in.readUnsignedByte();
                if (v >= Tiles.COUNT) throw new IOException("bad tile id");
                for (int k = 0; k < run; k++) {
                    if (idx >= World.WIDTH * World.HEIGHT) throw new IOException("tile overflow");
                    tiles[idx / World.HEIGHT][idx % World.HEIGHT] = v;
                    idx++;
                }
            }
            if (idx != World.WIDTH * World.HEIGHT) throw new IOException("tile underflow");

            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(sx) || !Float.isFinite(sy)
                || !Float.isFinite(tod) || !Float.isFinite(playTime)) throw new IOException("bad position");

            World world = new World(seed, level);
            world.loadTiles(tiles);
            Player p = new Player(px, py, playerTex);
            p.setSpawn(sx, sy);
            p.health = Math.max(1, Math.min(Config.MAX_HEALTH, hp));
            p.lives = Math.max(1, lives);
            p.score = score;
            p.coins = coins;
            p.velocity.setZero();
            p.invincible = 2f;
            p.inventory.clear();
            for (int i = 0; i < Inventory.SIZE; i++) {
                p.inventory.ids[i] = ids[i];
                p.inventory.counts[i] = counts[i];
            }
            p.inventory.select(sel);

            GameWorld g = new GameWorld(world, p);
            g.timeOfDay = tod;
            g.playTime = playTime;
            g.bossSpawned = bossSpawned;
            g.bossDefeated = bossDefeated;
            if (!bossSpawned || bossDefeated) {
                g.enemies.removeIf(enemy -> enemy.type == Enemy.BOSS);
                g.boss = null;
            }
            return g;
        }
    }

    private static Preferences prefs() {
        return Gdx.app.getPreferences(PREFS);
    }

    public static int[] highScores() {
        int[] s = new int[5];
        try {
            Preferences p = prefs();
            for (int i = 0; i < 5; i++) s[i] = p.getInteger("hs" + i, 0);
        } catch (Exception ignored) {
        }
        return s;
    }

    public static int submitScore(int score) {
        if (score <= 0) return -1;
        int[] s = highScores();
        int rank = -1;
        for (int i = 0; i < s.length; i++) {
            if (score > s[i]) {
                rank = i;
                break;
            }
        }
        if (rank < 0) return -1;
        for (int i = s.length - 1; i > rank; i--) s[i] = s[i - 1];
        s[rank] = score;
        try {
            Preferences p = prefs();
            for (int i = 0; i < s.length; i++) p.putInteger("hs" + i, s[i]);
            p.flush();
        } catch (Exception ignored) {
        }
        return rank;
    }

    public static boolean musicOn() {
        try { return prefs().getBoolean("music", true); } catch (Exception e) { return true; }
    }

    public static boolean sfxOn() {
        try { return prefs().getBoolean("sfx", true); } catch (Exception e) { return true; }
    }

    public static void setMusic(boolean on) {
        try { Preferences p = prefs(); p.putBoolean("music", on); p.flush(); } catch (Exception ignored) { }
    }

    public static void setSfx(boolean on) {
        try { Preferences p = prefs(); p.putBoolean("sfx", on); p.flush(); } catch (Exception ignored) { }
    }
}
