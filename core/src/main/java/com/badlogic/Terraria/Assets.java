package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Loads every texture once and hands out regions by name. */
public class Assets implements Disposable {
    private static final String[] NAMES = {
        "dirt", "stone", "bedrock", "sand", "spikes", "lava", "crumble", "arrowtrap",
        "coal", "iron", "gold", "wood", "leaves", "planks", "torch", "flag", "portal",
        "coin", "heart", "arrow", "slime", "bat", "skeleton", "boss", "particle",
        "pickaxe", "sword", "player"
    };

    private final AssetManager manager = new AssetManager();
    private final Map<String, TextureRegion> regions = new HashMap<>();
    private final TextureRegion[] tileRegions = new TextureRegion[Tiles.COUNT];

    public Assets() {
        for (String name : NAMES) {
            if (Gdx.files.internal(name + ".png").exists()) {
                manager.load(name + ".png", Texture.class);
            }
        }
        manager.finishLoading();

        for (String name : NAMES) {
            if (manager.isLoaded(name + ".png", Texture.class)) {
                Texture texture = manager.get(name + ".png", Texture.class);
                texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                regions.put(name, new TextureRegion(texture));
            }
        }

        if (!regions.containsKey("dirt")) {
            throw new IllegalStateException("Missing required texture: dirt.png");
        }

        for (int i = 0; i < Tiles.COUNT; i++) {
            String texture = Tiles.texture(i);
            if (texture != null) {
                tileRegions[i] = regions.get(texture);
            }
        }
    }

    public TextureRegion get(String name) {
        TextureRegion region = regions.get(name);
        // Missing future-module art uses dirt rather than crashing this module.
        return region != null ? region : regions.get("dirt");
    }

    public TextureRegion tile(int type) {
        return type >= 0 && type < tileRegions.length ? tileRegions[type] : null;
    }

    @Override
    public void dispose() {
        manager.dispose();
    }
}
