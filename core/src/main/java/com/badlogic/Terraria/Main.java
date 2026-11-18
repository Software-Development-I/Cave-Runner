package com.badlogic.Terraria;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/** Module 4: a real, physics-driven Player replaces the camera-target dot. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Assets assets;
    private World world;
    private Player player;
    private final Player.Controls controls = new Player.Controls();
    private final float[] skyTmp = new float[3];

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        applyViewport();

        assets = new Assets();
        world = new World(12345L, 1);

        float spawnPx = world.spawnX * World.TILE_SIZE;
        float spawnPy = world.spawnY * World.TILE_SIZE;
        player = new Player(spawnPx, spawnPy, assets.get("player"));
        player.setSpawn(spawnPx, spawnPy);

        camera.position.set(player.centerX(), player.centerY(), 0);
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        applyViewport();
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
        float delta = Gdx.graphics.getDeltaTime();
        buildControls();
        player.update(delta, world, controls);
        updateCamera();
        draw();
    }

    @Override
    public void dispose() {
        batch.dispose();
        assets.dispose();
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

    private void updateCamera() {
        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;
        float tx = player.centerX();
        float ty = player.centerY() + 10f;
        camera.position.x += (tx - camera.position.x) * Config.CAMERA_SMOOTH;
        camera.position.y += (ty - camera.position.y) * Config.CAMERA_SMOOTH;
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
        drawPlayer();
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

    private void drawPlayer() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        player.render(batch);
        batch.end();
    }
}
