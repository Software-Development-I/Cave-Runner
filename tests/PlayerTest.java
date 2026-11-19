import com.badlogic.Terraria.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class PlayerTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }

    static World flatWorld() {
        World w = new World(1, 1);
        // wipe to a clean box: solid floor at y<=10, air above, walls at x=100 & x=120
        for (int x = 0; x < World.WIDTH; x++) for (int y = 1; y < World.HEIGHT; y++) w.set(x, y, y <= 10 ? Tiles.STONE : Tiles.AIR);
        return w;
    }
    static Player mk(float tx, float ty) { return new Player(tx * 16f, ty * 16f, null); }
    static void step(Player p, World w, Player.Controls c, int frames) { for (int i = 0; i < frames; i++) p.update(1 / 60f, w, c); }

    public static void main(String[] a) {
        Player.Controls c = new Player.Controls();

        System.out.println("[1] Gravity, landing, grounded");
        World w = flatWorld(); Player p = mk(50, 15);
        step(p, w, c, 120);
        check(p.grounded, "player lands and is grounded");
        check(Math.abs(p.position.y - 11 * 16) < 0.01f, "player rests exactly on top of floor (y=" + p.position.y + ")");

        System.out.println("[2] Jump");
        c.reset(); c.jumpPressed = true; step(p, w, c, 1); c.reset();
        float peak = p.position.y; for (int i = 0; i < 60; i++) { p.update(1 / 60f, w, c); peak = Math.max(peak, p.position.y); }
        float jumpH = (peak - 11 * 16) / 16f;
        check(jumpH > 2.5f && jumpH < 4.0f, "jump height ~" + String.format("%.2f", jumpH) + " tiles (expect ~3)");

        System.out.println("[3] Fall damage");
        w = flatWorld(); p = mk(50, 60); int hp0 = p.health; p.invincible = 0;
        step(p, w, c, 200);
        check(p.health < hp0, "long fall hurts (hp " + hp0 + " -> " + p.health + ")");
        w = flatWorld(); p = mk(50, 13); p.invincible = 0; hp0 = p.health; step(p, w, c, 120);
        check(p.health == hp0, "short drop is harmless");

        System.out.println("[4] Wall jump works WITHOUT pushing into the wall (original bug)");
        w = flatWorld(); for (int y = 11; y < 60; y++) w.set(60, y, Tiles.STONE);
        p = mk(59, 20); p.position.x = 60 * 16 - p.width; p.position.y = 25 * 16; p.velocity.setZero();
        c.reset(); c.moveX = 1f; step(p, w, c, 5);  // touch the wall
        c.reset(); step(p, w, c, 2);                  // release: not pushing anymore
        check(p.touchingWallRight, "wall contact still detected when not pushing");
        float vyBefore = p.velocity.y; c.reset(); c.jumpPressed = true; step(p, w, c, 1);
        check(p.velocity.y > 100f && p.velocity.x < 0, "wall-jump kicks up and away (vx=" + p.velocity.x + ", vy=" + p.velocity.y + ")");

        System.out.println("[5] Wall slide caps fall speed");
        w = flatWorld(); for (int y = 11; y < 100; y++) w.set(60, y, Tiles.STONE);
        p = mk(59, 20); p.position.x = 60 * 16 - p.width; p.position.y = 90 * 16; c.reset(); c.moveX = 1f; step(p, w, c, 40);
        check(p.velocity.y >= Config.WALL_SLIDE_MAX - 0.5f, "wall-slide speed " + p.velocity.y);

        System.out.println("[6] Spikes damage + bounce");
        w = flatWorld(); w.set(50, 11, Tiles.SPIKES); p = mk(50, 15); p.position.x = 50 * 16 + 2; p.invincible = 0; c.reset();
        step(p, w, c, 60);
        check(p.health == Config.MAX_HEALTH - Config.SPIKE_DAMAGE, "spike did " + (Config.MAX_HEALTH - p.health) + " dmg");

        System.out.println("[7] Invincibility frames prevent damage stacking");
        w = flatWorld(); p = mk(50, 12); p.invincible = 0;
        boolean first = p.damage(10, 0, 0, false), second = p.damage(10, 0, 0, false);
        check(first && !second && p.health == 90, "second hit blocked during i-frames");

        System.out.println("[8] Lava: DoT ignores i-frames, slows you");
        w = flatWorld(); for (int x = 48; x <= 52; x++) for (int y = 11; y <= 14; y++) w.set(x, y, Tiles.LAVA);
        p = mk(50, 13); p.invincible = 0; step(p, w, c, 60);
        check(p.inLava && p.health < Config.MAX_HEALTH - 15, "lava burning: hp=" + p.health);

        System.out.println("[9] Death, lives, respawn, game over");
        p = mk(50, 12); p.invincible = 0; p.damage(999, 0, 0, true);
        check(p.dead && p.health == 0, "player dies at 0 hp");
        boolean alive = p.loseLifeAndRespawn();
        check(alive && !p.dead && p.health == Config.MAX_HEALTH && p.lives == Config.START_LIVES - 1, "respawn with full hp, lives=" + p.lives);
        p.lives = 1; p.damage(999, 0, 0, true);
        check(!p.loseLifeAndRespawn() && p.lives == 0, "last life lost => game over");

        System.out.println("[10] Combo score");
        p = mk(50, 12); p.addScore(10); int s1 = p.score; p.addScore(10); p.addScore(10);
        check(p.comboMultiplier >= 2 && p.score > 30, "combo raises multiplier (x" + p.comboMultiplier + ", score " + p.score + ")");
        p.update(Config.COMBO_WINDOW + 0.5f, flatWorld(), c); 
        for (int i = 0; i < 200; i++) p.update(1 / 60f, flatWorld(), c);
        check(p.comboMultiplier == 1, "combo decays after " + Config.COMBO_WINDOW + "s");

        System.out.println("[11] Attack cooldown");
        p = mk(50, 12); c.reset(); c.attackPressed = true;
        check(p.tryAttack(c) != null, "first attack fires");
        check(p.tryAttack(c) == null, "second blocked by cooldown");

        System.out.println("[12] No tunnelling through floor on a lag spike");
        w = flatWorld(); p = mk(50, 40); p.update(1.0f, w, c); step(p, w, c, 300);
        check(p.position.y >= 11 * 16 - 0.01f, "still above floor after huge dt (y=" + p.position.y + ")");

        System.out.println(fails == 0 ? "\nALL PLAYER TESTS PASSED" : "\nPLAYER TESTS FAILED: " + fails);
    }
}
