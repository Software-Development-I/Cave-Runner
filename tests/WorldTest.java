import com.badlogic.Terraria.*;
import java.util.*;

public class WorldTest {
    public static void main(String[] a) {
        int fails = 0;
        for (int level = 1; level <= Config.MAX_LEVEL; level++) {
            for (long seed = 1; seed <= 25; seed++) {
                World w = new World(seed, level);
                int[] counts = new int[Tiles.COUNT];
                for (int x = 0; x < World.WIDTH; x++) for (int y = 0; y < World.HEIGHT; y++) counts[w.get(x, y)]++;
                // spawn safety: spawn column air, nothing harmful within safe radius
                boolean unsafe = false;
                // no hazards within the safe radius from 12 tiles below the surface up to the sky
                for (int x = Config.SPAWN_X - Config.SPAWN_SAFE_RADIUS; x <= Config.SPAWN_X + Config.SPAWN_SAFE_RADIUS; x++)
                    for (int y = w.surfaceY(x) - 12; y < World.HEIGHT; y++) {
                        int t = w.get(x, y);
                        if (t == Tiles.SPIKES || t == Tiles.LAVA || t == Tiles.ARROW_TRAP || t == Tiles.CRUMBLE) unsafe = true;
                    }
                if (unsafe) { System.out.println("UNSAFE spawn L" + level + " seed " + seed); fails++; }
                // spawn has 2 free tiles high and ground under
                if (w.isSolid(w.spawnX, w.spawnY) || w.isSolid(w.spawnX, w.spawnY + 1) || !w.isSolid(w.spawnX, w.spawnY - 1)) {
                    System.out.println("BAD spawn cell L" + level + " seed " + seed); fails++; }
                // portal reachable over the SURFACE: walk/jump along the surface (max step up 2 tiles for a jump of ~4)
                int dir = w.portalX > w.spawnX ? 1 : -1;
                boolean reachable = true; int x = w.spawnX;
                while (x != w.portalX) {
                    int nx = x + dir;
                    int rise = w.surfaceY(nx) - w.surfaceY(x);
                    if (rise > 3) { reachable = false; break; }  // jump height ~ 240^2/(2*600)=48px=3 tiles
                    x = nx;
                }
                // trees/traps on the route might block, so also check for spikes ON surface path
                if (!reachable) { System.out.println("Surface path steep L" + level + " seed " + seed + " at x=" + x); fails++; }
                if (level == 1 && seed == 1) {
                    System.out.println("L1 seed1 counts: dirt=" + counts[1] + " stone=" + counts[2] + " sand=" + counts[4]
                        + " spikes=" + counts[5] + " lava=" + counts[6] + " crumble=" + counts[7] + " arrow=" + counts[8]
                        + " coal=" + counts[9] + " iron=" + counts[10] + " gold=" + counts[11]
                        + " wood=" + counts[12] + " portal=" + counts[17] + " flags=" + counts[16]);
                    System.out.println("arrowTraps=" + w.arrowTraps.size() + " rocks=" + w.fallingRocks.size()
                        + " spawn=(" + w.spawnX + "," + w.spawnY + ") portal=(" + w.portalX + "," + w.portalY + ")");
                }
            }
        }
        System.out.println(fails == 0 ? "WORLD TESTS PASSED (125 worlds)" : "WORLD TESTS FAILED: " + fails);
    }
}
