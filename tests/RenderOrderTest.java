import java.nio.file.*; import java.util.*; import java.util.regex.*;

/**
 * Static guard for the "HUD vanished" bug class. GL cannot run headlessly, so this reads the source and checks:
 *  1. Hud.render sets the screen projection on BOTH the batch and the shape renderer BEFORE it draws anything.
 *  2. Every Main method that draws UI-space content (pixels) sets the screen matrix before batch.begin().
 *  3. Nothing mutates the batch matrix in place (getProjectionMatrix().idt()...), which leaks state between passes.
 *  4. Every batch.begin() is matched by a batch.end() within the same method.
 */
public class RenderOrderTest {
    static int fails = 0, checks = 0;
    static void check(boolean ok, String msg) { checks++; System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }

    /** Returns the body of the first method whose signature line contains `sig`. */
    static String body(String src, String sig) {
        int i = src.indexOf(sig); if (i < 0) return null;
        int open = src.indexOf('{', i), depth = 0, j = open;
        for (; j < src.length(); j++) { char c = src.charAt(j); if (c == '{') depth++; else if (c == '}') { depth--; if (depth == 0) break; } }
        return src.substring(open, j + 1);
    }
    static int count(String s, String needle) { int n = 0, i = 0; while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); } return n; }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : "core/src/main/java/com/badlogic/Terraria/";
        String main = new String(Files.readAllBytes(Paths.get(dir + "Main.java"))), hud = new String(Files.readAllBytes(Paths.get(dir + "Hud.java")));

        System.out.println("[1] Hud.render forces the pixel-space projection before drawing");
        String render = body(hud, "public void render(SpriteBatch b, ShapeRenderer sr");
        check(render != null, "found Hud.render");
        int pb = render.indexOf("b.setProjectionMatrix(screenProjection)"), ps = render.indexOf("sr.setProjectionMatrix(screenProjection)");
        int firstBegin = Math.min(render.indexOf("sr.begin("), render.indexOf("b.begin("));
        check(pb >= 0, "batch gets the screen projection");
        check(ps >= 0, "shape renderer gets the screen projection");
        check(pb >= 0 && ps >= 0 && pb < firstBegin && ps < firstBegin, "both are set BEFORE the first begin() (so nothing is drawn with the world camera)");
        check(hud.contains("screenProjection.setToOrtho2D(0, 0, Math.max(1, w), Math.max(1, h))"), "screen projection is rebuilt from the real window size on resize");

        System.out.println("[2] Main never mutates a matrix in place");
        check(!main.contains("getProjectionMatrix().idt()"), "no getProjectionMatrix().idt() (in-place mutation leaks state between draw passes)");
        check(!main.contains("setToOrtho2D(0, 0, sw, sh)"), "no ad-hoc ortho matrices; all UI shares Hud.screenMatrix()");

        System.out.println("[3] Each draw method uses exactly the projection its content needs");
        String[][] worldSpace = {{"private void drawWorld(", "camera.combined"}, {"private void drawPlayerOnTop(", "camera.combined"}};
        for (String[] w : worldSpace) { String b = body(main, w[0]); check(b != null && b.contains("setProjectionMatrix(" + w[1] + ")"), w[0].replace("private void ", "") + " sets the world camera"); }
        String[] pixelSpace = {"private void drawMenu(", "private void drawOverlayScreens("};
        for (String sig : pixelSpace) { String b = body(main, sig); check(b != null && b.contains("hud.screenMatrix()"), sig.replace("private void ", "") + " uses the screen matrix"); }
        String light = body(main, "private void drawLighting(");
        check(light != null && light.contains("shapes.setProjectionMatrix(hud.screenMatrix())") && light.contains("batch.setProjectionMatrix(camera.combined)"),
              "drawLighting: full-screen dim in pixels, glows in world space");

        System.out.println("[4] draw() order: world -> lighting -> player -> HUD -> touch -> overlays");
        String draw = body(main, "private void draw()");
        String[] order = {"drawWorld(dark)", "drawLighting(dark, sw, sh)", "drawPlayerOnTop(dark)", "hud.render(", "touch.render()", "drawOverlayScreens(sw, sh)"};
        int last = -1; boolean inOrder = true; for (String o : order) { int i = draw.indexOf(o); if (i < 0 || i < last) inOrder = false; last = i; }
        check(inOrder, "draw passes are called in the intended order");
        check(draw.indexOf("hud.render(") > draw.indexOf("drawPlayerOnTop(dark)"), "HUD is drawn AFTER the world so it is never covered");

        System.out.println("[5] begin/end are balanced in every draw method");
        for (String sig : new String[]{"private void drawWorld(", "private void drawPlayerOnTop(", "private void drawLighting(", "private void drawMenu(", "private void drawOverlayScreens("}) {
            String b = body(main, sig); int bg = count(b, "batch.begin()"), en = count(b, "batch.end()");
            check(bg == en, sig.replace("private void ", "") + " batch.begin x" + bg + " == batch.end x" + en);
        }
        String r2 = render; int bb = count(r2, "b.begin()"), be = count(r2, "b.end()");
        check(bb >= be && bb - be <= 1, "Hud.render: b.begin x" + bb + " vs b.end x" + be + " (tutorial pass may re-open once)");
        int sb = count(r2, "sr.begin("), se = count(r2, "sr.end()");
        check(sb == se, "Hud.render: sr.begin x" + sb + " == sr.end x" + se);

        System.out.println(fails == 0 ? "\nALL RENDER-ORDER TESTS PASSED (" + checks + " checks)" : "\nRENDER-ORDER TESTS FAILED: " + fails + " of " + checks);
    }
}
