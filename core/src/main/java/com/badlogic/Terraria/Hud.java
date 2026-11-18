package com.badlogic.Terraria;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** In-game health bar, hotbar, tutorial banner, and crafting menu. */
public class Hud implements Disposable {
    private final BitmapFont font = new BitmapFont();
    private final Assets assets;
    public boolean touchMode;
    public boolean inventoryOpen;
    public float uiScale = 1f;

    public Hud(Assets assets) { this.assets = assets; }

    public void resize(int w, int h) {
        uiScale = Math.max(0.8f, Math.min(2f, h / 720f));
    }

    public void render(SpriteBatch batch, GameWorld game, int sw, int sh) {
        drawHealthBar(batch, game.player, sw, sh);
        drawHotbar(batch, game.player.inventory, sw);
        if (game.tutorial.active()) drawTutorialBanner(batch, game.tutorial, sw, sh);
        if (inventoryOpen) drawCraftMenu(batch, game.player.inventory, sw, sh);
    }

    private void drawHealthBar(SpriteBatch batch, Player p, int sw, int sh) {
        TextureRegion pixel = assets.get("particle");
        float x = 12 * uiScale, y = sh - 28 * uiScale, w = 200 * uiScale, h = 16 * uiScale;
        batch.setColor(0.1f, 0.1f, 0.1f, 0.8f);
        batch.draw(pixel, x - 2, y - 2, w + 4, h + 4);
        batch.setColor(0.7f, 0.1f, 0.1f, 1f);
        batch.draw(pixel, x, y, w, h);
        float frac = p.health / (float) Config.MAX_HEALTH;
        batch.setColor(0.15f, 0.85f, 0.25f, 1f);
        batch.draw(pixel, x, y, w * frac, h);
        batch.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "HP " + p.health + "/" + Config.MAX_HEALTH + "   x" + p.lives + "   Score " + p.score, x, y - 6 * uiScale);
    }

    public int hotbarSlotAt(float screenX, float screenYFromBottom, int sw) {
        float[] bar = HudLayout.hotbar(sw, uiScale);
        float[] sg = HudLayout.hotbarSlotAndGap(sw, uiScale);
        float slot = sg[0], gap = sg[1];
        if (screenYFromBottom < bar[1] || screenYFromBottom > bar[1] + bar[3]) return -1;
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            float sx = bar[0] + i * (slot + gap);
            if (screenX >= sx && screenX <= sx + slot) return i;
        }
        return -1;
    }

    private void drawHotbar(SpriteBatch batch, Inventory inv, int sw) {
        TextureRegion pixel = assets.get("particle");
        float[] bar = HudLayout.hotbar(sw, uiScale);
        float[] sg = HudLayout.hotbarSlotAndGap(sw, uiScale);
        float slot = sg[0], gap = sg[1];
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            float sx = bar[0] + i * (slot + gap), sy = bar[1];
            boolean sel = i == inv.selected;
            batch.setColor(sel ? new Color(0.95f, 0.85f, 0.2f, 0.9f) : new Color(0.1f, 0.1f, 0.1f, 0.6f));
            batch.draw(pixel, sx - 2, sy - 2, slot + 4, slot + 4);
            batch.setColor(0.15f, 0.15f, 0.15f, 0.75f);
            batch.draw(pixel, sx, sy, slot, slot);
            batch.setColor(1f, 1f, 1f, 1f);
            int id = inv.ids[i];
            if (id != 0) {
                TextureRegion icon = Inventory.isTool(id) ? assets.get("particle") : assets.tile(id);
                if (icon != null) batch.draw(icon, sx + slot * 0.15f, sy + slot * 0.15f, slot * 0.7f, slot * 0.7f);
                if (inv.counts[i] > 1) font.draw(batch, String.valueOf(inv.counts[i]), sx + 3, sy + 13 * uiScale);
            }
        }
    }

    private void drawTutorialBanner(SpriteBatch batch, Tutorial t, int sw, int sh) {
        TextureRegion pixel = assets.get("particle");
        String text = "[" + t.stepNumber() + "/" + t.stepTotal() + "] " + t.prompt(touchMode);
        float w = 420 * uiScale, h = 40 * uiScale;
        float x = (sw - w) / 2f, y = sh - 90 * uiScale;
        batch.setColor(0f, 0f, 0f, 0.65f);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, x + 12 * uiScale, y + h - 12 * uiScale);
    }

    private void drawCraftMenu(SpriteBatch batch, Inventory inv, int sw, int sh) {
        TextureRegion pixel = assets.get("particle");
        float w = 420 * uiScale, h = (Recipe.ALL.size() + 1) * 24 * uiScale + 16 * uiScale;
        float x = (sw - w) / 2f, y = (sh - h) / 2f;
        batch.setColor(0.05f, 0.05f, 0.08f, 0.9f);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "CRAFTING (tap BAG / press TAB to close)", x + 10 * uiScale, y + h - 10 * uiScale);
        for (int i = 0; i < Recipe.ALL.size(); i++) {
            Recipe r = Recipe.ALL.get(i);
            font.setColor(r.canCraft(inv) ? Color.WHITE : Color.GRAY);
            font.draw(batch, (i + 1) + ") " + r.label(), x + 10 * uiScale, y + h - 10 * uiScale - (i + 1) * 24 * uiScale);
        }
        font.setColor(Color.WHITE);
    }

    @Override public void dispose() { font.dispose(); }
}
