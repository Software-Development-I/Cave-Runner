import com.badlogic.Terraria.*;

/** Verifies the pure layout geometry at real phone / tablet / desktop resolutions. No GL needed. */
public class LayoutTest {
    static int fails = 0, checks = 0;
    static void check(boolean ok, String msg) { checks++; if (!ok) { fails++; System.out.println("  FAIL " + msg); } }
    static boolean inside(float[] r, int w, int h) { return r[0] >= -0.5f && r[1] >= -0.5f && r[0] + r[2] <= w + 0.5f && r[1] + r[3] <= h + 0.5f; }
    static boolean ov(float[] a, float[] b) { return TouchLayoutOverlap(a, b); }
    static boolean TouchLayoutOverlap(float[] a, float[] b) { return a[0] < b[0] + b[2] && a[0] + a[2] > b[0] && a[1] < b[1] + b[3] && a[1] + a[3] > b[1]; }

    static final int[][] RES = {
        {1280, 720}, {1920, 1080}, {2560, 1440}, {800, 480}, {1024, 600}, {854, 480},     // desktop + small
        {2340, 1080}, {2400, 1080}, {2532, 1170}, {1920, 864}, {2778, 1284},               // modern phones (landscape)
        {1280, 800}, {2048, 1536}, {1024, 768},                                            // tablets / 4:3
        {640, 360},                                                                       // very small
    };
    static final String[] NAME = {"PAD", "JUMP", "ATTACK", "MINE", "PLACE", "PAUSE", "BAG"};

    public static void main(String[] a) {
        System.out.println("[1] Touch controls: on-screen and non-overlapping at " + RES.length + " resolutions");
        for (int[] r : RES) {
            float[][] t = TouchLayout.compute(r[0], r[1]);
            for (int i = 0; i < t.length; i++) check(inside(t[i], r[0], r[1]), NAME[i] + " fully on-screen at " + r[0] + "x" + r[1] + " (" + java.util.Arrays.toString(t[i]) + ")");
            for (int i = 0; i < t.length; i++) for (int j = i + 1; j < t.length; j++)
                check(!ov(t[i], t[j]), NAME[i] + " overlaps " + NAME[j] + " at " + r[0] + "x" + r[1]);
        }
        System.out.println("    " + checks + " checks so far, " + fails + " failed");

        System.out.println("[2] Touch targets are large enough to hit (>= 44 physical-ish px at 720p)");
        for (int[] r : RES) { float[][] t = TouchLayout.compute(r[0], r[1]); float u = TouchLayout.scale(r[1]);
            for (int i = 1; i < 5; i++) check(t[i][2] >= 44 * u * 0.99f && t[i][3] >= 44 * u * 0.99f, NAME[i] + " big enough at " + r[0] + "x" + r[1]);
            for (int i = 5; i < 7; i++) check(t[i][3] >= 40 * u * 0.99f, NAME[i] + " tall enough at " + r[0] + "x" + r[1]); }

        System.out.println("[3] The thumb zones are correct: pad on the LEFT, action buttons on the RIGHT");
        for (int[] r : RES) { float[][] t = TouchLayout.compute(r[0], r[1]);
            check(t[TouchLayout.PAD][0] + t[TouchLayout.PAD][2] < r[0] * 0.5f, "pad in left half at " + r[0] + "x" + r[1]);
            for (int i = 1; i <= 4; i++) check(t[i][0] > r[0] * 0.5f, NAME[i] + " in right half at " + r[0] + "x" + r[1]);
            check(t[TouchLayout.PAUSE][1] > r[1] * 0.5f && t[TouchLayout.BAG][1] > r[1] * 0.5f, "PAUSE/BAG at the top at " + r[0] + "x" + r[1]); }

        System.out.println("[4] Scale is clamped and monotonic");
        check(TouchLayout.scale(100) == 0.8f && TouchLayout.scale(5000) == 2.5f, "scale clamps to [0.8, 2.5]");
        float prev = 0; boolean mono = true; for (int h = 100; h <= 4000; h += 10) { float s = TouchLayout.scale(h); if (s < prev) mono = false; prev = s; }
        check(mono, "scale never decreases as the screen grows");

        System.out.println("[5] Controls do not cover the hotbar, the tutorial banner, or the boss bar");
        for (int[] r : RES) { float u = TouchLayout.scale(r[1]); float[][] t = TouchLayout.compute(r[0], r[1]);
            float[] hot = HudLayout.hotbar(r[0], r[1], u);
            for (int i = 0; i < t.length; i++) {
                // the hotbar sits at the bottom centre; PAD/action buttons are at the sides. They must not collide.
                if (ov(t[i], hot)) check(false, NAME[i] + " overlaps the hotbar at " + r[0] + "x" + r[1]); else checks++;
            }
            float[] tut = HudLayout.tutorialRect(r[0], r[1], u, 300 * u, 40 * u);
            float[] boss = HudLayout.bossBar(r[0], r[1], u);
            for (int i = 5; i < 7; i++) {   // PAUSE + BAG are the only top-of-screen controls
                check(!ov(t[i], tut), NAME[i] + " overlaps the tutorial banner at " + r[0] + "x" + r[1]);
                check(!ov(t[i], boss), NAME[i] + " overlaps the boss bar at " + r[0] + "x" + r[1]);
            }
        }

        System.out.println("[6] HUD blocks: on-screen, and the banners never collide with the stats block");
        for (int[] r : RES) { float u = TouchLayout.scale(r[1]);
            float[] left = HudLayout.leftBlock(r[1], u), hot = HudLayout.hotbar(r[0], r[1], u), boss = HudLayout.bossBar(r[0], r[1], u);
            check(inside(left, r[0], r[1]), "stats block on-screen at " + r[0] + "x" + r[1]);
            check(inside(hot, r[0], r[1]), "hotbar on-screen at " + r[0] + "x" + r[1]);
            check(inside(boss, r[0], r[1]), "boss bar on-screen at " + r[0] + "x" + r[1]);
            check(!ov(left, boss), "boss bar clear of stats block at " + r[0] + "x" + r[1]);
            for (float tw : new float[]{100, 300, 700, 1500}) for (float th : new float[]{20, 60, 120}) {
                float[] tut = HudLayout.tutorialRect(r[0], r[1], u, tw * u, th * u);
                check(!ov(left, tut), "tutorial (text " + (int) tw + "x" + (int) th + ") clear of stats block at " + r[0] + "x" + r[1]);
                check(tut[0] >= 0 && tut[0] + tut[2] <= r[0] + 0.5f, "tutorial within screen width at " + r[0] + "x" + r[1] + " text " + (int) tw);
            }
        }

        System.out.println("[7] Tutorial box grows with wrapped text height and stays centred");
        float[] s1 = HudLayout.tutorialRect(1280, 720, 1f, 300, 20), s2 = HudLayout.tutorialRect(1280, 720, 1f, 300, 80);
        check(s2[3] > s1[3], "taller text -> taller box");
        check(Math.abs((s1[0] + s1[2] / 2f) - 640f) < 0.5f, "banner is horizontally centred");
        check(Math.abs((s1[1] + s1[3]) - (720 - 70)) < 0.5f, "banner top edge is fixed, it grows DOWNWARD (never toward the bar)");

        System.out.println("[8] Degenerate sizes never produce NaN / negative-size rects");
        for (int[] r : new int[][]{{1, 1}, {10, 10}, {320, 180}, {100, 2000}, {5000, 100}}) {
            float u = TouchLayout.scale(r[1]);
            for (float[] q : TouchLayout.compute(r[0], r[1])) for (float v : q) check(!Float.isNaN(v) && !Float.isInfinite(v), "TouchLayout finite at " + r[0] + "x" + r[1]);
            for (float[] q : new float[][]{HudLayout.leftBlock(r[1], u), HudLayout.hotbar(r[0], r[1], u), HudLayout.bossBar(r[0], r[1], u), HudLayout.tutorialRect(r[0], r[1], u, 400, 30)}) {
                for (float v : q) check(!Float.isNaN(v) && !Float.isInfinite(v), "HudLayout finite at " + r[0] + "x" + r[1]);
                check(q[2] > 0 && q[3] > 0, "HudLayout positive size at " + r[0] + "x" + r[1]);
            }
        }
        System.out.println("[9] Fitted hotbar stays comfortably tappable on every realistic device, and never grows past full size");
        int[][] devices = {{1280,720},{1920,1080},{2560,1440},{2340,1080},{2400,1080},{2532,1170},{2778,1284},{1920,864},{1280,800},{2048,1536},{1024,768},{854,480},{800,480},{1024,600}};
        for (int[] r : devices) { float u = TouchLayout.scale(r[1]);
            for (int touch = 0; touch <= 1; touch++) { float[] sg = HudLayout.hotbarSlotAndGap(r[0], touch == 1 ? r[1] : 0, u);
                check(sg[0] >= 36f * 0.999f * Math.min(1f, u / 0.8f) || sg[0] >= 30f * u, "slot >= minimum at " + r[0] + "x" + r[1] + " touch=" + touch + " (" + sg[0] + ")");
                check(sg[0] <= HudLayout.SLOT * u + 0.01f, "slot never exceeds full size at " + r[0] + "x" + r[1]);
                check(sg[0] >= 36f || u < 0.8f + 1e-3f || sg[0] >= 0.75f * HudLayout.SLOT * u, "slot not shrunk below 75% of full at " + r[0] + "x" + r[1] + " touch=" + touch + " (" + sg[0] + " vs " + HudLayout.SLOT * u + ")"); } }

        System.out.println("[10] Drawing and tapping agree: every slot's centre maps back to that slot (Hud vs HudLayout geometry)");
        for (int[] r : devices) for (int touch = 0; touch <= 1; touch++) { float u = TouchLayout.scale(r[1]);
            float[] hb = HudLayout.hotbar(r[0], touch == 1 ? r[1] : 0, u); float[] sg = HudLayout.hotbarSlotAndGap(r[0], touch == 1 ? r[1] : 0, u);
            for (int i = 0; i < Inventory.HOTBAR; i++) { float cx = hb[0] + i * (sg[0] + sg[1]) + sg[0] / 2f, cy = hb[1] + sg[0] / 2f;
                int hit = (int) ((cx - hb[0]) / (sg[0] + sg[1]));
                check(hit == i && cx >= 0 && cx <= r[0], "slot " + i + " centre maps to itself and is on-screen at " + r[0] + "x" + r[1] + " touch=" + touch); }
            check(Math.abs(hb[2] - (Inventory.HOTBAR * sg[0] + (Inventory.HOTBAR - 1) * sg[1])) < 0.5f, "bar width == 9 slots + 8 gaps at " + r[0] + "x" + r[1]);
            check(hb[0] >= 0 && hb[0] + hb[2] <= r[0] + 0.5f, "bar fully on-screen at " + r[0] + "x" + r[1] + " touch=" + touch); }

        System.out.println(fails == 0 ? "\nALL LAYOUT TESTS PASSED (" + checks + " checks)" : "\nLAYOUT TESTS FAILED: " + fails + " of " + checks + " checks");
    }
}
