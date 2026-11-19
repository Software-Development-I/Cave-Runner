package com.badlogic.Terraria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/**
 * On-screen controls: movement pad, JUMP / HIT / MINE / PUT buttons, PAUSE and BAG.
 * The stage runs in "base units" (1 unit = 1 px at 720p) and the viewport scales them up with screen height,
 * so buttons stay comfortably large on 1080p / 1440p phones. Positions come from {@link TouchLayout}.
 * On desktop the controls are disabled (keyboard + mouse); F2 toggles them for testing.
 */
public class TouchHUD implements Disposable {
    public final Stage stage;
    public final Touchpad touchpad;

    // one-shot flags: set on touch-down, consumed by Main
    public boolean jumpPressed, attackPressed, pauseToggled, inventoryToggled;
    // held flags
    public boolean mineHeld, placeHeld, jumpHeld;

    /** Whether the game state wants the controls shown (playing, bag closed...). */
    public boolean visible = true;
    /** Platform gate: false on desktop so the mouse is not covered by a virtual joystick. */
    public boolean enabled = true;

    private final ScreenViewport viewport;
    private final TextButton jumpBtn, attackBtn, mineBtn, placeBtn, pauseBtn, invBtn;
    private final BitmapFont font;
    private final Texture[] textures = new Texture[12];
    private int texCount = 0;
    private float scale = 1f;
    private final Vector2 tmp = new Vector2();

    public TouchHUD() {
        viewport = new ScreenViewport();
        stage = new Stage(viewport);
        font = new BitmapFont();
        font.getData().setScale(1.5f);

        Touchpad.TouchpadStyle ts = new Touchpad.TouchpadStyle();
        ts.background = circle(180, new Color(0.15f, 0.15f, 0.15f, 0.35f));
        ts.knob = circle(70, new Color(0.9f, 0.9f, 0.9f, 0.55f));
        touchpad = new Touchpad(12, ts);
        touchpad.setSize(TouchLayout.PAD_SIZE, TouchLayout.PAD_SIZE);
        stage.addActor(touchpad);

        TextButton.TextButtonStyle round = new TextButton.TextButtonStyle();
        round.font = font;
        round.up = circle((int) TouchLayout.BTN, new Color(0.15f, 0.15f, 0.15f, 0.5f));
        round.down = circle((int) TouchLayout.BTN, new Color(0.7f, 0.7f, 0.7f, 0.75f));

        TextButton.TextButtonStyle small = new TextButton.TextButtonStyle();
        small.font = font;
        small.up = square((int) TouchLayout.SMALL_W, (int) TouchLayout.SMALL_H, new Color(0.15f, 0.15f, 0.15f, 0.55f));
        small.down = square((int) TouchLayout.SMALL_W, (int) TouchLayout.SMALL_H, new Color(0.7f, 0.7f, 0.7f, 0.8f));

        jumpBtn = new TextButton("JUMP", round);
        attackBtn = new TextButton("HIT", round);
        mineBtn = new TextButton("MINE", round);
        placeBtn = new TextButton("PUT", round);
        pauseBtn = new TextButton("||", small);
        invBtn = new TextButton("BAG", small);
        for (TextButton b : new TextButton[]{jumpBtn, attackBtn, mineBtn, placeBtn}) b.setSize(TouchLayout.BTN, TouchLayout.BTN);
        pauseBtn.setSize(TouchLayout.SMALL_W, TouchLayout.SMALL_H);
        invBtn.setSize(TouchLayout.SMALL_W, TouchLayout.SMALL_H);

        holdListener(jumpBtn, 0); holdListener(mineBtn, 1); holdListener(placeBtn, 2);
        attackBtn.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { attackPressed = true; return super.touchDown(e, x, y, p, b); }
        });
        pauseBtn.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { pauseToggled = true; } });
        invBtn.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { inventoryToggled = true; } });

        stage.addActor(jumpBtn); stage.addActor(attackBtn); stage.addActor(mineBtn);
        stage.addActor(placeBtn); stage.addActor(pauseBtn); stage.addActor(invBtn);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void holdListener(TextButton b, final int which) {
        b.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int btn) {
                if (which == 0) { jumpPressed = true; jumpHeld = true; }
                else if (which == 1) mineHeld = true; else placeHeld = true;
                return super.touchDown(e, x, y, p, btn);
            }
            @Override public void touchUp(InputEvent e, float x, float y, int p, int btn) {
                if (which == 0) jumpHeld = false; else if (which == 1) mineHeld = false; else placeHeld = false;
                super.touchUp(e, x, y, p, btn);
            }
        });
    }

    /** Places every actor from the pure {@link TouchLayout} rects (screen px) converted to stage units. */
    private void layout(int w, int h) {
        float[][] r = TouchLayout.compute(w, h);
        Actor[] actors = {touchpad, jumpBtn, attackBtn, mineBtn, placeBtn, pauseBtn, invBtn};
        for (int i = 0; i < actors.length; i++) actors[i].setPosition(r[i][0] / scale, r[i][1] / scale);
    }

    public float axisX() { return touchpad.getKnobPercentX(); }
    public float axisY() { return touchpad.getKnobPercentY(); }

    /** True if a screen-space point (origin top-left) lies over any visible HUD control. */
    public boolean hitsHud(float screenX, float screenY) {
        if (!visible || !enabled) return false;
        stage.screenToStageCoordinates(tmp.set(screenX, screenY));
        return stage.hit(tmp.x, tmp.y, true) != null;
    }

    public void consumeFlags() { jumpPressed = attackPressed = pauseToggled = inventoryToggled = false; }

    public void setVisible(boolean v) {
        visible = v;
        boolean show = v && enabled;
        for (Actor a : stage.getActors()) a.setVisible(show);
        if (!show) { mineHeld = placeHeld = jumpHeld = false; }
    }

    /** While the bag is open only the BAG button stays, so the player can close it again on a phone. */
    public void showOnlyBag() {
        visible = true;
        for (Actor a : stage.getActors()) a.setVisible(enabled && a == invBtn);
        mineHeld = placeHeld = jumpHeld = false;
    }

    public void act(float dt) { stage.act(dt); }
    public void render() { if (visible && enabled) stage.draw(); }

    public void resize(int w, int h) {
        scale = TouchLayout.scale(h);
        viewport.setUnitsPerPixel(1f / scale);
        viewport.update(w, h, true);
        layout(w, h);
    }

    private Drawable circle(int size, Color c) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(c); pm.fillCircle(size / 2, size / 2, size / 2 - 1);
        return drawable(pm);
    }

    private Drawable square(int w, int h, Color c) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(c); pm.fill();
        return drawable(pm);
    }

    private Drawable drawable(Pixmap pm) {
        Texture t = new Texture(pm);
        pm.dispose();
        if (texCount < textures.length) textures[texCount++] = t;
        return new TextureRegionDrawable(t);
    }

    @Override public void dispose() {
        stage.dispose(); font.dispose();
        for (int i = 0; i < texCount; i++) if (textures[i] != null) textures[i].dispose();
    }
}
