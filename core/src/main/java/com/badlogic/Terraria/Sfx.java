package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class Sfx implements Disposable {
    public static final int RATE = 22050;

    private Sound jump, mine, place, hurt, coin, death, attack, kill, levelUp, craft, music;
    private long musicId = -1;
    public boolean sfxOn = true, musicOn = true;
    private boolean ready;

    public Sfx() {
        try {
            sfxOn = SaveData.sfxOn();
            musicOn = SaveData.musicOn();
            jump = make("jump", tone(0.14f, 320f, 640f, 0.35f, 0));
            mine = make("mine", noiseHit(0.09f, 0.35f));
            place = make("place", tone(0.08f, 180f, 120f, 0.4f, 1));
            hurt = make("hurt", tone(0.28f, 300f, 90f, 0.5f, 2));
            coin = make("coin", chirp(0.16f, 880f, 1320f));
            death = make("death", tone(0.7f, 400f, 60f, 0.55f, 2));
            attack = make("attack", noiseSweep(0.12f, 0.35f));
            kill = make("kill", tone(0.2f, 500f, 900f, 0.4f, 1));
            levelUp = make("levelup", arpeggio());
            craft = make("craft", chirp(0.18f, 500f, 750f));
            music = make("music", musicLoop());
            ready = true;
            applyMusic();
        } catch (Throwable t) {
            ready = false;
        }
    }

    private Sound make(String name, short[] pcm) throws IOException {
        FileHandle fh = Gdx.files.local("sfx_" + name + ".wav");
        fh.writeBytes(wav(pcm), false);
        return Gdx.audio.newSound(fh);
    }

    private void play(Sound s, float vol) {
        if (ready && sfxOn && s != null) s.play(vol);
    }

    public void jump() { play(jump, 0.5f); }
    public void mine() { play(mine, 0.5f); }
    public void place() { play(place, 0.6f); }
    public void hurt() { play(hurt, 0.8f); }
    public void coin() { play(coin, 0.6f); }
    public void death() { play(death, 0.9f); }
    public void attack() { play(attack, 0.6f); }
    public void kill() { play(kill, 0.7f); }
    public void levelUp() { play(levelUp, 0.8f); }
    public void craft() { play(craft, 0.7f); }

    public void setSfx(boolean on) {
        sfxOn = on;
        SaveData.setSfx(on);
    }

    public void setMusic(boolean on) {
        musicOn = on;
        SaveData.setMusic(on);
        applyMusic();
    }

    private void applyMusic() {
        if (!ready || music == null) return;
        if (musicOn) {
            if (musicId == -1) musicId = music.loop(0.25f);
        } else if (musicId != -1) {
            music.stop(musicId);
            musicId = -1;
        }
    }

    private static float wave(int kind, double phase) {
        double p = phase - Math.floor(phase);
        switch (kind) {
            case 1: return p < 0.5 ? 1f : -1f;
            case 2: return (float) (2 * p - 1);
            default: return (float) Math.sin(phase * 2 * Math.PI);
        }
    }

    static short[] tone(float dur, float f0, float f1, float vol, int kind) {
        int n = (int) (dur * RATE);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 + (f1 - f0) * t;
            phase += f / RATE;
            float env = (float) Math.pow(1 - t, 1.6) * Math.min(1f, i / 80f);
            out[i] = (short) (wave(kind, phase) * env * vol * 32000);
        }
        return out;
    }

    static short[] chirp(float dur, float f0, float f1) {
        int n = (int) (dur * RATE);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = i < n / 2 ? f0 : f1;
            phase += f / RATE;
            float env = (float) Math.pow(1 - t, 1.3) * Math.min(1f, i / 60f);
            out[i] = (short) (wave(1, phase) * env * 0.3f * 32000);
        }
        return out;
    }

    static short[] noiseHit(float dur, float vol) {
        int n = (int) (dur * RATE);
        short[] out = new short[n];
        Random r = new Random(3);
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.35f;
            out[i] = (short) (lp * (float) Math.pow(1 - t, 2.2) * vol * 32000);
        }
        return out;
    }

    static short[] noiseSweep(float dur, float vol) {
        int n = (int) (dur * RATE);
        short[] out = new short[n];
        Random r = new Random(9);
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float k = 0.1f + 0.6f * t;
            lp += (r.nextFloat() * 2 - 1 - lp) * k;
            out[i] = (short) (lp * (float) Math.sin(Math.PI * t) * vol * 32000);
        }
        return out;
    }

    static short[] arpeggio() {
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f};
        int per = (int) (0.11f * RATE);
        short[] out = new short[per * notes.length + (int) (0.3f * RATE)];
        for (int k = 0; k < notes.length; k++) {
            double phase = 0;
            int len = per + (k == notes.length - 1 ? (int) (0.3f * RATE) : 0);
            for (int i = 0; i < len; i++) {
                phase += notes[k] / RATE;
                float env = (float) Math.pow(1 - i / (float) len, 1.4);
                out[k * per + i] = (short) (wave(1, phase) * env * 0.28f * 32000);
            }
        }
        return out;
    }

    static short[] musicLoop() {
        float beat = 0.30f;
        float[] bass = {110f, 110f, 87.31f, 98f, 110f, 110f, 82.41f, 98f};
        float[][] chord = {{220, 261.63f, 329.63f}, {220, 261.63f, 329.63f},
            {174.61f, 220, 261.63f}, {196, 246.94f, 293.66f},
            {220, 261.63f, 329.63f}, {220, 261.63f, 329.63f},
            {164.81f, 207.65f, 246.94f}, {196, 246.94f, 293.66f}};
        int per = (int) (beat * 4 * RATE);
        short[] out = new short[per * bass.length];
        for (int bar = 0; bar < bass.length; bar++) {
            double bp = 0;
            for (int i = 0; i < per; i++) {
                float t = i / (float) per;
                bp += bass[bar] / RATE;
                float benv = (float) Math.pow(1 - (i % (per / 2)) / (float) (per / 2), 1.5);
                float v = wave(0, bp) * benv * 0.30f;
                int step = (int) (t * 8) % 3;
                double ap = (bar * per + i) * (double) chord[bar][step] / RATE;
                float aenv = (float) Math.pow(1 - ((i * 8) % per) / (float) per, 1.2);
                v += wave(0, ap) * aenv * 0.10f;
                out[bar * per + i] = (short) (v * 32000 * 0.7f);
            }
        }
        int fade = RATE / 50;
        for (int i = 0; i < fade; i++) {
            float f = i / (float) fade;
            out[i] = (short) (out[i] * f);
            int j = out.length - 1 - i;
            out[j] = (short) (out[j] * f);
        }
        return out;
    }

    static byte[] wav(short[] pcm) throws IOException {
        int dataLen = pcm.length * 2;
        ByteArrayOutputStream b = new ByteArrayOutputStream(44 + dataLen);
        b.write(new byte[]{'R','I','F','F'});
        le32(b, 36 + dataLen);
        b.write(new byte[]{'W','A','V','E','f','m','t',' '});
        le32(b, 16);
        le16(b, 1);
        le16(b, 1);
        le32(b, RATE);
        le32(b, RATE * 2);
        le16(b, 2);
        le16(b, 16);
        b.write(new byte[]{'d','a','t','a'});
        le32(b, dataLen);
        for (short s : pcm) le16(b, s);
        return b.toByteArray();
    }

    private static void le16(ByteArrayOutputStream b, int v) {
        b.write(v & 0xff);
        b.write((v >> 8) & 0xff);
    }

    private static void le32(ByteArrayOutputStream b, int v) {
        le16(b, v & 0xffff);
        le16(b, (v >>> 16) & 0xffff);
    }

    @Override
    public void dispose() {
        if (!ready) return;
        Sound[] all = {jump, mine, place, hurt, coin, death, attack, kill, levelUp, craft, music};
        for (Sound s : all) if (s != null) s.dispose();
    }
}
