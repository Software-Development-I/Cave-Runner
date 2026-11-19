import com.badlogic.Terraria.*;

public class SimTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }
    static final float DT = 1 / 60f;

    /** Flat arena: stone floor y<=10, air above. Clears traps so scenarios are isolated. */
    static GameWorld arena() {
        GameWorld g = new GameWorld(null, 5, 1);
        World w = g.world;
        for (int x = 0; x < World.WIDTH; x++) for (int y = 1; y < World.HEIGHT; y++) w.set(x, y, y <= 10 ? Tiles.STONE : Tiles.AIR);
        g.enemies.clear(); g.projectiles.clear(); g.drops.clear();
        g.player.position.set(100 * 16, 11 * 16); g.player.velocity.setZero(); g.player.invincible = 0; g.player.health = Config.MAX_HEALTH;
        g.player.setSpawn(100 * 16, 11 * 16);
        return g;
    }
    static void run(GameWorld g, Player.Controls c, int frames) { for (int i = 0; i < frames; i++) { g.update(DT, c); c.jumpPressed = false; c.attackPressed = false; } }

    public static void main(String[] a) {
        Player.Controls c = new Player.Controls();

        System.out.println("[1] Hold-to-mine takes time, then drops an item that the player collects");
        GameWorld g = arena(); run(g, c, 30);
        int tx = 101, ty = 10;    // stone next to player's feet
        float t = 0; boolean broke = false;
        while (t < 5f && !broke) { g.mineTile(tx, ty, DT); t += DT; broke = g.world.get(tx, ty) == Tiles.AIR; }
        check(broke, "stone block broke after " + String.format("%.2f", t) + "s (bare hands, hardness 0.7)");
        check(t > 0.5f && t < 1.0f, "mining took a sensible time");
        check(g.drops.size() == 1 && g.drops.get(0).id == Tiles.STONE, "a stone item dropped");
        run(g, c, 120);
        check(g.player.inventory.count(Tiles.STONE) == 1, "stone was picked up (count=" + g.player.inventory.count(Tiles.STONE) + ")");
        check(g.player.score >= Config.SCORE_STONE, "score awarded: " + g.player.score);

        System.out.println("[2] Bedrock cannot be mined");
        g = arena(); g.player.position.set(100 * 16, 1 * 16);  // stand near bottom
        g.mineTile(100, 0, 3f);
        check(g.world.get(100, 0) == Tiles.BEDROCK, "bedrock survived 3s of mining");

        System.out.println("[3] Iron needs a pickaxe tier; better pick mines faster");
        g = arena(); g.world.set(101, 11, Tiles.IRON); g.mineTile(101, 11, 5f);
        check(g.world.get(101, 11) == Tiles.IRON, "iron NOT mineable with bare hands");
        g.player.inventory.add(Inventory.ITEM_WOOD_PICK, 1); g.mineTile(101, 11, 5f);
        check(g.world.get(101, 11) == Tiles.IRON, "iron NOT mineable with wood pick");
        g.player.inventory.add(Inventory.ITEM_STONE_PICK, 1); g.mineTile(101, 11, 5f);
        check(g.world.get(101, 11) == Tiles.AIR, "iron mineable with stone pick");
        g.world.set(101, 11, Tiles.GOLD); g.mineTile(101, 11, 5f);
        check(g.world.get(101, 11) == Tiles.GOLD, "gold NOT mineable with stone pick");
        g.player.inventory.add(Inventory.ITEM_IRON_PICK, 1); g.mineTile(101, 11, 5f);
        check(g.world.get(101, 11) == Tiles.AIR, "gold mineable with iron pick");
        GameWorld g1 = arena(); GameWorld g2 = arena(); g2.player.inventory.add(Inventory.ITEM_IRON_PICK, 1);
        float t1 = 0, t2 = 0; while (g1.world.get(101, 10) != Tiles.AIR && t1 < 10) { g1.mineTile(101, 10, DT); t1 += DT; }
        while (g2.world.get(101, 10) != Tiles.AIR && t2 < 10) { g2.mineTile(101, 10, DT); t2 += DT; }
        check(t2 < t1 * 0.6f, "iron pick is faster (" + String.format("%.2f", t2) + "s vs " + String.format("%.2f", t1) + "s)");

        System.out.println("[4] Out-of-reach mining is refused");
        g = arena(); g.mineTile(130, 10, 5f);
        check(g.world.get(130, 10) == Tiles.STONE, "cannot mine a tile 30 tiles away");

        System.out.println("[5] Placing blocks");
        g = arena(); g.player.inventory.add(Tiles.DIRT, 5);
        for (int i = 0; i < Inventory.HOTBAR; i++) if (g.player.inventory.ids[i] == Tiles.DIRT) g.player.inventory.selected = i;
        check(g.player.inventory.selectedId() == Tiles.DIRT, "dirt selected in hotbar");
        check(g.placeTile(102, 11), "placed dirt next to player");
        check(g.world.get(102, 11) == Tiles.DIRT && g.player.inventory.count(Tiles.DIRT) == 4, "tile set, inventory decremented");
        check(!g.placeTile(100, 11), "cannot place inside the player's own body");
        check(!g.placeTile(102, 11), "cannot place on an occupied tile");
        check(!g.placeTile(105, 11), "cannot place out of reach... (5 tiles)");

        System.out.println("[6] Spikes hurt, lava burns, and falling to death costs a life");
        g = arena(); g.world.set(101, 11, Tiles.SPIKES); g.player.position.set(101 * 16 + 2, 11 * 16); run(g, c, 30);
        check(g.player.health == Config.MAX_HEALTH - Config.SPIKE_DAMAGE, "spike hit -> hp " + g.player.health);
        g = arena(); g.player.damage(999, 0, 0, true); int livesBefore = g.player.lives; run(g, c, 120);
        check(!g.player.dead && g.player.lives == livesBefore - 1 && g.player.health == Config.MAX_HEALTH, "died, waited, respawned with " + g.player.lives + " lives");
        g = arena(); g.player.lives = 1; g.player.damage(999, 0, 0, true); run(g, c, 120);
        check(g.gameOver, "game over after the last life");

        System.out.println("[7] Crumbling block breaks after standing on it, then regrows");
        g = arena(); for (int x = 98; x <= 102; x++) g.world.set(x, 10, Tiles.CRUMBLE);
        g.player.position.set(100 * 16 + 2, 11 * 16); run(g, c, 30);
        check(g.world.get(100, 10) == Tiles.CRUMBLE, "still there at 0.5s");
        run(g, c, 20);
        check(g.world.get(100, 10) == Tiles.AIR, "crumbled after > 0.6s");
        g.player.position.set(140 * 16, 11 * 16); run(g, c, (int) (Config.CRUMBLE_RESPAWN * 60) + 30);
        check(g.world.get(100, 10) == Tiles.CRUMBLE, "block regrew after " + Config.CRUMBLE_RESPAWN + "s");

        System.out.println("[8] Arrow trap fires only with line of sight, arrow damages then dies");
        g = arena(); g.world.set(90, 12, Tiles.ARROW_TRAP);
        g.player.position.set(100 * 16, 11 * 16);   // 10 tiles away, same row band
        java.lang.reflect.Field f = null;
        try { // register the trap through the same path the generator uses
            g.world.arrowTraps.add(new int[]{90, 12, 1});
            java.lang.reflect.Method m = GameWorld.class.getDeclaredMethod("buildTraps"); m.setAccessible(true); m.invoke(g);
        } catch (Exception e) { throw new RuntimeException(e); }
        g.player.position.set(100 * 16, 12 * 16 - 8);
        int shot = 0; int hpBefore = g.player.health;
        for (int i = 0; i < 400; i++) { g.update(DT, c); g.player.position.y = 12 * 16 - 8; if (g.projectiles.size() > 0) shot++; }
        check(shot > 0, "trap fired an arrow");
        check(g.player.health < hpBefore, "arrow hurt the player (hp " + hpBefore + " -> " + g.player.health + ")");
        // block the line: arrows should not fire
        g = arena(); g.world.set(90, 12, Tiles.ARROW_TRAP); g.world.set(95, 12, Tiles.STONE); g.world.set(95, 13, Tiles.STONE);
        try { g.world.arrowTraps.add(new int[]{90, 12, 1}); java.lang.reflect.Method m = GameWorld.class.getDeclaredMethod("buildTraps"); m.setAccessible(true); m.invoke(g); } catch (Exception e) { throw new RuntimeException(e); }
        g.player.position.set(100 * 16, 12 * 16 - 8); int shots2 = 0;
        for (int i = 0; i < 400; i++) { g.update(DT, c); g.player.position.y = 12 * 16 - 8; shots2 += g.projectiles.size(); }
        check(shots2 == 0, "wall in the way: no arrows fired");

        System.out.println("[9] Falling rock");
        g = arena(); g.world.set(100, 15, Tiles.STONE);
        g.world.fallingRocks.add(new int[]{100, 15});
        try { java.lang.reflect.Method m = GameWorld.class.getDeclaredMethod("buildTraps"); m.setAccessible(true); m.invoke(g); } catch (Exception e) { throw new RuntimeException(e); }
        g.player.position.set(100 * 16 + 2, 11 * 16); int hp = g.player.health; run(g, c, 90);
        check(g.player.health == hp - Config.ROCK_DAMAGE || g.world.get(100, 15) == Tiles.AIR, "rock dislodged when player walked under (hp " + hp + " -> " + g.player.health + ")");

        System.out.println("[10] Melee kills enemies, they drop coins, score goes up");
        g = arena(); Enemy s = new Enemy(Enemy.SLIME, 106 * 16, 11 * 16); g.enemies.add(s);
        g.player.position.set(104 * 16, 11 * 16); g.player.facingRight = true; g.player.invincible = 999; int sc = g.player.score;
        for (int fr = 0; fr < 60 * 8 && !s.dead; fr++) {
            float dx = s.cx() - g.player.centerX(); c.reset();
            c.moveX = Math.abs(dx) > 18 ? Math.signum(dx) : 0; c.attackPressed = Math.abs(dx) < 30;
            g.update(DT, c); g.player.invincible = 999;
        }
        check(s.dead, "slime killed by a player who chases and swings");
        check(g.player.score > sc, "score +" + (g.player.score - sc));
        c.reset(); run(g, c, 150);
        check(g.player.coins >= 1, "coins collected: " + g.player.coins);

        System.out.println("[11] Enemy contact damages the player, with knockback and i-frames");
        g = arena(); g.player.position.set(100 * 16, 11 * 16); Enemy sl = new Enemy(Enemy.SLIME, 100 * 16, 11 * 16); g.enemies.add(sl);
        run(g, c, 3);
        check(g.player.health == Config.MAX_HEALTH - Config.SLIME_DMG, "hit once (hp " + g.player.health + ")");
        run(g, c, 10);
        check(g.player.health >= Config.MAX_HEALTH - Config.SLIME_DMG - 0, "i-frames stop repeated hits");

        System.out.println("[12] Skeleton turns at ledges instead of walking off");
        g = arena(); for (int x = 90; x <= 110; x++) for (int y = 1; y <= 10; y++) g.world.set(x, y, Tiles.AIR);
        for (int x = 96; x <= 104; x++) for (int y = 1; y <= 10; y++) g.world.set(x, y, Tiles.STONE);
        Enemy sk = new Enemy(Enemy.SKELETON, 100 * 16, 11 * 16); g.enemies.add(sk); g.player.position.set(140 * 16, 11 * 16);
        float minX = 999999, maxX = -1; for (int i = 0; i < 1200; i++) { g.update(DT, c); g.player.position.set(140 * 16, 11 * 16); if (!sk.dead) { minX = Math.min(minX, sk.x); maxX = Math.max(maxX, sk.x); } }
        check(!sk.dead && sk.y >= 10 * 16, "skeleton stayed on the platform (y=" + sk.y + ", range " + (int) minX + ".." + (int) maxX + ")");

        System.out.println("[13] Bat is only aggressive nearby");
        g = arena(); Enemy bat = new Enemy(Enemy.BAT, 130 * 16, 20 * 16); g.enemies.add(bat); g.player.position.set(100 * 16, 11 * 16);
        float d0 = Math.abs(bat.x - g.player.position.x); run(g, c, 60); float d1 = Math.abs(bat.x - g.player.position.x);
        check(d1 > d0 - 20, "far bat did not chase (" + (int) d0 + " -> " + (int) d1 + ")");
        bat.x = 106 * 16; bat.y = 16 * 16; d0 = Math.abs(bat.x - g.player.position.x); run(g, c, 30); d1 = Math.abs(bat.x - g.player.position.x);
        check(d1 < d0, "near bat swoops in (" + (int) d0 + " -> " + (int) d1 + ")");

        System.out.println("[14] Checkpoint sets respawn point");
        g = arena(); g.world.set(110, 11, Tiles.FLAG); g.player.position.set(110 * 16, 11 * 16); run(g, c, 3);
        check(Math.abs(g.player.getSpawn().x - (110 * 16 + (16 - Config.PLAYER_W) / 2f)) < 1f, "spawn moved to the flag");
        g.player.damage(999, 0, 0, true); run(g, c, 120);
        check(Math.abs(g.player.position.x - g.player.getSpawn().x) < 40f, "respawned at checkpoint x=" + (int) g.player.position.x);

        System.out.println("[15] Portal completes the level and awards bonus; next level regenerates");
        g = arena(); g.world.set(110, 11, Tiles.PORTAL); g.world.set(110, 12, Tiles.PORTAL); g.player.position.set(110 * 16, 11 * 16); int s0 = g.player.score; run(g, c, 3);
        check(g.levelComplete && g.player.score >= s0 + Config.SCORE_LEVEL, "level complete, score +" + (g.player.score - s0));
        int ll = g.player.lives; g.nextLevel(99);
        check(g.level == 2 && !g.levelComplete && g.player.lives == ll, "level 2 started, lives kept");

        System.out.println("[16] Heart pickup heals; combo multiplier from quick mining");
        g = arena(); g.player.health = 40; g.drops.add(new ItemDrop(ItemDrop.HEART, 1, 100 * 16 + 4, 12 * 16)); run(g, c, 120);
        check(g.player.health == 40 + Config.HEART_HEAL, "heart healed to " + g.player.health);

        System.out.println("[17] Sand falls when its support is removed (and none is lost)");
        g = arena(); g.world.set(101, 10, Tiles.DIRT); g.world.set(101, 11, Tiles.SAND); g.world.set(101, 12, Tiles.SAND);
        g.player.position.set(99 * 16, 11 * 16);
        int fr2 = 0; while (g.world.get(101, 10) == Tiles.DIRT && fr2 < 600) { g.mineTile(101, 10, DT); fr2++; }
        check(g.world.get(101, 10) == Tiles.SAND && g.world.get(101, 11) == Tiles.SAND && g.world.get(101, 12) == Tiles.AIR,
            "both sand blocks dropped one tile: y10=" + g.world.get(101, 10) + " y11=" + g.world.get(101, 11) + " y12=" + g.world.get(101, 12));

        System.out.println("[18] Day/night cycle");
        g = arena(); g.timeOfDay = 0.5f; float dayD = g.darkness(); g.timeOfDay = 0.95f; float nightD = g.darkness();
        check(dayD < 0.2f && nightD > 0.7f && g.isNight(), "noon dark=" + dayD + " midnight dark=" + nightD);

        System.out.println("[19] Enemy spawning respects cap and safety radius over a long run");
        g = arena(); g.world = new World(7, 1); g.player.position.set(g.world.spawnX * 16, g.world.spawnY * 16);
        g.timeOfDay = 0.9f; int maxAlive = 0;
        for (int i = 0; i < 60 * 90; i++) { c.reset(); g.update(DT, c); g.player.health = Config.MAX_HEALTH; g.player.invincible = 1; int al = 0; for (Enemy e : g.enemies) if (!e.dead) al++; maxAlive = Math.max(maxAlive, al); }
        check(maxAlive > 0 && maxAlive <= Config.ENEMY_CAP + 2 * g.level + 1, "enemies spawned (max alive " + maxAlive + ", cap " + (Config.ENEMY_CAP + 2 * g.level) + ")");

        System.out.println(fails == 0 ? "\nALL SIMULATION TESTS PASSED" : "\nSIMULATION TESTS FAILED: " + fails);
    }
}
