package com.badlogic.Terraria;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Module 5: mouse-driven mining and placement. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Assets assets;
    private GameWorld game;
    private final Player.Controls controls = new Player.Controls();
    private final Vector3 mouse = new Vector3();
    private BitmapFont font;
    private OrthographicCamera uiCamera;
    private boolean craftMenuOpen;
    private final float[] skyTmp = new float[3];
    private final int[] mineTargetTmp = new int[2];
    private boolean haveTarget;
    private int targetX;
    private int targetY;

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        applyViewport();
        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false);
        font = new BitmapFont();

        assets = new Assets();
        World world = new World(12345L, 1);
        float spawnPx = world.spawnX * World.TILE_SIZE;
        float spawnPy = world.spawnY * World.TILE_SIZE;
        Player player = new Player(spawnPx, spawnPy, assets.get("player"));
        player.setSpawn(spawnPx, spawnPy);
        game = new GameWorld(world, player);
        player.inventory.add(Tiles.WOOD, 4);

        camera.position.set(player.centerX(), player.centerY(), 0);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        applyViewport();
        uiCamera.setToOrtho(false, width, height);
    }

    private void applyViewport() {
        float aspect = Gdx.graphics.getWidth()
            / (float) Math.max(1, Gdx.graphics.getHeight());
        camera.viewportHeight = Config.VIEW_H_TILES * World.TILE_SIZE;
        camera.viewportWidth = camera.viewportHeight * aspect;
        camera.update();
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)
            || Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            craftMenuOpen = !craftMenuOpen;
        }

        if (craftMenuOpen) {
            handleCraftMenuInput();
        } else {
            buildControls();
            game.update(dt, controls);
            handleWorldPointer(dt);
            handleHotbarKeys();
            updateCamera();
        }
        draw();
        game.clearEvents();
    }

    private void buildControls() {
        controls.reset();
        if (Gdx.input.isKeyPressed(Input.Keys.A)
            || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            controls.moveX -= 1;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)
            || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            controls.moveX += 1;
        }
        controls.jumpPressed = Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.W)
            || Gdx.input.isKeyJustPressed(Input.Keys.UP);
    }

    private void handleHotbarKeys() {
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                game.player.inventory.select(i);
            }
        }
    }

    private void handleWorldPointer(float dt) {
        mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouse);
        int tx = MathUtils.floor(mouse.x / World.TILE_SIZE);
        int ty = MathUtils.floor(mouse.y / World.TILE_SIZE);

        int[] snapped = pickMineTarget(tx, ty);
        haveTarget = snapped != null;
        if (haveTarget) {
            targetX = snapped[0];
            targetY = snapped[1];
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && haveTarget) {
            game.mineTile(targetX, targetY, dt);
        } else {
            game.resetMining();
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            game.placeTile(tx, ty);
        }
    }

    private int[] pickMineTarget(int cx, int cy) {
        if (Tiles.isBreakable(game.world.get(cx, cy))
            && game.inReach(cx, cy)) {
            mineTargetTmp[0] = cx;
            mineTargetTmp[1] = cy;
            return mineTargetTmp;
        }

        int bestX = -1;
        int bestY = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int x = cx + dx;
                int y = cy + dy;
                if (!Tiles.isBreakable(game.world.get(x, y))
                    || !game.inReach(x, y)) {
                    continue;
                }
                float wx = (x + 0.5f) * World.TILE_SIZE;
                float wy = (y + 0.5f) * World.TILE_SIZE;
                float distance = (wx - mouse.x) * (wx - mouse.x)
                    + (wy - mouse.y) * (wy - mouse.y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) {
            return null;
        }
        mineTargetTmp[0] = bestX;
        mineTargetTmp[1] = bestY;
        return mineTargetTmp;
    }

    private void updateCamera() {
        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;
        float tx = game.player.centerX();
        float ty = game.player.centerY() + 10f;
        camera.position.x += (tx - camera.position.x) * Config.CAMERA_SMOOTH;
        camera.position.y += (ty - camera.position.y) * Config.CAMERA_SMOOTH;
        camera.position.x = MathUtils.clamp(camera.position.x, halfW,
            World.WIDTH * World.TILE_SIZE - halfW);
        camera.position.y = MathUtils.clamp(camera.position.y, halfH,
            World.HEIGHT * World.TILE_SIZE - halfH);
        camera.update();
    }

    private void draw() {
        float[] sky = game.world.theme.sky(0f, skyTmp);
        Gdx.gl.glClearColor(sky[0], sky[1], sky[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawWorld();
        drawDrops();
        drawPlayer();
        drawTargetHighlight();
        drawCraftingOverlay();
    }

    private void drawWorld() {
        batch.setProjectionMatrix(camera.combined);
        int ts = World.TILE_SIZE;
        World world = game.world;
        int x0 = Math.max(0, MathUtils.floor(
            (camera.position.x - camera.viewportWidth / 2f) / ts) - 1);
        int x1 = Math.min(World.WIDTH - 1, MathUtils.ceil(
            (camera.position.x + camera.viewportWidth / 2f) / ts) + 1);
        int y0 = Math.max(0, MathUtils.floor(
            (camera.position.y - camera.viewportHeight / 2f) / ts) - 1);
        int y1 = Math.min(World.HEIGHT - 1, MathUtils.ceil(
            (camera.position.y + camera.viewportHeight / 2f) / ts) + 1);

        TextureRegion pixel = assets.get("particle");
        batch.begin();
        for (int x = x0; x <= x1; x++) {
            int surface = world.surfaceY(x);
            for (int y = y0; y <= y1; y++) {
                int tile = world.get(x, y);
                if (tile == Tiles.AIR && y < surface - 1) {
                    float depthFade = MathUtils.clamp(
                        (surface - 1 - y) / 4f, 0f, 1f);
                    float[] cave = world.theme.cave;
                    batch.setColor(cave[0], cave[1], cave[2], depthFade);
                    batch.draw(pixel, x * ts, y * ts, ts, ts);
                    batch.setColor(1f, 1f, 1f, 1f);
                }
                if (tile == Tiles.AIR) {
                    continue;
                }

                TextureRegion region = assets.tile(tile);
                if (region == null) {
                    continue;
                }
                float[] tint = world.theme.tintFor(tile);
                boolean tinted = false;
                if (tile == Tiles.LAVA) {
                    float flicker = 0.85f + 0.15f * MathUtils.sin(
                        (System.currentTimeMillis() % 100000) * 0.004f
                            + x * 0.9f + y * 0.5f);
                    batch.setColor(1f, flicker, flicker * 0.7f, 1f);
                    tinted = true;
                } else if (tint != null) {
                    batch.setColor(tint[0], tint[1], tint[2], 1f);
                    tinted = true;
                }
                batch.draw(region, x * ts, y * ts, ts, ts);
                if (tinted) {
                    batch.setColor(1f, 1f, 1f, 1f);
                }
            }
        }

        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                float mining = world.getMining(x, y);
                if (mining > 0.02f) {
                    batch.setColor(0f, 0f, 0f, 0.55f * mining);
                    batch.draw(pixel, x * ts, y * ts, ts, ts);
                    batch.setColor(1f, 1f, 1f, 1f);
                }
            }
        }
        batch.end();
    }

    private void drawDrops() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (ItemDrop drop : game.drops) {
            TextureRegion region = assets.tile(drop.id);
            if (region != null) {
                float bob = MathUtils.sin(drop.age * 6f) * 1.5f;
                batch.draw(region, drop.x, drop.y + bob,
                    drop.size(), drop.size());
            }
        }
        batch.end();
    }

    private void drawPlayer() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        game.player.render(batch);
        batch.end();
    }

    private void drawTargetHighlight() {
        if (!haveTarget) {
            return;
        }
        batch.setProjectionMatrix(camera.combined);
        int ts = World.TILE_SIZE;
        TextureRegion pixel = assets.get("particle");
        batch.begin();
        batch.setColor(1f, 1f, 1f, 0.5f);
        int px = targetX * ts;
        int py = targetY * ts;
        batch.draw(pixel, px, py, ts, 1);
        batch.draw(pixel, px, py + ts - 1, ts, 1);
        batch.draw(pixel, px, py, 1, ts);
        batch.draw(pixel, px + ts - 1, py, 1, ts);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private void drawCraftingOverlay() {
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();
        Inventory inv = game.player.inventory;

        StringBuilder hotbar = new StringBuilder();
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            boolean selected = i == inv.selected;
            hotbar.append(selected ? "[" : " ");
            hotbar.append(i + 1).append(':');
            hotbar.append(inv.ids[i] == 0 ? "-"
                : Inventory.name(inv.ids[i]) + "x" + inv.counts[i]);
            hotbar.append(selected ? "]" : " ").append("  ");
        }

        font.setColor(Color.WHITE);
        font.draw(batch, hotbar.toString(), 10, 30);
        font.draw(batch, "Pick tier: " + inv.pickTier()
            + "  Sword tier: " + inv.swordTier()
            + "  [TAB] crafting", 10, Gdx.graphics.getHeight() - 10);

        if (craftMenuOpen) {
            font.draw(batch, "-- CRAFTING (press number, TAB/C to close) --",
                10, Gdx.graphics.getHeight() - 40);
            for (int i = 0; i < Recipe.ALL.size(); i++) {
                Recipe recipe = Recipe.ALL.get(i);
                font.setColor(recipe.canCraft(inv) ? Color.WHITE : Color.GRAY);
                font.draw(batch, (i + 1) + ") " + recipe.label(),
                    10, Gdx.graphics.getHeight() - 40 - (i + 1) * 20);
            }
            font.setColor(Color.WHITE);
        }
        batch.end();
    }

    private void handleCraftMenuInput() {
        for (int i = 0; i < Recipe.ALL.size() && i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                game.craft(Recipe.ALL.get(i));
            }
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        assets.dispose();
        font.dispose();
    }
}
