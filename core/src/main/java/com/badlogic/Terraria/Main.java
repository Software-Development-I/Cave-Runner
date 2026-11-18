package com.badlogic.Terraria;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private static final int VIEW_W_TILES = 32;
    private static final int VIEW_H_TILES = 18;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 2.0f;
    private static final float FOCUS_SPEED = 220f;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Assets assets;
    private World world;
    private float zoom = 1f;
    private float focusX;
    private float focusY;
    private final float[] skyTmp = new float[3];

    @Override
    public void create() {
        batch = new SpriteBatch();
        assets = new Assets();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VIEW_W_TILES * World.TILE_SIZE,
            VIEW_H_TILES * World.TILE_SIZE);
        applyZoom();
        world = new World(12345L, 1);
        focusX = world.spawnX * World.TILE_SIZE;
        focusY = world.spawnY * World.TILE_SIZE;
    }

    @Override
    public void resize(int width, int height) {
        applyZoom();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        handleInput(delta);
        updateCamera();
        draw();
    }

    @Override
    public void dispose() {
        batch.dispose();
        assets.dispose();
    }

    private void handleInput(float delta) {
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            focusX -= FOCUS_SPEED * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            focusX += FOCUS_SPEED * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            focusY += FOCUS_SPEED * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            focusY -= FOCUS_SPEED * delta;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)
            || Gdx.input.isKeyJustPressed(Input.Keys.PLUS)) {
            zoomBy(-0.1f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
            zoomBy(0.1f);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0)) {
            zoom = 1f;
            applyZoom();
        }
    }

    private void zoomBy(float delta) {
        zoom = MathUtils.clamp(zoom + delta, MIN_ZOOM, MAX_ZOOM);
        applyZoom();
    }

    /** Recompute viewport size from zoom and the window aspect ratio. */
    private void applyZoom() {
        float aspect = Gdx.graphics.getWidth()
            / (float) Math.max(1, Gdx.graphics.getHeight());
        camera.viewportHeight = VIEW_H_TILES * World.TILE_SIZE * zoom;
        camera.viewportWidth = camera.viewportHeight * aspect;
        camera.update();
    }

    /** Smooth the camera toward the focus point, then keep it inside the world. */
    private void updateCamera() {
        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;
        camera.position.x += (focusX - camera.position.x) * 0.15f;
        camera.position.y += (focusY - camera.position.y) * 0.15f;
        camera.position.x = MathUtils.clamp(camera.position.x, halfW,
            World.WIDTH * World.TILE_SIZE - halfW);
        camera.position.y = MathUtils.clamp(camera.position.y, halfH,
            World.HEIGHT * World.TILE_SIZE - halfH);
        camera.update();
    }

    private void draw() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float[] sky = world.theme.sky(0f, skyTmp);
        Gdx.gl.glClearColor(sky[0], sky[1], sky[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawWorld();
    }

    /** Draw only the tile range visible to the camera. */
    private void drawWorld() {
        batch.setProjectionMatrix(camera.combined);
        int ts = World.TILE_SIZE;
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
            int surf = world.surfaceY(x);
            for (int y = y0; y <= y1; y++) {
                int type = world.get(x, y);

                if (type == Tiles.AIR && y < surf - 1) {
                    float depthFade = MathUtils.clamp((surf - 1 - y) / 4f, 0f, 1f);
                    float[] cave = world.theme.cave;
                    batch.setColor(cave[0], cave[1], cave[2], depthFade);
                    batch.draw(pixel, x * ts, y * ts, ts, ts);
                    batch.setColor(1f, 1f, 1f, 1f);
                }
                if (type == Tiles.AIR) {
                    continue;
                }

                TextureRegion region = assets.tile(type);
                if (region == null) {
                    continue;
                }

                float[] tint = world.theme.tintFor(type);
                boolean tinted = false;
                if (type == Tiles.LAVA) {
                    float flicker = 0.85f + 0.15f * MathUtils.sin(
                        (System.nanoTime() / 1e9f) * 4f + x * 0.9f + y * 0.5f);
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
        batch.end();
    }
}
