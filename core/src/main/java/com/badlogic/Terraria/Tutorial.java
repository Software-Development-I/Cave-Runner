package com.badlogic.Terraria;

/**
 * A short, skippable first-run tutorial that advances only after the player performs an action.
 * It contains no GL or platform dependencies.
 */
public class Tutorial {
    public enum Step { MOVE, JUMP, MINE, PLACE, ATTACK, BAG, DONE }

    public Step step = Step.MOVE;
    public boolean enabled = true;
    public boolean justAdvanced;

    public int minedCount, placedCount, attackCount, jumpCount;
    public boolean bagOpened;

    private float movedDistance = 0f;
    private float lastX = Float.NaN;
    private int startBlocks = 0, startPlaced = 0, startAttacks = 0, startJumps = 0;

    public boolean active() { return enabled && step != Step.DONE; }
    public void skip() { step = Step.DONE; enabled = false; }

    public String prompt(boolean touch) {
        switch (step) {
            case MOVE:   return touch ? "Drag the left pad to walk" : "Press A / D to walk";
            case JUMP:   return touch ? "Tap JUMP" : "Press W / Space to jump";
            case MINE:   return touch ? "Hold MINE next to a block to dig it" : "Hold left mouse on a block to dig it";
            case PLACE:  return touch ? "Tap PUT to place a block" : "Right-click to place a block";
            case ATTACK: return touch ? "Tap HIT to swing your sword" : "Press J to swing your sword";
            case BAG:    return touch ? "Tap BAG to craft tools" : "Press Tab to open the bag and craft";
            default:     return "";
        }
    }

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
        startBlocks = minedCount;
        startPlaced = placedCount;
        startAttacks = attackCount;
        startJumps = jumpCount;
    }
}
