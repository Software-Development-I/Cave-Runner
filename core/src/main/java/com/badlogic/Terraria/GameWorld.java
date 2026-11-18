package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Whole game simulation: world, player, drops, and mining without rendering or input polling. */
public class GameWorld {
    public final World world;
    public final Player player;
    public final List<ItemDrop> drops = new ArrayList<>();

    public boolean evMine;
    public boolean evPlace;

    private int mineX = -1;
    private int mineY = -1;
    private float mineProgress;

    public int heldBlock = Tiles.DIRT;

    public GameWorld(World world, Player player) {
        this.world = world;
        this.player = player;
    }

    public void update(float dt, Player.Controls controls) {
        player.update(dt, world, controls);
        Iterator<ItemDrop> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemDrop drop = iterator.next();
            drop.update(dt, world, player);
            if (drop.collected) {
                iterator.remove();
            }
        }
    }

    public void mineTile(int tx, int ty, float dt) {
        int tile = world.get(tx, ty);
        if (!Tiles.isBreakable(tile)
            || !inReach(tx, ty)
            || Tiles.requiredTier(tile) > player.pickTier) {
            resetMining();
            return;
        }

        if (tx != mineX || ty != mineY) {
            resetMining();
            mineX = tx;
            mineY = ty;
        }

        mineProgress += dt * player.miningPower() / Tiles.hardness(tile);
        world.setMining(tx, ty, Math.min(mineProgress, 1f));
        if (mineProgress >= 1f) {
            breakTile(tx, ty);
            resetMining();
        }
    }

    public void resetMining() {
        if (mineX >= 0) {
            world.setMining(mineX, mineY, 0f);
        }
        mineX = -1;
        mineY = -1;
        mineProgress = 0f;
    }

    private void breakTile(int tx, int ty) {
        int tile = world.get(tx, ty);
        world.remove(tx, ty);
        evMine = true;

        float cx = (tx + 0.5f) * World.TILE_SIZE;
        float cy = (ty + 0.5f) * World.TILE_SIZE;
        int drop = Tiles.drop(tile);
        if (drop != 0) {
            drops.add(new ItemDrop(drop, 1, cx - 4f, cy - 4f));
        }
        settleSandAbove(tx, ty);
    }

    private void settleSandAbove(int tx, int ty) {
        for (int y = ty + 1; y < World.HEIGHT; y++) {
            if (world.get(tx, y) != Tiles.SAND) {
                break;
            }
            int land = y;
            while (land > 1 && !world.isSolid(tx, land - 1)) {
                land--;
            }
            if (land != y) {
                world.set(tx, y, Tiles.AIR);
                world.set(tx, land, Tiles.SAND);
            }
        }
    }

    public boolean inReach(int tx, int ty) {
        int ts = World.TILE_SIZE;
        int minX = MathUtils.floor(player.position.x / ts) - Config.REACH_TILES;
        int maxX = MathUtils.floor((player.position.x + player.width) / ts) + Config.REACH_TILES;
        int minY = MathUtils.floor(player.position.y / ts) - Config.REACH_TILES;
        int maxY = MathUtils.floor((player.position.y + player.height) / ts) + Config.REACH_TILES;
        return tx >= minX && tx <= maxX && ty >= minY && ty <= maxY;
    }

    public boolean placeTile(int tx, int ty) {
        if (world.get(tx, ty) != Tiles.AIR || !inReach(tx, ty)) {
            return false;
        }

        float ts = World.TILE_SIZE;
        Rectangle tile = new Rectangle(tx * ts, ty * ts, ts, ts);
        if (player.getBounds().overlaps(tile)) {
            return false;
        }

        boolean attached = world.isSolid(tx - 1, ty)
            || world.isSolid(tx + 1, ty)
            || world.isSolid(tx, ty - 1)
            || world.isSolid(tx, ty + 1);
        if (!attached) {
            return false;
        }

        world.set(tx, ty, heldBlock);
        evPlace = true;
        return true;
    }

    public void clearEvents() {
        evMine = false;
        evPlace = false;
    }
}
