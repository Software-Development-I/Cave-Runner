package com.badlogic.Terraria;

/**
 * A short, skippable first-run tutorial. It watches what the player actually DOES (via counters that GameWorld
 * increments) and advances when they do it, so it never blocks and never lies about controls.
 * Pure logic, no GL, so it is unit-tested headlessly.
 */
public class Tutorial {
    public enum Step { MOVE, JUMP, MINE, PLACE, ATTACK, BAG, DONE }

    public Step step = Step.MOVE;
    public boolean enabled = true;
    /** Set for one frame when a step is completed, so Main can play a sound. */
    public boolean justAdvanced;

    /** Counters the tutorial reads. GameWorld/Main increment these. */
    public int minedCount, placedCount, attackCount, jumpCount;
    public boolean bagOpened;

    private float movedDistance = 0f;
    private float lastX = Float.NaN;
    // Baselines are snapshotted the moment a step BEGINS, so an action in the very next frame still counts.
    private int startBlocks = 0, startPlaced = 0, startAttacks = 0, startJumps = 0;

    public boolean active() { return enabled && step != Step.DONE; }

    public void skip() { step = Step.DONE; enabled = false; }

    /** @param touch true on a phone, so the hint names the on-screen buttons instead of keys. */
    public String prompt(boolean touch) {
        switch (step) {
            case MOVE:   return touch ? "Drag the left pad to walk" : "Press A / D to walk";
            case JUMP:   return touch ? "Tap JUMP. In mid-air, touch a wall and tap JUMP again to wall-jump!" : "Press W / Space to jump. In mid-air, touch a wall and jump again to wall-jump!";
            case MINE:   return touch ? "Hold MINE next to a block to dig it" : "Hold left mouse on a block (or press F) to dig it";
            case PLACE:  return touch ? "Tap PUT to place a block" : "Right-click (or press E) to place a block";
            case ATTACK: return touch ? "Tap HIT to swing your sword" : "Press J to swing your sword";
            case BAG:    return touch ? "Tap BAG to craft tools" : "Press Tab to open the bag and craft a pickaxe";
            default:     return "";
        }
    }

    /** 1-based step number for the "n / 6" counter. */
    public int stepNumber() { return Math.min(step.ordinal() + 1, 6); }
    public int stepTotal() { return 6; }

    public void update(float dt, Player p, Player.Controls c) {
        justAdvanced = false;
        if (!active() || p.dead) return;
        if (Float.isNaN(lastX)) lastX = p.position.x;
        movedDistance += Math.abs(p.position.x - lastX);
        lastX = p.position.x;

        switch (step) {
            case MOVE:   if (movedDistance > 48f) advance(); break;
            case JUMP:   if (jumpCount > startJumps) advance(); break;
            case MINE:   if (minedCount > startBlocks) advance(); break;
            case PLACE:  if (placedCount > startPlaced) advance(); break;
            case ATTACK: if (attackCount > startAttacks) advance(); break;
            case BAG:    if (bagOpened) advance(); break;
            default: break;
        }
    }

    private void advance() {
        step = Step.values()[step.ordinal() + 1];
        justAdvanced = true;
        startBlocks = minedCount; startPlaced = placedCount; startAttacks = attackCount; startJumps = jumpCount;
    }

    // ---- persistence: only "was the tutorial finished/skipped" matters ----
    public static boolean alreadyDone() {
        try { return com.badlogic.gdx.Gdx.app.getPreferences("cave_runner").getBoolean("tutorial_done", false); } catch (Exception e) { return false; }
    }
    public static void markDone() {
        try { com.badlogic.gdx.Preferences p = com.badlogic.gdx.Gdx.app.getPreferences("cave_runner"); p.putBoolean("tutorial_done", true); p.flush(); } catch (Exception ignored) { }
    }
}
