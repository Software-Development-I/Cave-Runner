package com.badlogic.Terraria;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Screen state machine + input + rendering. All game rules live in {@link GameWorld}. */
public class Main implements ApplicationListener {
    public enum State { MENU, PLAYING, PAUSED, GAME_OVER, LEVEL_DONE, WIN }

    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private OrthographicCamera camera;
    private TouchHUD touch;
    private Hud hud;
    private Sfx sfx;
    private GameWorld game;
    private State state = State.MENU;
    private Texture lightTex;          // soft radial gradient used for glows
    private TextureRegion lightRegion;

    private final float[] skyTmp = new float[3];
    private final Player.Controls controls = new Player.Controls();
    private final Vector3 mouse = new Vector3();
    private float stateTime;
    private boolean scoreSubmitted;
    private int lastRank = -1;
    private float mineHold;
    private int menuChoice = 0;
    private boolean hasSave;

    // one-shot keyboard/mouse intent flags gathered from the input processor
    private boolean kbJump, kbAttack, kbPause, kbInv, kbEnter, kbUp, kbDown, kbClickLeft, kbClickRight;
    private int kbSlot = -1, kbScroll = 0;
    private float lastClickX, lastClickY;
    private boolean clickThisFrame;

    private static final int VIEW_W_TILES = 32, VIEW_H_TILES = 18;

    @Override
    public void create() {
        assets = new Assets();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, VIEW_W_TILES * World.TILE_SIZE, VIEW_H_TILES * World.TILE_SIZE);
        touch = new TouchHUD();
        hud = new Hud(assets);
        sfx = new Sfx();
        hasSave = SaveData.exists();
        buildLightTexture();

        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(touch.stage);
        mux.addProcessor(new InputAdapter() {
            @Override public boolean keyDown(int k) {
                switch (k) {
                    case Input.Keys.SPACE: case Input.Keys.W: case Input.Keys.UP: kbJump = true; kbUp = true; break;
                    case Input.Keys.J: case Input.Keys.X: kbAttack = true; break;
                    case Input.Keys.ESCAPE: case Input.Keys.P: kbPause = true; break;
                    case Input.Keys.TAB: case Input.Keys.I: kbInv = true; break;
                    case Input.Keys.ENTER: kbEnter = true; break;
                    case Input.Keys.S: case Input.Keys.DOWN: kbDown = true; break;
                    case Input.Keys.F2: touch.enabled = !touch.enabled; hud.setTouchControls(touch.enabled); touch.setVisible(touch.enabled && state == State.PLAYING && !hud.inventoryOpen); break;
                    case Input.Keys.M: sfx.setMusic(!sfx.musicOn); break;
                    case Input.Keys.N: sfx.setSfx(!sfx.sfxOn); break;
                    default:
                        if (k >= Input.Keys.NUM_1 && k <= Input.Keys.NUM_9) kbSlot = k - Input.Keys.NUM_1;
                }
                return false;
            }
            @Override public boolean scrolled(float ax, float ay) { kbScroll += (int) Math.signum(ay); return false; }
            @Override public boolean touchDown(int x, int y, int p, int b) {
                clickThisFrame = true; lastClickX = x; lastClickY = y;
                if (b == Input.Buttons.RIGHT) kbClickRight = true; else kbClickLeft = true;
                return false;
            }
        });
        Gdx.input.setInputProcessor(mux);
        hud.touchMode = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        hud.touchMode = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        // Touch controls are ON by default everywhere, including desktop, so you can see and test the on-screen
        // joystick/buttons you'll ship on a phone without needing a hidden key. Keyboard and mouse still work
        // alongside them at all times. F2 toggles the on-screen controls off if they get in the way on desktop.
        touch.enabled = true;
        hud.setTouchControls(touch.enabled);           // the hotbar squeezes between pad and buttons only when they are shown
        touch.setVisible(touch.enabled);
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }


    /** 64x64 white radial gradient with smooth (quadratic) falloff, tinted at draw time. */
    private void buildLightTexture() {
        int n = 64;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float dx = (x + 0.5f - n / 2f) / (n / 2f), dy = (y + 0.5f - n / 2f) / (n / 2f);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float a = MathUtils.clamp(1f - d, 0f, 1f);
            a = a * a;                                   // soft falloff, no visible rings
            pm.setColor(1f, 1f, 1f, a);
            pm.drawPixel(x, y);
        }
        lightTex = new Texture(pm);
        lightTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        lightRegion = new TextureRegion(lightTex);
    }

    // ------------------------------------------------------------------ state changes

    private void startNewGame() {
        SaveData.delete();
        game = new GameWorld(assets.get("player"), System.nanoTime() & 0xFFFFFF, 1);
        if (Tutorial.alreadyDone()) game.tutorial.skip();
        scoreSubmitted = false; lastRank = -1;
        setState(State.PLAYING);
    }

    private void continueGame() {
        GameWorld g = SaveData.load(assets.get("player"));
        if (g == null) { startNewGame(); return; }
        game = g; game.tutorial.skip(); scoreSubmitted = false; lastRank = -1;
        setState(State.PLAYING);
    }

    private void setState(State s) {
        state = s; stateTime = 0f; hud.inventoryOpen = false;
        touch.setVisible(s == State.PLAYING);
        if (s == State.PAUSED) sfx.pauseMusic(); else if (s == State.PLAYING) sfx.resumeMusic();
    }

    // ------------------------------------------------------------------ main loop

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1 / 20f);
        stateTime += dt;
        touch.act(dt);

        switch (state) {
            case MENU: updateMenu(); break;
            case PLAYING: updatePlaying(dt); break;
            case PAUSED: updatePaused(); break;
            case GAME_OVER: updateGameOver(); break;
            case LEVEL_DONE: updateLevelDone(); break;
            case WIN: updateWin(); break;
        }
        draw();
        clearOneShots();
    }

    private void clearOneShots() {
        kbJump = kbAttack = kbPause = kbInv = kbEnter = kbUp = kbDown = kbClickLeft = kbClickRight = false;
        kbSlot = -1; kbScroll = 0; clickThisFrame = false;
        touch.consumeFlags();
    }

    // ------------------------------------------------------------------ MENU

    private void updateMenu() {
        int items = hasSave ? 2 : 1;
        if (kbUp) menuChoice = (menuChoice + items - 1) % items;
        if (kbDown) menuChoice = (menuChoice + 1) % items;
        boolean activate = kbEnter;
        int sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
        if (clickThisFrame) {
            float cy = sh - lastClickY;
            for (int i = 0; i < items; i++) if (Math.abs(cy - menuButtonY(i, sh)) < 34 * hud.uiScale) { menuChoice = i; activate = true; }
        }
        if (activate) {
            sfx.click();
            boolean cont = hasSave && menuChoice == 0;
            if (cont) continueGame(); else startNewGame();
        }
    }

    private float menuButtonY(int i, int sh) { return sh * 0.42f - i * 70 * hud.uiScale; }

    // ------------------------------------------------------------------ PLAYING

    private void updatePlaying(float dt) {
        if (kbPause || touch.pauseToggled) { setState(State.PAUSED); return; }
        if (game.tutorial.active()) {
            boolean tapSkip = clickThisFrame && kbClickLeft && hud.tutorialSkipHit(lastClickX, Gdx.graphics.getHeight() - lastClickY, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            if (Gdx.input.isKeyJustPressed(Input.Keys.K) || tapSkip) { game.tutorial.skip(); Tutorial.markDone(); game.toast("Tutorial skipped"); }
        }
        if (kbInv || touch.inventoryToggled) {
            if (!hud.inventoryOpen) game.tutorial.bagOpened = true;
            hud.inventoryOpen = !hud.inventoryOpen; hud.craftScroll = 0; sfx.click();
            touch.setVisible(!hud.inventoryOpen);
            if (hud.inventoryOpen) touch.showOnlyBag();
        }
        if (kbSlot >= 0) game.player.inventory.select(kbSlot);
        if (kbScroll != 0) {
            if (hud.inventoryOpen) hud.scrollCrafting(kbScroll, Gdx.graphics.getHeight());
            else game.player.inventory.select(game.player.inventory.selected + kbScroll);
        }

        int sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
        handlePointerUi(sw, sh);

        buildControls();
        if (hud.inventoryOpen) { controls.moveX = 0; controls.attackPressed = false; }

        game.update(dt, controls);
        handleWorldPointer(dt, sw, sh);
        playEvents();
        updateCamera();

        if (game.shouldAutosave()) SaveData.save(game);
        if (game.gameOver) { submitScoreOnce(); setState(State.GAME_OVER); }
        else if (game.levelComplete) { SaveData.save(game); setState(game.level >= Config.MAX_LEVEL ? State.WIN : State.LEVEL_DONE); }
    }

    private void buildControls() {
        controls.reset();
        float kx = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) kx -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) kx += 1;
        float tx = Math.abs(touch.axisX()) > 0.25f ? Math.signum(touch.axisX()) : 0;
        controls.moveX = kx != 0 ? kx : tx;
        controls.jumpPressed = kbJump || touch.jumpPressed;
        controls.jumpHeld = touch.jumpHeld || Gdx.input.isKeyPressed(Input.Keys.SPACE);
        controls.attackPressed = kbAttack || touch.attackPressed;
    }

    /** Clicks on hotbar / inventory / crafting are UI, never mining. */
    private void handlePointerUi(int sw, int sh) {
        if (!clickThisFrame || !kbClickLeft) return;
        float x = lastClickX, y = sh - lastClickY;
        int hb = hud.hotbarSlotAt(x, y, sw);
        if (hb >= 0) { game.player.inventory.select(hb); sfx.click(); return; }
        if (hud.inventoryOpen) {
            int slot = hud.inventorySlotAt(x, y, sw, sh);
            if (slot >= 0 && slot < Inventory.HOTBAR) { game.player.inventory.select(slot); sfx.click(); }
            else if (slot >= Inventory.HOTBAR) swapToHotbar(slot);
            int r = hud.recipeAt(x, y, sw, sh);
            if (r >= 0) {
                if (Recipe.ALL.get(r).craft(game.player.inventory)) { sfx.craft(); game.toast("Crafted " + Inventory.name(Recipe.ALL.get(r).resultId)); }
                else game.toast("Missing materials");
            }
        }
    }

    private void swapToHotbar(int slot) {
        Inventory inv = game.player.inventory; int h = inv.selected;
        int id = inv.ids[slot], c = inv.counts[slot];
        inv.ids[slot] = inv.ids[h]; inv.counts[slot] = inv.counts[h];
        inv.ids[h] = id; inv.counts[h] = c; sfx.click();
    }

    /** Mouse/touch on the world: hold left = mine, right click (or PUT button) = place. Ignores HUD hits. */
    private void handleWorldPointer(float dt, int sw, int sh) {
        if (hud.inventoryOpen || game.player.dead) { game.resetMining(); return; }
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        boolean overUi = touch.hitsHud(mx, my) || hud.hotbarSlotAt(mx, sh - my, sw) >= 0;

        mouse.set(mx, my, 0); camera.unproject(mouse);
        int tx = MathUtils.floor(mouse.x / World.TILE_SIZE), ty = MathUtils.floor(mouse.y / World.TILE_SIZE);
        lastTargetX = tx; lastTargetY = ty;

        boolean mouseMine = Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !overUi && Gdx.input.isTouched(0) && !touch.hitsHud(mx, my);
        boolean keyMine = Gdx.input.isKeyPressed(Input.Keys.F);
        boolean placeNow = (kbClickRight && !overUi) || Gdx.input.isKeyJustPressed(Input.Keys.E);

        // On-screen MINE/PUT buttons act on the tile in front of the player
        int fx = MathUtils.floor((game.player.centerX() + (game.player.facingRight ? 1 : -1) * 12f) / World.TILE_SIZE);
        int fy = MathUtils.floor((game.player.position.y + 4f) / World.TILE_SIZE);

        if (touch.mineHeld) { pickFrontTarget(fx, fy); game.mineTile(frontX, frontY, dt); }
        else if (mouseMine || keyMine) game.mineTile(tx, ty, dt);
        else game.resetMining();

        if (touch.placeHeld) { if (placeCooldown <= 0) { pickPlaceTarget(fx, fy); game.placeTile(placeX, placeY); placeCooldown = 0.22f; } }
        if (placeNow) game.placeTile(tx, ty);
        if (placeCooldown > 0) placeCooldown -= dt;
    }

    private int lastTargetX, lastTargetY, frontX, frontY, placeX, placeY;
    private float placeCooldown;

    /** Choose the solid tile in front of the player (body height first, then feet, then below). */
    private void pickFrontTarget(int fx, int fy) {
        int[][] cand = {{fx, fy + 1}, {fx, fy}, {fx, fy - 1}, {(int) (game.player.centerX() / World.TILE_SIZE), fy - 1}};
        frontX = fx; frontY = fy;
        for (int[] c : cand) if (Tiles.isBreakable(game.world.get(c[0], c[1]))) { frontX = c[0]; frontY = c[1]; return; }
    }

    private void pickPlaceTarget(int fx, int fy) {
        int[][] cand = {{fx, fy}, {fx, fy + 1}, {fx, fy - 1}};
        placeX = fx; placeY = fy;
        for (int[] c : cand) if (game.world.get(c[0], c[1]) == Tiles.AIR) { placeX = c[0]; placeY = c[1]; return; }
    }

    private void playEvents() {
        if (game.evJump) sfx.jump();
        if (game.evMine) sfx.mine();
        if (game.evPlace) sfx.place();
        if (game.evHurt) sfx.hurt();
        if (game.evCoin) sfx.coin();
        if (game.evDeath) sfx.death();
        if (game.evAttack) sfx.attack();
        if (game.evKill) sfx.kill();
        if (game.evLevelUp) sfx.levelUp();
        if (game.evSlam) sfx.hurt();
        if (game.tutorial.justAdvanced) { sfx.craft(); if (!game.tutorial.active()) Tutorial.markDone(); }
        game.clearEvents();
    }

    // ------------------------------------------------------------------ other states

    private void updatePaused() {
        if (kbPause || kbEnter) { setState(State.PLAYING); }
        else if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) { SaveData.save(game); hasSave = true; setState(State.MENU); }
        else if (clickThisFrame) { setState(State.PLAYING); }
    }

    private void updateGameOver() {
        if (stateTime > 0.8f && (kbEnter || clickThisFrame)) { hasSave = SaveData.exists(); menuChoice = 0; setState(State.MENU); }
    }

    private void updateLevelDone() {
        if (stateTime > 0.8f && (kbEnter || clickThisFrame)) {
            game.nextLevel(System.nanoTime() & 0xFFFFFF);
            SaveData.save(game); hasSave = true;
            setState(State.PLAYING);
        }
    }

    private void updateWin() {
        if (stateTime > 0.8f && (kbEnter || clickThisFrame)) { submitScoreOnce(); SaveData.delete(); hasSave = false; setState(State.MENU); }
    }

    private void submitScoreOnce() {
        if (scoreSubmitted || game == null) return;
        scoreSubmitted = true; lastRank = SaveData.submitScore(game.player.score);
    }

    // ------------------------------------------------------------------ camera

    private void updateCamera() {
        Player p = game.player;
        float halfW = camera.viewportWidth / 2f, halfH = camera.viewportHeight / 2f;
        float tx = p.position.x + p.width / 2f, ty = p.position.y + p.height / 2f + 10f;
        camera.position.x += (tx - camera.position.x) * 0.15f;
        camera.position.y += (ty - camera.position.y) * 0.15f;
        camera.position.x = MathUtils.clamp(camera.position.x, halfW, World.WIDTH * World.TILE_SIZE - halfW);
        camera.position.y = MathUtils.clamp(camera.position.y, halfH, World.HEIGHT * World.TILE_SIZE - halfH);
        if (game.shake > 0) {
            camera.position.x += MathUtils.random(-game.shake, game.shake) * 0.5f;
            camera.position.y += MathUtils.random(-game.shake, game.shake) * 0.5f;
        }
        camera.update();
    }

    // ------------------------------------------------------------------ drawing

    private void draw() {
        int sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        if (state == State.MENU || game == null) { drawMenu(sw, sh); return; }

        float dark = game.darkness();
        float[] sky = game.world.theme.sky(dark, skyTmp);
        Gdx.gl.glClearColor(sky[0], sky[1], sky[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        drawWorld(dark);
        drawLighting(dark, sw, sh);
        drawPlayerOnTop(dark);
        hud.render(batch, shapes, game, sw, sh);
        if (state == State.PLAYING) touch.render();
        drawOverlayScreens(sw, sh);
    }

    private void drawWorld(float dark) {
        batch.setProjectionMatrix(camera.combined);
        int ts = World.TILE_SIZE;
        int x0 = Math.max(0, MathUtils.floor((camera.position.x - camera.viewportWidth / 2f) / ts) - 1);
        int x1 = Math.min(World.WIDTH - 1, MathUtils.ceil((camera.position.x + camera.viewportWidth / 2f) / ts) + 1);
        int y0 = Math.max(0, MathUtils.floor((camera.position.y - camera.viewportHeight / 2f) / ts) - 1);
        int y1 = Math.min(World.HEIGHT - 1, MathUtils.ceil((camera.position.y + camera.viewportHeight / 2f) / ts) + 1);

        batch.begin();
        // only tiles inside the camera view are drawn
        TextureRegion pixel = assets.get("particle");
        for (int x = x0; x <= x1; x++) {
            int surf = game.world.surfaceY(x);
            for (int y = y0; y <= y1; y++) {
                int t = game.world.get(x, y);
                if (t == Tiles.AIR || (assets.tile(t) != null && !Tiles.isSolid(t))) {
                    // underground air shows a dark cave wall instead of the sky; it fades in just below the surface
                    if (y < surf - 1) {
                        float depthFade = MathUtils.clamp((surf - 1 - y) / 4f, 0f, 1f);
                        float[] cw = game.world.theme.cave; batch.setColor(cw[0], cw[1], cw[2], depthFade);
                        batch.draw(pixel, x * ts, y * ts, ts, ts);
                        batch.setColor(1f, 1f, 1f, 1f);
                    }
                }
                if (t == Tiles.AIR) continue;
                TextureRegion r = assets.tile(t);
                if (r == null) continue;
                float[] tint = game.world.theme.tintFor(t);
                boolean tinted = false;
                if (t == Tiles.LAVA) { float s = 0.85f + 0.15f * MathUtils.sin(game.playTime * 4f + x * 0.9f + y * 0.5f); batch.setColor(1f, s, s * 0.7f, 1f); tinted = true; }
                else if (tint != null) { batch.setColor(MathUtils.clamp(tint[0], 0f, 1f), MathUtils.clamp(tint[1], 0f, 1f), MathUtils.clamp(tint[2], 0f, 1f), 1f); tinted = true; }
                batch.draw(r, x * ts, y * ts, ts, ts);
                if (tinted) batch.setColor(1f, 1f, 1f, 1f);
                if (t == Tiles.CRUMBLE) { }
            }
        }
        // crack overlay for the tile being mined
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            float m = game.world.getMining(x, y);
            if (m > 0.02f) { batch.setColor(0f, 0f, 0f, 0.55f * m); batch.draw(assets.get("particle"), x * ts, y * ts, ts, ts * m); batch.setColor(1f, 1f, 1f, 1f); }
        }
        for (ItemDrop d : game.drops) {
            TextureRegion r = d.id == ItemDrop.COIN ? assets.get("coin") : d.id == ItemDrop.HEART ? assets.get("heart") : assets.tile(d.id);
            if (r == null) continue;
            float bob = MathUtils.sin(d.age * 6f) * 1.5f;
            batch.draw(r, d.x, d.y + bob, d.size(), d.size());
        }
        for (Projectile p : game.projectiles) {
            TextureRegion r = assets.get("arrow");
            if (p.vx > 0) batch.draw(r, p.x, p.y, p.w, p.h); else batch.draw(r, p.x + p.w, p.y, -p.w, p.h);
        }
        game.particles.render(batch, assets.get("particle"));
        batch.end();

        drawTargetHighlight();
    }

    /** Player is drawn AFTER the darkness overlay so he is always fully visible, with a soft warm halo. */
    private void drawPlayerOnTop(float dark) {
        if (game.player.dead) return;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float a = MathUtils.clamp(dark * 0.5f, 0f, 0.5f);
        if (a > 0.02f) {                                    // subtle halo behind the sprite at night
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            light(game.player.centerX(), game.player.centerY(), 46f, a, 1f, 0.85f, 0.6f);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        // enemies after the darkness so they never vanish into it; a faint halo keeps them fair to read
        for (Enemy e : game.enemies) {
            if (e.dead) continue;
            if (a > 0.02f) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                light(e.cx(), e.cy(), Math.max(e.w, e.h) * 1.4f, a * 0.6f, 1f, 0.35f, 0.3f);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            TextureRegion r = assets.get(e.textureName());
            float dx = e.x, dy = e.y, dw = e.w, dh = e.h;
            if (e.type == Enemy.BOSS) {
                float wf = e.windupFraction();
                if (wf > 0f) {                                       // telegraph: squash down + flash white/red, faster as it charges
                    float sq = 1f - 0.28f * wf; dh = e.h * sq; dw = e.w * (1f + 0.16f * wf);
                    dx = e.x - (dw - e.w) / 2f;
                    boolean flash = ((int) (game.playTime * (8f + 14f * wf))) % 2 == 0;
                    batch.setColor(1f, flash ? 1f : 0.35f, flash ? 1f : 0.35f, 1f);
                } else if (e.enraged) {
                    float pulse = 0.75f + 0.25f * MathUtils.sin(game.playTime * 9f);
                    batch.setColor(1f, pulse, pulse, 1f);
                }
            }
            if (e.hurtTimer > 0) batch.setColor(1f, 0.4f, 0.4f, 1f);
            if (e.facingRight) batch.draw(r, dx, dy, dw, dh); else batch.draw(r, dx + dw, dy, -dw, dh);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        game.player.render(batch);
        if (game.player.attackAnim > 0) drawSwing();
        batch.end();
    }

    private void drawSwing() {
        Player p = game.player;
        float t = 1f - p.attackAnim / 0.18f;
        float ang = (p.facingRight ? -70f + 140f * t : 250f - 140f * t);
        TextureRegion sword = assets.get("sword");
        float ox = p.facingRight ? p.position.x + p.width - 2 : p.position.x + 2;
        float oy = p.position.y + p.height * 0.5f;
        batch.draw(sword, ox - 8, oy - 8, 8, 8, 16, 16, p.facingRight ? 1.3f : -1.3f, 1.3f, ang);
    }

    private void drawTargetHighlight() {
        if (game.player.dead || hud.inventoryOpen) return;
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        if (touch.hitsHud(mx, my)) return;
        if (!Gdx.app.getType().equals(com.badlogic.gdx.Application.ApplicationType.Desktop)) return;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        boolean ok = game.inReach(lastTargetX, lastTargetY);
        if (ok) shapes.setColor(0.3f, 1f, 0.3f, 1f); else shapes.setColor(1f, 0.3f, 0.3f, 0.5f);
        shapes.rect(lastTargetX * World.TILE_SIZE, lastTargetY * World.TILE_SIZE, World.TILE_SIZE, World.TILE_SIZE);
        shapes.end();
    }

    /**
     * Darkness = a black overlay whose alpha depends on time of day and depth, then soft additive glows
     * (player, torches, lava, portal) drawn from a radial gradient texture so there are no visible rings.
     */
    private void drawLighting(float dark, int sw, int sh) {
        float depth = MathUtils.clamp(1f - game.player.centerY() / (game.world.surfaceY(0) * (float) World.TILE_SIZE), 0f, 1f);
        // underground is dark even in the daytime; night makes the surface dark too
        float a = MathUtils.clamp(dark * 0.78f + depth * (0.30f + game.world.theme.ambientDark), 0f, 0.94f);
        if (a < 0.04f) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(hud.screenMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f, 0.03f, 0.09f, a);
        shapes.rect(0, 0, sw, sh);
        shapes.end();

        // Lights: "erase" the overlay with an additive warm glow. Additive keeps colours and stays smooth.
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        float k = a;                                        // brighter glow when it is darker
        light(game.player.centerX(), game.player.centerY(), 120f, 0.55f * k, 1f, 0.92f, 0.75f);
        int ts = World.TILE_SIZE;
        int x0 = Math.max(0, MathUtils.floor((camera.position.x - camera.viewportWidth / 2f) / ts) - 6), x1 = Math.min(World.WIDTH - 1, MathUtils.ceil((camera.position.x + camera.viewportWidth / 2f) / ts) + 6);
        int y0 = Math.max(0, MathUtils.floor((camera.position.y - camera.viewportHeight / 2f) / ts) - 6), y1 = Math.min(World.HEIGHT - 1, MathUtils.ceil((camera.position.y + camera.viewportHeight / 2f) / ts) + 6);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            int t = game.world.get(x, y);
            float cx = (x + 0.5f) * ts, cy = (y + 0.5f) * ts;
            if (t == Tiles.TORCH) light(cx, cy + 3f, 150f, 0.8f * k, 1f, 0.72f, 0.30f);
            else if (t == Tiles.LAVA) light(cx, cy, 64f, 0.55f * k, 1f, 0.38f, 0.08f);
            else if (t == Tiles.PORTAL) light(cx, cy, 90f, 0.7f * k, 0.7f, 0.3f, 1f);
            else if (t == Tiles.FLAG) light(cx, cy, 60f, 0.4f * k, 1f, 0.9f, 0.4f);
        }
        for (Enemy e : game.enemies) if (!e.dead && e.type == Enemy.BOSS) light(e.cx(), e.cy(), 90f, 0.4f * k, 0.8f, 0.3f, 1f);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** One soft glow centred on (cx, cy) with the given radius, tinted and scaled by strength. */
    private void light(float cx, float cy, float radius, float strength, float r, float g, float b) {
        batch.setColor(r, g, b, MathUtils.clamp(strength, 0f, 1f));
        batch.draw(lightRegion, cx - radius, cy - radius, radius * 2f, radius * 2f);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawMenu(int sw, int sh) {
        Gdx.gl.glClearColor(0.10f, 0.12f, 0.22f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(hud.screenMatrix());
        shapes.setProjectionMatrix(hud.screenMatrix());
        float u = hud.uiScale;
        int items = hasSave ? 2 : 1;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < items; i++) {
            float y = menuButtonY(i, sh);
            shapes.setColor(i == menuChoice ? 0.95f : 0.3f, i == menuChoice ? 0.75f : 0.32f, i == menuChoice ? 0.2f : 0.42f, 0.95f);
            shapes.rect(sw / 2f - 150 * u, y - 26 * u, 300 * u, 52 * u);
        }
        shapes.end();
        batch.begin();
        float bob = MathUtils.sin(stateTime * 2f) * 4f * u;
        TextureRegion pl = assets.get("player");
        float ph = 170 * u, pw = ph * pl.getRegionWidth() / (float) pl.getRegionHeight();
        batch.draw(pl, sw * 0.5f - pw / 2f - 260 * u, sh * 0.38f + bob, pw, ph);
        hud.drawCentered(batch, Config.TITLE.toUpperCase(), new String[]{"Dig deep. Dodge traps. Beat the slime king.", ""}, sw, sh, 0.86f);
        hud.font().setColor(0f, 0f, 0f, 1f);
        String[] labels = hasSave ? new String[]{"CONTINUE", "NEW GAME"} : new String[]{"NEW GAME"};
        for (int i = 0; i < items; i++) {
            hud.layout().setText(hud.font(), labels[i]);
            hud.font().draw(batch, labels[i], (sw - hud.layout().width) / 2f, menuButtonY(i, sh) + 8 * u);
        }
        hud.font().setColor(1f, 1f, 1f, 1f);
        int[] hs = SaveData.highScores();
        float y = sh * 0.42f - 30 * u; float x = sw * 0.5f + 210 * u;
        hud.font().draw(batch, "HIGH SCORES", x, y + 60 * u);
        for (int i = 0; i < 5; i++) hud.font().draw(batch, (i + 1) + ".  " + hs[i], x, y + 34 * u - i * 22 * u);
        String help = "A/D move   W/Space jump   J attack   Hold click / F mine   Right-click / E place   1-9 hotbar   Tab bag   Esc pause";
        hud.layout().setText(hud.font(), help);
        hud.font().draw(batch, help, (sw - hud.layout().width) / 2f, 40 * u);
        batch.end();
    }

    private void drawOverlayScreens(int sw, int sh) {
        if (state == State.PLAYING) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(hud.screenMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, state == State.GAME_OVER ? 0.7f : 0.55f);
        shapes.rect(0, 0, sw, sh);
        shapes.end();
        batch.setProjectionMatrix(hud.screenMatrix());
        batch.begin();
        Player p = game.player;
        String hint = stateTime > 0.8f ? "Press Enter / tap to continue" : "";
        switch (state) {
            case PAUSED:
                hud.drawCentered(batch, "PAUSED", new String[]{"Esc / tap: resume", "Q: save and quit to menu", "M: music " + (sfx.musicOn ? "ON" : "OFF") + "    N: sound " + (sfx.sfxOn ? "ON" : "OFF")}, sw, sh, 0.65f);
                break;
            case GAME_OVER: {
                java.util.List<String> lines = new java.util.ArrayList<>();
                lines.add("Final score: " + p.score); lines.add("Coins: " + p.coins + "   Level reached: " + game.level);
                lines.add(lastRank >= 0 ? "NEW HIGH SCORE!  Rank #" + (lastRank + 1) : "Top scores:");
                int[] hs = SaveData.highScores(); for (int i = 0; i < 5; i++) lines.add((i + 1) + ".  " + hs[i]);
                lines.add(hint);
                hud.drawCentered(batch, "GAME OVER", lines.toArray(new String[0]), sw, sh, 0.8f);
                break; }
            case LEVEL_DONE:
                hud.drawCentered(batch, "LEVEL " + game.level + " COMPLETE", new String[]{"Score: " + p.score, "Next: Level " + (game.level + 1) + " (more traps, tougher enemies)", hint}, sw, sh, 0.65f);
                break;
            case WIN:
                hud.drawCentered(batch, "YOU WIN!", new String[]{"All " + Config.MAX_LEVEL + " levels cleared", "Final score: " + p.score, hint}, sw, sh, 0.65f);
                break;
            default: break;
        }
        batch.end();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void resize(int width, int height) {
        touch.resize(width, height);
        hud.resize(width, height);
        // keep a constant world-height view so tiles stay crisp; width follows the aspect ratio
        float aspect = width / (float) Math.max(1, height);
        camera.viewportHeight = VIEW_H_TILES * World.TILE_SIZE;
        camera.viewportWidth = camera.viewportHeight * aspect;
        camera.update();
    }

    @Override public void pause() { if (game != null && state == State.PLAYING) { SaveData.save(game); hasSave = true; setState(State.PAUSED); } }
    @Override public void resume() { }

    @Override public void dispose() {
        if (game != null && state == State.PLAYING) SaveData.save(game);
        batch.dispose(); shapes.dispose(); assets.dispose(); if (lightTex != null) lightTex.dispose(); touch.dispose(); hud.dispose(); sfx.dispose();
    }
}
