package com.badlogic.Terraria;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Loads every texture once and hands out regions by name. */
public class Assets implements Disposable {
    private static final String[] NAMES = {
        "dirt", "player"
    };

    private final AssetManager manager = new AssetManager();
    private final Map<String, TextureRegion> regions = new HashMap<>();

    public Assets() {
        for (String name : NAMES) {
            manager.load(name + ".png", Texture.class);
        }
        manager.finishLoading();

        for (String name : NAMES) {
            Texture texture = manager.get(name + ".png", Texture.class);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            regions.put(name, new TextureRegion(texture));
        }
    }

    public TextureRegion get(String name) {
        TextureRegion region = regions.get(name);
        if (region == null) {
            throw new IllegalArgumentException("Missing texture: " + name);
        }
        return region;
    }

    @Override
    public void dispose() {
        manager.dispose();
    }
}
