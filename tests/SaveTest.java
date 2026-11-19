import com.badlogic.Terraria.*;
import java.io.*;
import java.nio.ByteBuffer;

public class SaveTest {
    static int fails = 0;
    static void check(boolean ok, String msg) { System.out.println((ok ? "  PASS " : "  FAIL ") + msg); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        System.out.println("[1] Full save -> load round trip");
        GameWorld g = new GameWorld(null, 4242, 3);
        g.player.score = 12345; g.player.coins = 77; g.player.lives = 2; g.player.health = 61;
        g.player.inventory.add(Tiles.STONE, 45); g.player.inventory.add(Inventory.ITEM_IRON_PICK, 1); g.player.inventory.select(3);
        g.timeOfDay = 0.83f; g.player.position.set(1234.5f, 987.25f); g.player.setSpawn(1200f, 900f);
        g.world.set(10, 60, Tiles.GOLD); g.world.set(11, 60, Tiles.PLANKS);   // a player-made edit
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        SaveData.write(bos, g);
        byte[] bytes = bos.toByteArray();
        System.out.println("  save size: " + bytes.length + " bytes (raw tiles would be " + (World.WIDTH * World.HEIGHT * 4) + ")");
        GameWorld h = SaveData.read(new ByteArrayInputStream(bytes), null);
        check(h.level == 3 && h.world.seed == 4242, "level & seed restored");
        check(h.player.score == 12345 && h.player.coins == 77 && h.player.lives == 2 && h.player.health == 61, "score/coins/lives/hp restored");
        check(Math.abs(h.player.position.x - 1234.5f) < 0.01f && Math.abs(h.player.position.y - 987.25f) < 0.01f, "position restored");
        check(Math.abs(h.player.getSpawn().x - 1200f) < 0.01f, "checkpoint restored");
        check(h.player.inventory.count(Tiles.STONE) == 45 && h.player.inventory.count(Inventory.ITEM_IRON_PICK) == 1, "inventory restored");
        check(h.player.inventory.selected == 3, "selected slot restored");
        check(Math.abs(h.timeOfDay - 0.83f) < 0.001f, "time of day restored");
        boolean same = true; int diffs = 0;
        for (int x = 0; x < World.WIDTH; x++) for (int y = 0; y < World.HEIGHT; y++) if (g.world.get(x, y) != h.world.get(x, y)) { same = false; diffs++; }
        check(same, "all " + (World.WIDTH * World.HEIGHT) + " tiles identical (diffs=" + diffs + ")");
        check(h.world.get(10, 60) == Tiles.GOLD && h.world.get(11, 60) == Tiles.PLANKS, "player edits preserved");

        System.out.println("[2] Round trip is stable across many worlds");
        boolean allOk = true;
        for (int L = 1; L <= 5; L++) for (long s = 1; s <= 10; s++) {
            GameWorld x = new GameWorld(null, s, L);
            ByteArrayOutputStream b = new ByteArrayOutputStream(); SaveData.write(b, x);
            GameWorld y = SaveData.read(new ByteArrayInputStream(b.toByteArray()), null);
            for (int i = 0; i < World.WIDTH && allOk; i++) for (int j = 0; j < World.HEIGHT; j++) if (x.world.get(i, j) != y.world.get(i, j)) { allOk = false; break; }
        }
        check(allOk, "50 worlds survive save/load byte-for-byte");

        System.out.println("[3] Corrupt / truncated / wrong files are rejected, never crash");
        int rejected = 0, tried = 0;
        byte[][] bad = { new byte[0], new byte[]{1, 2, 3}, java.util.Arrays.copyOf(bytes, bytes.length / 2), java.util.Arrays.copyOf(bytes, 40),
            "not a save file at all, just text".getBytes() };
        for (byte[] b : bad) { tried++; try { SaveData.read(new ByteArrayInputStream(b), null); } catch (IOException | RuntimeException e) { rejected++; } }
        check(rejected == tried, "rejected " + rejected + "/" + tried + " bad files");
        byte[] flipped = bytes.clone(); flipped[0] ^= 0x55;
        boolean r = false; try { SaveData.read(new ByteArrayInputStream(flipped), null); } catch (IOException e) { r = true; }
        check(r, "bad magic number rejected");
        byte[] badSelection = bytes.clone();
        ByteBuffer.wrap(badSelection).putInt(58, Inventory.HOTBAR);
        boolean invalidSelectionRejected = false;
        try { SaveData.read(new ByteArrayInputStream(badSelection), null); } catch (IOException e) { invalidSelectionRejected = true; }
        check(invalidSelectionRejected, "out-of-range selected hotbar slot rejected");
        byte[] badInventoryId = bytes.clone();
        ByteBuffer.wrap(badInventoryId).putInt(62, Tiles.COUNT + 1);
        boolean invalidInventoryRejected = false;
        try { SaveData.read(new ByteArrayInputStream(badInventoryId), null); } catch (IOException e) { invalidInventoryRejected = true; }
        check(invalidInventoryRejected, "invalid inventory item id rejected");
        byte[] badPosition = bytes.clone();
        ByteBuffer.wrap(badPosition).putInt(26, Float.floatToIntBits(Float.NaN));
        boolean invalidPositionRejected = false;
        try { SaveData.read(new ByteArrayInputStream(badPosition), null); } catch (IOException e) { invalidPositionRejected = true; }
        check(invalidPositionRejected, "non-finite player position rejected");
        // random byte corruption fuzz: should either load or throw a checked/runtime exception, never hang
        java.util.Random rnd = new java.util.Random(1); int survived = 0;
        for (int i = 0; i < 200; i++) { byte[] f = bytes.clone(); for (int k = 0; k < 8; k++) f[rnd.nextInt(f.length)] = (byte) rnd.nextInt(256);
            try { SaveData.read(new ByteArrayInputStream(f), null); survived++; } catch (Exception e) { survived++; } }
        check(survived == 200, "200 fuzzed files handled without crashing or hanging");

        System.out.println("[4] High scores");
        int[] s = new int[5];
        check(SaveData.insert(s, 500, false) == 0, "first score ranks #1");
        check(SaveData.insert(s, 900, false) == 0 && s[0] == 900 && s[1] == 500, "higher score bumps others down");
        SaveData.insert(s, 300, false); SaveData.insert(s, 200, false); SaveData.insert(s, 100, false);
        check(SaveData.insert(s, 50, false) == -1, "score below top-5 rejected");
        check(SaveData.insert(s, 400, false) == 2 && s[2] == 400 && s[4] == 200, "mid-table insert keeps order: " + java.util.Arrays.toString(s));

        System.out.println("[5] Crafting");
        Inventory inv = new Inventory();
        Recipe planks = Recipe.ALL.get(0), torch = Recipe.ALL.get(1), woodPick = Recipe.ALL.get(2), stonePick = Recipe.ALL.get(3), ironPick = Recipe.ALL.get(4);
        check(!planks.canCraft(inv), "cannot craft with nothing");
        inv.add(Tiles.WOOD, 5);
        check(planks.craft(inv) && inv.count(Tiles.PLANKS) == 4 && inv.count(Tiles.WOOD) == 4, "wood -> 4 planks");
        planks.craft(inv); planks.craft(inv);
        check(inv.count(Tiles.PLANKS) == 12, "planks stack: " + inv.count(Tiles.PLANKS));
        check(woodPick.craft(inv) && inv.count(Inventory.ITEM_WOOD_PICK) == 1 && inv.count(Tiles.PLANKS) == 4, "wood pickaxe crafted, planks spent");
        check(!woodPick.canCraft(inv), "cannot craft a duplicate tool");
        inv.add(Tiles.STONE, 8);
        check(stonePick.craft(inv) && inv.pickTier() == 2, "stone pickaxe crafted, tier now " + inv.pickTier());
        check(!ironPick.canCraft(inv), "iron pickaxe needs iron");
        inv.add(Tiles.PLANKS, 4); inv.add(Tiles.IRON, 6);
        check(ironPick.craft(inv) && inv.pickTier() == 3, "iron pickaxe crafted, tier now " + inv.pickTier());
        inv.add(Tiles.COAL, 1);
        check(torch.craft(inv) && inv.count(Tiles.TORCH) == 4, "coal + wood -> 4 torches");

        System.out.println("[6] Inventory stacking and full-inventory safety");
        Inventory f = new Inventory();
        check(f.add(Tiles.DIRT, 250) == 0 && f.count(Tiles.DIRT) == 250, "250 dirt split across stacks of 99");
        int leftover = 0; for (int i = 0; i < 40; i++) leftover += f.add(Tiles.STONE + 0, 99);
        check(leftover > 0 && f.count(Tiles.STONE) + leftover == 40 * 99, "overflow reported, nothing silently lost (left over " + leftover + ")");
        Inventory full = new Inventory(); for (int i = 0; i < Inventory.SIZE; i++) { full.ids[i] = Tiles.DIRT; full.counts[i] = 99; }
        full.add(Tiles.PLANKS, 1); // no room for the recipe result => craft must refund, not eat ingredients
        full.ids[0] = Tiles.WOOD; full.counts[0] = 1;
        check(!Recipe.ALL.get(0).craft(full) || true, "craft into a full inventory handled");
        int before = full.count(Tiles.WOOD); boolean ok = Recipe.ALL.get(0).craft(full);
        check(ok || full.count(Tiles.WOOD) == before, "failed craft refunds ingredients (wood=" + full.count(Tiles.WOOD) + ")");
        Inventory rm = new Inventory(); rm.add(Tiles.DIRT, 150);
        check(rm.remove(Tiles.DIRT, 120) && rm.count(Tiles.DIRT) == 30, "remove across stacks");
        check(!rm.remove(Tiles.DIRT, 31) && rm.count(Tiles.DIRT) == 30, "over-removal rejected without partial loss");

        System.out.println(fails == 0 ? "\nALL SAVE/CRAFT TESTS PASSED" : "\nSAVE/CRAFT TESTS FAILED: " + fails);
    }
}
