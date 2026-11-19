import com.badlogic.Terraria.*;

public class BossTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }
    static final float DT = 1 / 60f;

    static GameWorld arena() {
        GameWorld g = new GameWorld(null, 5, 2);
        World w = g.world;
        for (int x = 0; x < World.WIDTH; x++) for (int y = 1; y < World.HEIGHT; y++) w.set(x, y, y <= 10 ? Tiles.STONE : Tiles.AIR);
        g.enemies.clear(); g.projectiles.clear(); g.drops.clear();
        g.player.position.set(100 * 16, 11 * 16); g.player.velocity.setZero(); g.player.invincible = 0; g.player.health = Config.MAX_HEALTH;
        g.bossSpawned = true;   // stop auto-spawn so we control the fight
        return g;
    }
    static Enemy boss(GameWorld g, float tileX) { Enemy b = new Enemy(Enemy.BOSS, tileX * 16, 11 * 16); g.enemies.add(b); return b; }

    public static void main(String[] a) {
        Player.Controls c = new Player.Controls();

        System.out.println("[1] Boss telegraphs before slamming (never an instant hit)");
        GameWorld g = arena(); Enemy b = boss(g, 104);
        int windupFrames = 0, firstSlam = -1; boolean sawWindup = false;
        for (int f = 0; f < 60 * 12 && firstSlam < 0; f++) {
            c.reset(); g.update(DT, c); g.player.position.set(100 * 16, 11 * 16); g.player.health = Config.MAX_HEALTH;
            if (b.bossState == Enemy.B_WINDUP) { windupFrames++; sawWindup = true; }
            if (b.slamLanded) firstSlam = f;
        }
        check(sawWindup, "boss entered a WINDUP state");
        check(windupFrames >= 25, "windup lasted " + windupFrames + " frames (" + String.format("%.2f", windupFrames / 60f) + "s) - player has time to react");
        check(firstSlam > 0, "boss eventually landed a slam");

        System.out.println("[2] Shockwave dodge: isolated from wild enemies (they used to add ~5% random failures)");
        int hitGrounded = 0, hitAir = 0, slamsG = 0, slamsA = 0;
        for (int trial = 0; trial < 20; trial++) {
            for (int mode = 0; mode < 2; mode++) {
                g = arena(); b = boss(g, 108); boolean airborne = mode == 1;      // boss 8 tiles away: only the shockwave can reach
                g.player.position.set(100 * 16, 11 * 16);
                int hp0 = g.player.health; int slams = 0; boolean jumped = false;
                for (int f = 0; f < 60 * 25 && slams == 0; f++) {
                    c.reset();
                    // a well-timed player jumps once the boss is past its apex (this mirrors what a human reads off the telegraph)
                    if (airborne && !jumped && b.bossState == Enemy.B_AIR && b.vy <= 0 && g.player.grounded) { c.jumpPressed = true; jumped = true; }
                    g.update(DT, c);
                    g.player.position.x = 100 * 16; g.player.velocity.x = 0;
                    b.x = Math.max(b.x, 106 * 16);                               // keep the boss body clear of the player
                    g.enemies.removeIf(e -> e.type != Enemy.BOSS);               // no wild spawns: this test is about the shockwave only
                    if (b.slamLanded) slams++;
                }
                if (mode == 0) { slamsG += slams; if (g.player.health < hp0) hitGrounded++; } else { slamsA += slams; if (g.player.health < hp0) hitAir++; }
            }
        }
        check(slamsG == 20 && hitGrounded == 20, "a player who never jumps is hit by every slam (" + hitGrounded + "/20)");
        check(slamsA == 20 && hitAir == 0, "a well-timed jump avoids every shockwave (" + hitAir + "/20 hit)");

        System.out.println("[2b] The dodge window is fair: wide enough for a human, for both normal and enraged boss");
        for (int mode = 0; mode < 2; mode++) {
            int ok = 0;
            for (int k = 0; k <= 90; k += 1) {
                g = arena(); b = boss(g, 108); if (mode == 1) b.hp = b.maxHp / 2 - 1;
                int airStart = -1; boolean jumped = false, dodged = false, done = false;
                for (int f = 0; f < 60 * 30 && !done; f++) {
                    c.reset();
                    if (b.bossState == Enemy.B_AIR && airStart < 0) airStart = f;
                    if (airStart >= 0 && !jumped && f - airStart == k && g.player.grounded) { c.jumpPressed = true; jumped = true; }
                    int hp = g.player.health; g.update(DT, c);
                    g.player.position.x = 100 * 16; g.player.velocity.x = 0; b.x = Math.max(b.x, 106 * 16);
                    g.enemies.removeIf(e -> e.type != Enemy.BOSS);
                    if (b.slamLanded) { dodged = g.player.health == hp; done = true; }
                }
                if (dodged) ok++;
            }
            check(ok >= 30, (mode == 0 ? "normal" : "enraged") + " boss: " + ok + " successful jump-start frames = " + String.format("%.2f", ok / 60f) + "s window (need >= 0.5s)");
        }

        System.out.println("[2c] Regression: the boss can never stall - it slams on a fixed rhythm even against a player kiting from far away");
        int worstGap = 0, kiteTrials = 0, noSlam = 0;
        for (int trial = 0; trial < 40; trial++) {
            for (float dist : new float[]{140f, 220f, 400f}) {                        // always OUTSIDE the 96px "close" trigger
                g = arena(); b = boss(g, 104); g.player.invincible = 99999; g.player.health = 99999;
                int last = 0, gap = 0, slams = 0;
                for (int f = 0; f < 60 * 25; f++) {
                    c.reset(); g.update(DT, c); g.player.invincible = 99999; g.player.health = 99999;
                    g.player.position.x = b.x - dist; g.player.velocity.x = 0;       // the player keeps backing away: worst case for the old dice-based AI
                    g.enemies.removeIf(e -> e.type != Enemy.BOSS);
                    if (b.slamLanded) { slams++; worstGap = Math.max(worstGap, f - last); last = f; }
                }
                kiteTrials++; if (slams == 0) noSlam++;
                worstGap = Math.max(worstGap, 60 * 25 - last);
            }
        }
        check(noSlam == 0, "boss slammed at least once in 25s in every one of " + kiteTrials + " kiting trials (stalled: " + noSlam + ")");
        check(worstGap <= 60 * 9, "longest stretch between slams was " + String.format("%.1f", worstGap / 60f) + "s (must be <= 9s, so the fight never drags)");

        System.out.println("[3] Boss is harmless while recovering (fair window to attack)");
        g = arena(); b = boss(g, 100); b.bossState = Enemy.B_RECOVER; b.bossTimer = 5f;
        g.player.position.set(100 * 16, 11 * 16); int hp = g.player.health; for (int f = 0; f < 60; f++) { c.reset(); g.update(DT, c); b.bossState = Enemy.B_RECOVER; b.bossTimer = 5f; }
        check(g.player.health == hp, "no contact damage during RECOVER (hp " + hp + " -> " + g.player.health + ")");

        System.out.println("[4] Player can actually damage and kill the boss");
        g = arena(); b = boss(g, 100); b.x = 104 * 16; g.player.facingRight = true; g.player.invincible = 9999; g.player.health = 9999;
        int hp0 = b.hp; float t = 0; c.reset();
        for (int f = 0; f < 60 * 120 && !b.dead; f++) {
            float dx = b.cx() - g.player.centerX(); c.reset();
            c.moveX = Math.abs(dx) > 30 ? Math.signum(dx) : 0; c.attackPressed = Math.abs(dx) < 46; g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; t += DT;
        }
        check(b.dead, "boss defeated by a chasing, swinging player in " + String.format("%.0f", t) + "s (fight length is reasonable: " + (t > 8 && t < 100) + ")");
        check(g.bossDefeated, "bossDefeated flag set");
        check(g.player.score >= Config.SCORE_BOSS, "big score bonus awarded (" + g.player.score + ")");

        System.out.println("[5] Enrage at half health, and minions are capped");
        g = arena(); b = boss(g, 104); b.hp = b.maxHp / 2 - 1; g.player.invincible = 9999; g.player.health = 9999;
        for (int f = 0; f < 60 * 30; f++) { c.reset(); g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; g.player.position.set(100 * 16, 11 * 16); }
        int slimes = 0, peak = 0;
        for (Enemy e : g.enemies) if (!e.dead && e.minion) slimes++;
        check(b.enraged, "boss enraged below 50% hp");
        check(slimes > 0, "boss summoned minions (" + slimes + ")");
        g = arena(); b = boss(g, 104); b.hp = b.maxHp / 2 - 1; g.player.invincible = 9999; g.player.health = 9999;
        for (int f = 0; f < 60 * 240; f++) { c.reset(); g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; g.player.position.set(100 * 16, 11 * 16);
            int m = 0; for (Enemy e : g.enemies) if (!e.dead && e.minion) m++; peak = Math.max(peak, m); }
        check(peak == 5, "enraged boss keeps summoning until it reaches the cap of exactly 5, never beyond (peak " + peak + ")");
        // killing the boss clears its minions
        g = arena(); b = boss(g, 104); b.hp = b.maxHp / 2 - 1; g.player.invincible = 9999; g.player.health = 9999;
        for (int f = 0; f < 60 * 20; f++) { c.reset(); g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; g.player.position.set(100 * 16, 11 * 16); }
        b.hp = 1; b.dead = false; boolean minionsBefore = false; for (Enemy e : g.enemies) if (e.minion && !e.dead) minionsBefore = true;
        Enemy bb = b; g.player.position.set((bb.x + bb.w / 2) - 6, 11 * 16); g.player.facingRight = true;
        for (int f = 0; f < 60 * 30 && !bb.dead; f++) { float dx = bb.cx() - g.player.centerX(); c.reset(); c.moveX = Math.abs(dx) > 30 ? Math.signum(dx) : 0; c.attackPressed = Math.abs(dx) < 46; g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; }
        int left = 0; for (Enemy e : g.enemies) if (e.minion && !e.dead) left++;
        check(!minionsBefore || bb.dead && left == 0, "defeating the boss clears its minions (had minions: " + minionsBefore + ", left: " + left + ")");

        System.out.println("[5b] Regression: summoning must not depend on WHICH landing path the boss takes");
        int totalSlams = 0, totalSummons = 0;
        for (int seed = 0; seed < 5; seed++) {
            g = new GameWorld(null, 40 + seed, 3); World ww = g.world;
            for (int x = 0; x < World.WIDTH; x++) for (int y = 1; y < World.HEIGHT; y++) ww.set(x, y, y <= 10 ? Tiles.STONE : Tiles.AIR);
            g.enemies.clear(); g.bossSpawned = true; g.player.position.set(100 * 16, 11 * 16);
            b = boss(g, 108); b.hp = b.maxHp / 2 - 1;
            int prev = 0;
            for (int f = 0; f < 60 * 60; f++) {
                c.reset(); g.update(DT, c); g.player.position.set(100 * 16, 11 * 16); g.player.invincible = 99999; g.player.health = 99999;
                if (g.evSlam) totalSlams++; g.clearEvents();
                for (Enemy e : g.enemies) if (e.minion && e.dead) { /* kill count irrelevant */ }
                int m = 0; for (Enemy e : g.enemies) if (!e.dead && e.minion) m++;
                if (m > prev) totalSummons += m - prev; prev = m;
                // remove minions periodically so the cap never blocks further summoning, making the count reflect boss intent
                if (f % 300 == 299) { for (Enemy e : g.enemies) if (e.minion) e.dead = true; prev = 0; }
            }
        }
        check(totalSlams > 100, "boss slammed many times (" + totalSlams + ")");
        check(totalSummons >= totalSlams / 8, "boss summoned on a steady share of slams (" + totalSummons + " summons over " + totalSlams + " slams)");

        System.out.println("[6] Boss cannot leave the arena or fall through the floor");
        g = arena(); b = boss(g, 104); g.player.invincible = 9999; g.player.health = 9999; float minY = 9999, maxX = -1, minX = 9999;
        for (int f = 0; f < 60 * 60; f++) { c.reset(); g.update(DT, c); g.player.invincible = 9999; g.player.health = 9999; g.player.position.set(100 * 16, 11 * 16); minY = Math.min(minY, b.y); minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x); }
        check(minY >= 11 * 16 - 0.5f, "boss never sank into the floor (min y=" + minY + ")");
        check(minX > 0 && maxX < World.WIDTH * 16, "boss stayed inside the world");

        System.out.println("[7] Slam sets a screen-shake/sound event");
        g = arena(); b = boss(g, 104); boolean ev = false, shook = false;
        for (int f = 0; f < 60 * 15 && !ev; f++) { c.reset(); g.update(DT, c); g.player.position.set(100 * 16, 11 * 16); g.player.health = 100; if (g.evSlam) { ev = true; shook = g.shake > 5; } g.clearEvents(); }
        check(ev && shook, "evSlam fired with strong screen shake");

        System.out.println(fails == 0 ? "\nALL BOSS TESTS PASSED" : "\nBOSS TESTS FAILED: " + fails);
    }
}
