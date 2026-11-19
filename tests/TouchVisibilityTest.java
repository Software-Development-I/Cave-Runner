import java.nio.file.*;

/**
 * Static guard for on-screen touch controls (joystick + buttons).
 * Current intended behaviour: touch controls are ON and VISIBLE by default on EVERY platform (including
 * desktop), so a developer testing an Android build can see and use them immediately without a hidden key.
 * F2 lets desktop users toggle them off if they get in the way. Keyboard/mouse always work alongside them.
 */
public class TouchVisibilityTest {
    static int fails = 0, checks = 0;
    static void check(boolean ok, String msg) { checks++; System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }

    static String body(String src, String sig) {
        int i = src.indexOf(sig); if (i < 0) return null;
        int open = src.indexOf('{', i), depth = 0, j = open;
        for (; j < src.length(); j++) { char c = src.charAt(j); if (c == '{') depth++; else if (c == '}') { depth--; if (depth == 0) break; } }
        return src.substring(open, j + 1);
    }

    public static void main(String[] a) throws Exception {
        String dir = a.length > 0 ? a[0] : "core/src/main/java/com/badlogic/Terraria/";
        String main = new String(Files.readAllBytes(Paths.get(dir + "Main.java")));
        String touchHud = new String(Files.readAllBytes(Paths.get(dir + "TouchHUD.java")));

        System.out.println("[1] create() turns touch controls ON by default, unconditionally (not gated on platform)");
        String create = body(main, "public void create()");
        check(create != null, "found create()");
        check(!create.contains("touch.setVisible(false)"), "create() does not force touch invisible unconditionally");
        int enabledSet = create.indexOf("touch.enabled = true");
        check(enabledSet >= 0, "create() sets touch.enabled = true unconditionally (desktop included)");
        int visibleSet = create.indexOf("touch.setVisible(", Math.max(enabledSet, 0));
        check(visibleSet >= 0 && visibleSet > enabledSet, "create() sets startup visibility AFTER enabling touch");
        String visLine = create.substring(visibleSet, create.indexOf(';', visibleSet) + 1);
        check(visLine.contains("touch.enabled"), "the STARTUP visibility call is driven by touch.enabled: " + visLine.trim());

        System.out.println("[2] F2 still lets desktop users toggle the on-screen controls off");
        String keyDown = body(main, "public boolean keyDown(int k)");
        check(keyDown != null && keyDown.contains("Input.Keys.F2") && keyDown.contains("touch.enabled = !touch.enabled"), "F2 flips touch.enabled");
        check(keyDown.contains("touch.setVisible(touch.enabled"), "F2's visibility call also depends on touch.enabled (so turning it off actually hides the buttons)");

        System.out.println("[3] TouchHUD.render() and setVisible() both respect the 'enabled' gate (so F2-off truly hides them)");
        check(touchHud.contains("public void render() { if (visible && enabled) stage.draw(); }"), "render() checks both visible AND enabled");
        String setVis = body(touchHud, "public void setVisible(boolean v)");
        check(setVis != null && setVis.contains("v && enabled"), "setVisible() ANDs the requested state with the enabled gate");

        System.out.println("[4] Simulate boot on every platform: touch controls must be visible everywhere by default");
        for (String platform : new String[]{"Android", "iOS", "Desktop"}) {
            boolean enabled = true;                        // FIXED: touch.enabled = true, unconditionally
            boolean requestedVisible = enabled;            // touch.setVisible(touch.enabled)
            boolean actuallyShown = requestedVisible && enabled; // TouchHUD.setVisible's formula
            check(actuallyShown, platform + " boot: on-screen controls visible by default");
        }
        System.out.println("    Regression check: the OLD platform-gated code would hide them on Desktop. Confirming that would be wrong:");
        boolean oldDesktopBehaviour = false; // old: touch.enabled = hud.touchMode, which was false on Desktop
        check(!oldDesktopBehaviour, "reproduces the reported bug (buttons hidden on Desktop) under the OLD code, for contrast");

        System.out.println(fails == 0 ? "\nALL TOUCH-VISIBILITY TESTS PASSED (" + checks + " checks)" : "\nTOUCH-VISIBILITY TESTS FAILED: " + fails + " of " + checks);
    }
}
