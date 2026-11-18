package com.badlogic.Terraria;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Assets assets;

    private float playerX = 100f;

    private static final int VIEW_W = 32 * Config.TILE;
    private static final int VIEW_H = 18 * Config.TILE;

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VIEW_W, VIEW_H);
        assets = new Assets();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        playerX += Config.MOVE_SPEED * delta;
        if (playerX > VIEW_W) {
            playerX = -Config.PLAYER_W;
        }

        Gdx.gl.glClearColor(0.53f, 0.75f, 0.92f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(assets.get("dirt"), 0, 0, VIEW_W, Config.TILE);
        batch.draw(assets.get("player"), playerX, Config.TILE,
            Config.PLAYER_W, Config.PLAYER_H);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        assets.dispose();
    }
}
