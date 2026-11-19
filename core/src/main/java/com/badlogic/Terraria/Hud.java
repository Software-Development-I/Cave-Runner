package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * Draws all in-game UI in screen pixels (origin bottom-left): health bar, lives, score, coins, depth,
 * combo, hotbar, inventory + crafting panel, boss bar, toasts. Holds the layout math so input hit-testing
 * in {@link Main} uses exactly the same rectangles that get drawn.
 */
public class Hud implements Disposable {
    private final Assets assets;
    private final BitmapFont font = new BitmapFont();
    private final BitmapFont big = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    /** Pixel-space projection (origin bottom-left) for ALL UI. Rebuilt on resize; applied at the start of every UI pass. */
    private final Matrix4 screenProjection = new Matrix4();
    public boolean inventoryOpen = false;
    public boolean touchMode = false;
    public int craftScroll = 0;
    public float uiScale = 1f;

    public Hud(Assets assets) {
        this.assets = assets;
        big.getData().setScale(2.2f);
    }

    /** Scale UI with screen height so it is readable on phones and 4K monitors alike. */
    public void resize(int w, int h) { screenW = w; screenH = h; screenProjection.setToOrtho2D(0, 0, Math.max(1, w), Math.max(1, h)); uiScale = MathUtils.clamp(h / 720f, 0.8f, 2.5f); font.getData().setScale(uiScale); big.getData().setScale(2.2f * uiScale); }

    // ------------------------------------------------------------------ layout (shared with input)

    /** Screen size the current hotbar layout was computed for. The hotbar depends on where touch controls sit. */
    private int screenW = 1280, screenH = 720;
    private boolean touchControls;
    public void setTouchControls(boolean on) { touchControls = on; }

    /** {slot, gap} for the current screen; the single source used by BOTH drawing and hit-testing. */
    private float[] fit() { return HudLayout.hotbarSlotAndGap(screenW, touchControls ? screenH : 0, uiScale); }
    public float slotSize() { return fit()[0]; }
    public float slotGap() { return fit()[1]; }

    public float hotbarX(int sw) { return HudLayout.hotbar(sw, touchControls ? screenH : 0, uiScale)[0]; }
    public float hotbarY() { return HudLayout.HOTBAR_Y * uiScale; }

    /** Returns the hotbar slot under a screen point (origin bottom-left) or -1. */
    public int hotbarSlotAt(float x, float y, int sw) {
        float[] f = fit(); float slot = f[0], gap = f[1];
        float sx = hotbarX(sw), sy = hotbarY();
        if (y < sy || y > sy + slot) return -1;
        float rel = x - sx;
        if (rel < 0) return -1;
        int i = (int) (rel / (slot + gap));
        if (i < 0 || i >= Inventory.HOTBAR) return -1;
        return (rel - i * (slot + gap)) <= slot ? i : -1;
    }

    // ---- inventory panel geometry: EVERY draw and hit-test below is derived from these ----
    public float panelW(int sw) { return Math.min(sw - 24f, 680f * uiScale); }
    public float panelH(int sh) { return Math.min(sh - 24f, 400f * uiScale); }
    public float panelX(int sw) { return (sw - panelW(sw)) / 2f; }
    public float panelY(int sh) { return (sh - panelH(sh)) / 2f; }
    public float headerH() { return 44f * uiScale; }
    public float recipeRowH() { return 34f * uiScale; }
    /** Left column (item grid) width; right column (crafting) gets the rest. */
    public float leftW(int sw) { return panelW(sw) * 0.50f; }
    public float gridCell(int sw) { return Math.min(46f * uiScale, (leftW(sw) - 28f * uiScale) / 9f); }
    public float gridX(int sw) { return panelX(sw) + 14f * uiScale; }
    /** Top edge (y, bottom-left origin) of grid row 0. */
    public float gridTop(int sh) { return panelY(sh) + panelH(sh) - headerH() - 8f * uiScale; }
    public float recipeX(int sw) { return panelX(sw) + leftW(sw) + 6f * uiScale; }
    public float recipeW(int sw) { return panelW(sw) - leftW(sw) - 20f * uiScale; }
    public float recipeTop(int sh) { return panelY(sh) + panelH(sh) - headerH() - 4f * uiScale; }
    /** How many recipe rows fit before the footer. Extra recipes scroll with the mouse wheel / drag. */
    public int recipeRowsVisible(int sh) { return Math.max(1, (int) ((recipeTop(sh) - (panelY(sh) + 34f * uiScale)) / recipeRowH())); }
    public int maxCraftScroll(int sh) { return Math.max(0, Recipe.ALL.size() - recipeRowsVisible(sh)); }

    /** Inventory grid slot under a screen point (bottom-left origin), or -1. 9 wide x 4 tall, row 0 = hotbar. */
    public int inventorySlotAt(float x, float y, int sw, int sh) {
        if (!inventoryOpen) return -1;
        float cell = gridCell(sw), gx = gridX(sw), top = gridTop(sh);
        for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) {
            float rx = gx + c * cell, ry = top - (r + 1) * cell;
            if (x >= rx && x <= rx + cell - 2 && y >= ry && y <= ry + cell - 2) return r * 9 + c;
        }
        return -1;
    }

    /** Crafting recipe (index into Recipe.ALL) under a screen point, or -1. Honors scrolling. */
    public int recipeAt(float x, float y, int sw, int sh) {
        if (!inventoryOpen) return -1;
        float rx = recipeX(sw), top = recipeTop(sh);
        if (x < rx || x > rx + recipeW(sw) || y > top) return -1;
        int row = (int) ((top - y) / recipeRowH());
        if (row < 0 || row >= recipeRowsVisible(sh)) return -1;
        int i = row + craftScroll;
        return i < Recipe.ALL.size() ? i : -1;
    }

    public void scrollCrafting(int delta, int sh) { craftScroll = MathUtils.clamp(craftScroll + delta, 0, maxCraftScroll(sh)); }

    // ------------------------------------------------------------------ drawing

    public void render(SpriteBatch b, ShapeRenderer sr, GameWorld g, int sw, int sh) {
        Player p = g.player;
        float u = uiScale;
        // The previous pass drew the world with the CAMERA matrix. UI is in pixels, so switch explicitly.
        b.setProjectionMatrix(screenProjection);
        sr.setProjectionMatrix(screenProjection);
        b.setColor(1f, 1f, 1f, 1f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---------- backgrounds (shapes) ----------
        sr.begin(ShapeRenderer.ShapeType.Filled);
        float barX = 16 * u, barY = sh - 34 * u, barW = 220 * u, barH = 18 * u;
        sr.setColor(0.1f, 0.1f, 0.1f, 0.75f); sr.rect(barX - 3, barY - 3, barW + 6, barH + 6);
        float frac = p.health / (float) Config.MAX_HEALTH;
        sr.setColor(frac > 0.5f ? 0.2f : frac > 0.25f ? 0.9f : 0.9f, frac > 0.5f ? 0.8f : frac > 0.25f ? 0.7f : 0.15f, 0.15f, 1f);
        sr.rect(barX, barY, barW * frac, barH);

        Enemy boss = g.activeBoss();
        float[] bb = HudLayout.bossBar(sw, sh, u);              // outer frame rect; the fill sits 3px inside
        if (boss != null) {
            sr.setColor(0.1f, 0.05f, 0.1f, 0.85f); sr.rect(bb[0], bb[1], bb[2], bb[3]);
            sr.setColor(0.7f, 0.2f, 0.85f, 1f); sr.rect(bb[0] + 3f, bb[1] + 3f, (bb[2] - 6f) * (boss.hp / (float) boss.maxHp), bb[3] - 6f);
        }

        // hotbar
        float hx = hotbarX(sw), hy = hotbarY();
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            boolean sel = i == p.inventory.selected;
            sr.setColor(sel ? 1f : 0.1f, sel ? 0.85f : 0.1f, sel ? 0.2f : 0.12f, sel ? 0.95f : 0.7f);
            float sx = hx + i * (slotSize() + slotGap());
            sr.rect(sx - 2, hy - 2, slotSize() + 4, slotSize() + 4);
            sr.setColor(0.18f, 0.18f, 0.22f, 0.9f);
            sr.rect(sx, hy, slotSize(), slotSize());
        }

        if (inventoryOpen) drawPanelBackground(sr, g, sw, sh);
        sr.end();

        // ---------- icons + text (batch) ----------
        b.begin();
        font.setColor(Color.WHITE);
        // "HP 64/100" is vertically centred in the bar (baseline = bar centre + half cap height)
        font.draw(b, "HP " + p.health + "/" + Config.MAX_HEALTH, barX + 8 * u, barY + barH / 2f + font.getCapHeight() / 2f);
        // lives (hearts) directly under the bar
        TextureRegion heart = assets.get("heart");
        float rowY = barY - 8 * u;                                  // top of the next row
        for (int i = 0; i < p.lives; i++) b.draw(heart, barX + i * 24 * u, rowY - 18 * u, 20 * u, 18 * u);
        float lineH = 22 * u;
        float y = rowY - 18 * u - 10 * u;                            // baseline of the first text row
        font.draw(b, "Score  " + p.score, barX, y);
        y -= lineH;
        float coinSz = 14 * u;
        b.draw(assets.get("coin"), barX, y - font.getCapHeight() / 2f - coinSz / 2f, coinSz, coinSz);
        font.draw(b, "x " + p.coins, barX + coinSz + 6 * u, y);
        y -= lineH;
        int depth = Math.max(0, g.world.surfaceY(MathUtils.clamp((int) (p.centerX() / World.TILE_SIZE), 0, World.WIDTH - 1)) - (int) (p.centerY() / World.TILE_SIZE));
        font.draw(b, "Depth " + depth + "   Level " + g.level + "/" + Config.MAX_LEVEL, barX, y);
        y -= lineH;
        if (p.comboMultiplier > 1) { font.setColor(1f, 0.85f, 0.2f, 1f); font.draw(b, "COMBO x" + p.comboMultiplier, barX, y); font.setColor(Color.WHITE); }
        y -= lineH;
        if (g.isNight()) { font.setColor(0.7f, 0.75f, 1f, 1f); font.draw(b, "Night: more enemies!", barX, y); font.setColor(Color.WHITE); }
        if (boss != null) {
            layout.setText(font, "GIANT SLIME");
            font.draw(b, "GIANT SLIME", (sw - layout.width) / 2f, bb[1] + bb[3] / 2f + font.getCapHeight() / 2f);
        }

        // hotbar contents
        for (int i = 0; i < Inventory.HOTBAR; i++) drawStack(b, p.inventory, i, hx + i * (slotSize() + slotGap()), hy, slotSize());
        font.setColor(0.8f, 0.8f, 0.8f, 1f);
        int selId = p.inventory.selectedId();
        if (selId != 0) { layout.setText(font, Inventory.name(selId)); font.draw(b, Inventory.name(selId), (sw - layout.width) / 2f, hy + slotSize() + 22 * u); }
        font.setColor(Color.WHITE);

        if (g.messageTimer > 0) {
            float a = MathUtils.clamp(g.messageTimer, 0f, 1f);
            big.setColor(1f, 1f, 1f, a);
            layout.setText(big, g.message);
            big.draw(b, g.message, (sw - layout.width) / 2f, sh * 0.72f);
            big.setColor(Color.WHITE);
        }
        if (!inventoryOpen) drawTutorial(b, sr, g.tutorial, touchMode, sw, sh);
        if (inventoryOpen) drawPanelContents(b, g, sw, sh);
        b.end();
    }

    private void drawStack(SpriteBatch b, Inventory inv, int i, float x, float y, float size) {
        int id = inv.ids[i];
        if (id == 0) return;
        TextureRegion tex = iconFor(id);
        if (tex != null) b.draw(tex, x + size * 0.12f, y + size * 0.12f, size * 0.76f, size * 0.76f);
        if (!Inventory.isTool(id) && inv.counts[i] > 1) drawCount(b, inv.counts[i], x, y, size);
    }


    /** Stack count drawn at the bottom-right of a slot with a dark drop-shadow so it stays readable on any icon. */
    private void drawCount(SpriteBatch b, int count, float x, float y, float size) {
        String txt = String.valueOf(count);
        layout.setText(font, txt);
        float tx = x + size - layout.width - 3f * uiScale, ty = y + layout.height + 2f * uiScale;
        font.setColor(0f, 0f, 0f, 0.9f);
        font.draw(b, txt, tx + 1f, ty - 1f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(b, txt, tx, ty);
    }

    private TextureRegion iconFor(int id) {
        if (id == Inventory.ITEM_WOOD_PICK || id == Inventory.ITEM_STONE_PICK || id == Inventory.ITEM_IRON_PICK) return assets.get("pickaxe");
        if (id == Inventory.ITEM_WOOD_SWORD || id == Inventory.ITEM_STONE_SWORD || id == Inventory.ITEM_IRON_SWORD) return assets.get("sword");
        return assets.tile(id);
    }

    private void drawPanelBackground(ShapeRenderer sr, GameWorld g, int sw, int sh) {
        float px = panelX(sw), py = panelY(sh), pw = panelW(sw), ph = panelH(sh);
        sr.setColor(0f, 0f, 0f, 0.62f); sr.rect(0, 0, sw, sh);                       // dim everything behind
        sr.setColor(0.13f, 0.14f, 0.20f, 0.98f); sr.rect(px, py, pw, ph);
        sr.setColor(0.30f, 0.32f, 0.45f, 1f); sr.rect(px, py + ph - 5f * uiScale, pw, 5f * uiScale);
        sr.setColor(0.22f, 0.24f, 0.34f, 1f); sr.rect(px + leftW(sw), py + 10f * uiScale, 2f, ph - headerH() - 6f * uiScale); // column divider
        float cell = gridCell(sw), gx = gridX(sw), top = gridTop(sh);
        for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) {
            boolean sel = r == 0 && c == g.player.inventory.selected;
            sr.setColor(sel ? 0.95f : 0.28f, sel ? 0.8f : 0.30f, sel ? 0.2f : 0.38f, 1f);
            sr.rect(gx + c * cell, top - (r + 1) * cell, cell - 2, cell - 2);
        }
        int rows = Math.min(recipeRowsVisible(sh), Recipe.ALL.size() - craftScroll);
        for (int k = 0; k < rows; k++) {
            float ry = recipeTop(sh) - (k + 1) * recipeRowH();
            boolean ok = Recipe.ALL.get(craftScroll + k).canCraft(g.player.inventory);
            sr.setColor(ok ? 0.20f : 0.20f, ok ? 0.50f : 0.21f, ok ? 0.25f : 0.26f, 1f);
            sr.rect(recipeX(sw), ry, recipeW(sw), recipeRowH() - 3f);
        }
    }

    private void drawPanelContents(SpriteBatch b, GameWorld g, int sw, int sh) {
        float px = panelX(sw), py = panelY(sh), ph = panelH(sh), pw = panelW(sw);
        font.setColor(Color.WHITE);
        font.draw(b, "INVENTORY", gridX(sw), py + ph - 12f * uiScale);
        font.draw(b, "CRAFTING", recipeX(sw), py + ph - 12f * uiScale);
        float cell = gridCell(sw), gx = gridX(sw), top = gridTop(sh);
        for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) {
            int i = r * 9 + c; int id = g.player.inventory.ids[i];
            if (id == 0) continue;
            TextureRegion tex = iconFor(id);
            float ix = gx + c * cell, iy = top - (r + 1) * cell;
            if (tex != null) b.draw(tex, ix + 3, iy + 3, cell - 8, cell - 8);
            if (!Inventory.isTool(id) && g.player.inventory.counts[i] > 1) drawCount(b, g.player.inventory.counts[i], ix, iy, cell - 2);
        }
        // recipe text: baseline = row centre so each label sits INSIDE its own box
        font.getData().setScale(uiScale * 0.82f);
        int rows = Math.min(recipeRowsVisible(sh), Recipe.ALL.size() - craftScroll);
        for (int k = 0; k < rows; k++) {
            Recipe r = Recipe.ALL.get(craftScroll + k);
            float rowBottom = recipeTop(sh) - (k + 1) * recipeRowH();
            float ty = rowBottom + (recipeRowH() - 3f) / 2f + font.getCapHeight() / 2f;
            font.setColor(r.canCraft(g.player.inventory) ? Color.WHITE : new Color(0.62f, 0.62f, 0.68f, 1f));
            font.draw(b, r.label(), recipeX(sw) + 6f * uiScale, ty);
        }
        font.getData().setScale(uiScale); font.setColor(0.75f, 0.78f, 0.9f, 1f);
        if (maxCraftScroll(sh) > 0) font.draw(b, "Scroll for more recipes  (" + (craftScroll + 1) + "-" + (craftScroll + rows) + " of " + Recipe.ALL.size() + ")", recipeX(sw), py + 26f * uiScale);
        font.draw(b, "Pickaxe tier " + g.player.inventory.pickTier() + "   Sword tier " + g.player.inventory.swordTier(), gridX(sw), py + 26f * uiScale);
        font.setColor(Color.WHITE);
    }

    private final float[] tutRect = new float[4];

    /** The pixel-space matrix used for every UI draw (never the world camera). */
    public Matrix4 screenMatrix() { return screenProjection; }

    /** Tutorial hint strip near the top-centre, with step counter and a skip hint. Drawn inside the batch pass. */
    public void drawTutorial(SpriteBatch b, ShapeRenderer sr, Tutorial t, boolean touch, int sw, int sh) {
        if (!t.active()) { tutRect[2] = 0f; return; }
        float u = uiScale;
        String msg = t.prompt(touch);
        String step = "Tutorial " + t.stepNumber() + "/" + t.stepTotal();
        String skip = touch ? "tap to skip" : "press K to skip";
        layout.setText(font, msg);
        float[] probe = HudLayout.tutorialRect(sw, sh, u, layout.width, 0f);           // decides the width
        layout.setText(font, msg, Color.WHITE, probe[2] - 24f * u, com.badlogic.gdx.utils.Align.center, true);
        float[] r = HudLayout.tutorialRect(sw, sh, u, probe[2] - 40f * u, layout.height);  // now knows the wrapped height
        System.arraycopy(r, 0, tutRect, 0, 4);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        b.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.05f, 0.07f, 0.14f, 0.82f); sr.rect(x, y, w, h);
        float done = (t.stepNumber() - 1) / (float) t.stepTotal();
        sr.setColor(1f, 0.85f, 0.25f, 1f); sr.rect(x, y + h - 4f * u, w * done, 4f * u);
        sr.setColor(0.35f, 0.4f, 0.6f, 1f); sr.rect(x + w * done, y + h - 4f * u, w / t.stepTotal(), 4f * u);
        sr.end();
        b.begin();
        font.setColor(1f, 0.85f, 0.25f, 1f); font.draw(b, step, x + 12f * u, y + h - 12f * u);
        GlyphLayout sk = new GlyphLayout(font, skip);
        font.setColor(0.7f, 0.75f, 0.9f, 1f); font.draw(b, skip, x + w - sk.width - 12f * u, y + h - 12f * u);
        font.setColor(Color.WHITE);
        layout.setText(font, msg, Color.WHITE, w - 24f * u, com.badlogic.gdx.utils.Align.center, true);
        font.draw(b, layout, x + 12f * u, y + h - 32f * u);
    }

    /** True if a screen point (bottom-left origin) is on the tutorial strip as it was actually drawn. */
    public boolean tutorialSkipHit(float px, float py, int sw, int sh) {
        return tutRect[2] > 0f && px >= tutRect[0] && px <= tutRect[0] + tutRect[2] && py >= tutRect[1] && py <= tutRect[1] + tutRect[3];
    }

    /** Centered big title + optional subtitle lines, used by menu / pause / game over. */
    public void drawCentered(SpriteBatch b, String title, String[] lines, int sw, int sh, float yFrac) {
        big.setColor(Color.WHITE);
        layout.setText(big, title);
        big.draw(b, title, (sw - layout.width) / 2f, sh * yFrac);
        float y = sh * yFrac - 60 * uiScale;
        for (String s : lines) {
            layout.setText(font, s);
            font.draw(b, s, (sw - layout.width) / 2f, y);
            y -= 28 * uiScale;
        }
    }

    public BitmapFont font() { return font; }
    public GlyphLayout layout() { return layout; }

    @Override public void dispose() { font.dispose(); big.dispose(); }
}
