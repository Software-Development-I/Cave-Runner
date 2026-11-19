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
        "dirt", "stone", "bedrock", "sand", "spikes", "lava", "crumble", "arrowtrap", "coal", "iron", "gold",
        "wood", "leaves", "planks", "torch", "flag", "portal", "coin", "heart", "arrow", "slime", "bat",
        "skeleton", "boss", "particle", "pickaxe", "sword", "player"
    };

    private final AssetManager manager = new AssetManager();
    private final Map<String, TextureRegion> regions = new HashMap<>();
    private final TextureRegion[] tileRegions = new TextureRegion[Tiles.COUNT];

    public Assets() {
        for (String n : NAMES) manager.load(n + ".png", Texture.class);
        manager.finishLoading();
        for (String n : NAMES) {
            Texture t = manager.get(n + ".png", Texture.class);
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            regions.put(n, new TextureRegion(t));
        }
        for (int i = 0; i < Tiles.COUNT; i++) {
            String tex = Tiles.texture(i);
            if (tex != null) tileRegions[i] = regions.get(tex);
        }
    }

    public TextureRegion get(String name) {
        TextureRegion r = regions.get(name);
        if (r == null) throw new IllegalArgumentException("Missing texture: " + name);
        return r;
    }

    public TextureRegion tile(int type) {
        return (type >= 0 && type < tileRegions.length) ? tileRegions[type] : null;
    }

    @Override public void dispose() { manager.dispose(); }
}
