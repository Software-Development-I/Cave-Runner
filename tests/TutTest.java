import com.badlogic.Terraria.*;

public class TutTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }
    static final float DT = 1 / 60f;

    public static void main(String[] a) {
        Player.Controls c = new Player.Controls();
        GameWorld g = new GameWorld(null, 5, 1);
        World w = g.world;
        for (int x = 0; x < World.WIDTH; x++) for (int y = 1; y < World.HEIGHT; y++) w.set(x, y, y <= 10 ? Tiles.STONE : Tiles.AIR);
        g.enemies.clear(); g.player.position.set(100 * 16, 11 * 16); g.player.invincible = 9999;
        Tutorial t = g.tutorial;

        System.out.println("[1] Tutorial starts on MOVE and does not advance while idle");
        check(t.step == Tutorial.Step.MOVE && t.active(), "starts at MOVE");
        for (int f = 0; f < 300; f++) { c.reset(); g.update(DT, c); g.player.invincible = 9999; }
        check(t.step == Tutorial.Step.MOVE, "standing still does not advance (still " + t.step + ")");

        System.out.println("[2] Each step advances only on the matching action");
        c.reset(); c.moveX = 1; for (int f = 0; f < 60; f++) { g.update(DT, c); g.player.invincible = 9999; }
        check(t.step == Tutorial.Step.JUMP, "walking ~1s -> JUMP (now " + t.step + ")");
        c.reset(); c.moveX = 1; for (int f = 0; f < 60; f++) { g.update(DT, c); }
        check(t.step == Tutorial.Step.JUMP, "walking more does NOT skip past JUMP");
        c.reset(); c.jumpPressed = true; g.update(DT, c); c.reset();
        check(t.step == Tutorial.Step.MINE, "jumping -> MINE (now " + t.step + ")");
        for (int f = 0; f < 90; f++) { c.reset(); g.update(DT, c); }
        g.mineTile((int) (g.player.centerX() / 16) + 1, 10, 3f); c.reset(); g.update(DT, c);   // a real game runs a frame after each action
        check(t.step == Tutorial.Step.PLACE, "mining a block -> PLACE (now " + t.step + ")");
        g.player.inventory.add(Tiles.DIRT, 3); for (int i = 0; i < Inventory.HOTBAR; i++) if (g.player.inventory.ids[i] == Tiles.DIRT) g.player.inventory.select(i);
        g.placeTile((int) (g.player.centerX() / 16) + 1, 10); c.reset(); g.update(DT, c);
        check(t.step == Tutorial.Step.ATTACK, "placing a block -> ATTACK (now " + t.step + ")");
        c.reset(); c.attackPressed = true; g.update(DT, c); c.reset();
        check(t.step == Tutorial.Step.BAG, "swinging -> BAG (now " + t.step + ")");
        t.bagOpened = true; g.update(DT, c);
        check(t.step == Tutorial.Step.DONE && !t.active(), "opening the bag -> DONE, tutorial inactive");

        System.out.println("[3] Prompts adapt to keyboard vs touch and are never empty while active");
        Tutorial x = new Tutorial();
        boolean allOk = true;
        for (Tutorial.Step s : new Tutorial.Step[]{Tutorial.Step.MOVE, Tutorial.Step.JUMP, Tutorial.Step.MINE, Tutorial.Step.PLACE, Tutorial.Step.ATTACK, Tutorial.Step.BAG}) {
            x.step = s; if (x.prompt(false).isEmpty() || x.prompt(true).isEmpty() || x.prompt(true).equals(x.prompt(false))) allOk = false; }
        check(allOk, "all 6 steps have distinct keyboard + touch prompts");
        x.step = Tutorial.Step.DONE; check(x.prompt(false).isEmpty(), "no prompt once DONE");

        System.out.println("[4] Skip works and stays skipped");
        Tutorial y = new Tutorial(); y.skip();
        check(!y.active() && y.step == Tutorial.Step.DONE, "skip() ends the tutorial");
        Player.Controls cc = new Player.Controls(); GameWorld g2 = new GameWorld(null, 1, 1); g2.tutorial.skip();
        for (int f = 0; f < 120; f++) g2.update(DT, cc);
        check(g2.tutorial.step == Tutorial.Step.DONE, "a skipped tutorial never restarts");

        System.out.println("[5] Tutorial does not advance while the player is dead");
        Tutorial z = new Tutorial(); Player p = new Player(0, 0, null); p.dead = true; p.position.x = 500; z.update(1f, p, cc); p.position.x = 900; z.update(1f, p, cc);
        check(z.step == Tutorial.Step.MOVE, "dead player cannot complete steps");

        System.out.println(fails == 0 ? "\nALL TUTORIAL TESTS PASSED" : "\nTUTORIAL TESTS FAILED: " + fails);
    }
}
