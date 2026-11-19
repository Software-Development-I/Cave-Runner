package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole simulation: world + player + enemies + traps + drops. Contains NO rendering or GL calls,
 * so it can be driven headlessly in tests. Main only feeds it input and draws the result.
 */
public class GameWorld {
    public World world;
    public final Player player;
    public final List<Enemy> enemies = new ArrayList<>();
    /** Enemies created while iterating {@link #enemies} (e.g. boss minions); merged once the loop ends. */
    private final List<Enemy> pendingEnemies = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<ItemDrop> drops = new ArrayList<>();
    public final Particles particles = new Particles();
    public final Tutorial tutorial = new Tutorial();

    public int level = 1;
    public float timeOfDay = 0.30f;      // 0..1, 0.25 = sunrise, 0.5 = noon, 0.75 = sunset
    public float playTime = 0f;
    public boolean levelComplete = false;
    public boolean gameOver = false;
    public boolean bossSpawned = false, bossDefeated = false;
    public float deathTimer = 0f;
    public float shake = 0f;
    public String message = ""; public float messageTimer = 0f;
    public int checkpointsHit = 0;

    // event flags for sound/UI, cleared each frame by Main
    public boolean evMine, evPlace, evHurt, evCoin, evDeath, evJump, evAttack, evKill, evLevelUp, evCraft, evSlam;

    // ---- trap state ----
    private static class Crumble { int x, y; float timer; boolean triggered, gone; float respawn; }
    private static class ArrowTrap { int x, y, dir; float timer; }
    private static class Rock { int x, y; float vy, py; boolean falling, done; }
    private final List<Crumble> crumbles = new ArrayList<>();
    private final List<ArrowTrap> arrowTraps = new ArrayList<>();
    private final List<Rock> rocks = new ArrayList<>();

    // ---- mining state ----
    private int mineX = -1, mineY = -1;
    private float mineProgress = 0f;
    private float spawnTimer = 3f;
    private float autosave = Config.AUTOSAVE_INTERVAL;

    public GameWorld(com.badlogic.gdx.graphics.g2d.TextureRegion playerTex, long seed, int level) {
        this.level = level;
        this.world = new World(seed, level);
        float sx = world.spawnX * World.TILE_SIZE + (World.TILE_SIZE - Config.PLAYER_W) / 2f;
        float sy = world.spawnY * World.TILE_SIZE;
        player = new Player(sx, sy, playerTex);
        starterKit();
        buildTraps();
        toast("Level " + level + ": " + world.theme.name);
    }

    private void starterKit() {
        player.inventory.add(Tiles.TORCH, 4);
    }

    /** Builds a fresh world for the next level, keeping player progress. */
    public void nextLevel(long seed) {
        level = Math.min(Config.MAX_LEVEL, level + 1);
        world = new World(seed, level);
        enemies.clear(); pendingEnemies.clear(); projectiles.clear(); drops.clear();
        crumbles.clear(); arrowTraps.clear(); rocks.clear();
        float sx = world.spawnX * World.TILE_SIZE + (World.TILE_SIZE - Config.PLAYER_W) / 2f;
        float sy = world.spawnY * World.TILE_SIZE;
        player.setSpawn(sx, sy);
        player.respawnAtSpawn();
        player.health = Config.MAX_HEALTH;
        levelComplete = false; bossSpawned = false; bossDefeated = false;
        checkpointsHit = 0;
        buildTraps();
        toast("Level " + level + ": " + world.theme.name);
    }

    public void toast(String s) { message = s; messageTimer = 2.5f; }

    private void buildTraps() {
        for (int[] a : world.arrowTraps) {
            ArrowTrap t = new ArrowTrap(); t.x = a[0]; t.y = a[1]; t.dir = a[2]; t.timer = MathUtils.random(0.5f, Config.ARROW_INTERVAL);
            arrowTraps.add(t);
        }
        for (int[] r : world.fallingRocks) { Rock k = new Rock(); k.x = r[0]; k.y = r[1]; rocks.add(k); }
    }

    // =================================================================== main step

    public void update(float dt, Player.Controls controls) {
        dt = Math.min(dt, 1 / 30f);
        playTime += dt;
        if (messageTimer > 0) messageTimer -= dt;
        if (shake > 0) shake = Math.max(0, shake - dt * 30f);

        // day / night
        timeOfDay = (timeOfDay + dt / Config.DAY_LENGTH) % 1f;

        if (gameOver) { particles.update(dt); return; }

        if (player.dead) {
            deathTimer += dt;
            if (deathTimer > 1.4f) {
                deathTimer = 0f;
                if (!player.loseLifeAndRespawn()) { gameOver = true; }
                else { enemies.removeIf(e -> Math.abs(e.cx() - player.centerX()) < 96f); toast("Lives left: " + player.lives); }
            }
            particles.update(dt);
            return;
        }

        boolean wasDead = player.dead;
        player.update(dt, world, controls);
        if (controls.jumpPressed && !player.grounded) evJump = true;
        if (controls.jumpPressed) tutorial.jumpCount++;
        if (player.tookDamageThisFrame) { evHurt = true; shake = 6f; particles.burst(player.centerX(), player.centerY(), 12, 90f, 0.5f, 0.9f, 0.1f, 0.1f, true); }
        if (player.dead && !wasDead) { evDeath = true; shake = 10f; deathTimer = 0f; particles.burst(player.centerX(), player.centerY(), 40, 160f, 1f, 0.9f, 0.2f, 0.2f, true); }

        // melee
        Rectangle hit = player.tryAttack(controls);
        if (hit != null) { evAttack = true; tutorial.attackCount++; doMelee(hit); }

        updateTraps(dt);
        updateEnemies(dt);
        updateProjectiles(dt);
        updateDrops(dt);
        updateSpawning(dt);
        particles.update(dt);
        checkGoal();
        tutorial.update(dt, player, controls);

        autosave -= dt;
    }

    // =================================================================== mining & building

    /** Hold-to-mine one tile. Call each frame while the mine input is held, with the tile under the cursor. */
    public void mineTile(int tx, int ty, float dt) {
        if (player.dead) return;
        int t = world.get(tx, ty);
        if (!Tiles.isBreakable(t)) { resetMining(); return; }
        if (!inReach(tx, ty)) { resetMining(); return; }
        if (Tiles.requiredTier(t) > 0 && player.inventory.pickTier() < Tiles.requiredTier(t)) {
            if (messageTimer <= 0) toast("Need a better pickaxe for " + Tiles.name(t));
            resetMining(); return;
        }
        if (tx != mineX || ty != mineY) { resetMining(); mineX = tx; mineY = ty; }
        mineProgress += dt * player.miningPower() / Tiles.hardness(t);
        world.setMining(tx, ty, Math.min(mineProgress, 1f));
        if (MathUtils.random() < dt * 14f) particles.blockBreak((tx + 0.5f) * World.TILE_SIZE, (ty + 0.5f) * World.TILE_SIZE, t);
        if (mineProgress >= 1f) { breakTile(tx, ty); resetMining(); }
    }

    public void resetMining() {
        if (mineX >= 0) world.setMining(mineX, mineY, 0f);
        mineX = mineY = -1; mineProgress = 0f;
    }

    private void breakTile(int tx, int ty) {
        int t = world.get(tx, ty);
        world.remove(tx, ty);
        evMine = true; tutorial.minedCount++;
        float cx = (tx + 0.5f) * World.TILE_SIZE, cy = (ty + 0.5f) * World.TILE_SIZE;
        particles.blockBreak(cx, cy, t);
        player.addScore(Tiles.scoreFor(t));
        int drop = Tiles.drop(t);
        if (drop != 0) drops.add(new ItemDrop(drop, 1, cx - 4, cy - 4));
        // sand & floating gravel behaviour: unsupported sand above falls
        settleSandAbove(tx, ty);
        // break the whole trunk/leaves cluster gently: leaves give a chance at a heart
        if (t == Tiles.LEAVES && MathUtils.random() < 0.05f) drops.add(new ItemDrop(ItemDrop.HEART, 1, cx - 4, cy - 4));
    }

    /**
     * Makes sand above (tx, ty) fall. Walks up the column collecting each contiguous sand tile and
     * drops it straight down onto the nearest support, preserving the number of sand blocks.
     */
    private void settleSandAbove(int tx, int ty) {
        for (int y = ty + 1; y < World.HEIGHT; y++) {
            if (world.get(tx, y) != Tiles.SAND) break;
            int land = y;
            while (land > 1 && !world.isSolid(tx, land - 1)) land--;
            if (land != y) {
                world.set(tx, y, Tiles.AIR);
                world.set(tx, land, Tiles.SAND);
            }
        }
    }

    public boolean inReach(int tx, int ty) {
        int ts = World.TILE_SIZE;
        int minX = MathUtils.floor(player.position.x / ts) - 3, maxX = MathUtils.floor((player.position.x + player.width) / ts) + 3;
        int minY = MathUtils.floor(player.position.y / ts) - 3, maxY = MathUtils.floor((player.position.y + player.height) / ts) + 3;
        return tx >= minX && tx <= maxX && ty >= minY && ty <= maxY;
    }

    /** Place the selected hotbar block at the target tile. */
    public boolean placeTile(int tx, int ty) {
        if (player.dead) return false;
        int id = player.inventory.selectedId();
        if (!Inventory.isPlaceable(id)) return false;
        if (world.get(tx, ty) != Tiles.AIR || !inReach(tx, ty)) return false;
        if (Tiles.isSolid(id)) {
            float ts = World.TILE_SIZE;
            Rectangle tile = new Rectangle(tx * ts, ty * ts, ts, ts);
            if (player.getBounds().overlaps(tile)) return false;
            for (Enemy e : enemies) if (!e.dead && e.bounds().overlaps(tile)) return false;
        }
        // needs a neighbour or something to attach to, so no floating blocks in mid-air
        boolean attached = world.isSolid(tx - 1, ty) || world.isSolid(tx + 1, ty) || world.isSolid(tx, ty - 1) || world.isSolid(tx, ty + 1)
            || world.get(tx, ty - 1) == Tiles.WOOD;
        if (!attached && id != Tiles.TORCH) return false;
        world.set(tx, ty, id);
        player.inventory.useSelected();
        evPlace = true; tutorial.placedCount++;
        return true;
    }

    // =================================================================== combat

    private void doMelee(Rectangle box) {
        boolean any = false;
        for (Enemy e : enemies) {
            if (e.dead || !box.overlaps(e.bounds())) continue;
            any = true;
            float dir = player.facingRight ? 1f : -1f;
            boolean killed = e.hurt(player.attackDamage(), dir * Config.KNOCKBACK * (e.type == Enemy.BOSS ? 0.3f : 1f), 130f);
            particles.burst(e.cx(), e.cy(), 8, 90f, 0.4f, 1f, 1f, 0.6f, true);
            if (killed) onEnemyKilled(e);
        }
        if (any) shake = Math.max(shake, 2f);
    }

    private void onEnemyKilled(Enemy e) {
        evKill = true;
        int pts = e.type == Enemy.BOSS ? Config.SCORE_BOSS : Config.SCORE_ENEMY * (e.type + 1);
        player.addScore(pts);
        particles.burst(e.cx(), e.cy(), 24, 130f, 0.8f, 0.6f, 0.9f, 0.4f, true);
        int coins = e.type == Enemy.BOSS ? 12 : 1 + MathUtils.random(0, 1 + e.type);
        for (int i = 0; i < coins; i++) drops.add(new ItemDrop(ItemDrop.COIN, 1, e.cx(), e.cy()));
        if (MathUtils.random() < (e.type == Enemy.BOSS ? 1f : 0.22f)) drops.add(new ItemDrop(ItemDrop.HEART, 1, e.cx(), e.cy()));
        if (e.type == Enemy.BOSS) { bossDefeated = true; toast("Boss defeated! Reach the portal!"); for (Enemy m : enemies) if (m.minion && !m.dead) { m.hp = 0; m.dead = true; particles.burst(m.cx(), m.cy(), 10, 90f, 0.5f, 0.4f, 0.9f, 0.4f, true); } }
    }

    private void updateEnemies(float dt) {
        Rectangle pb = player.getBounds();
        for (Enemy e : enemies) {
            if (e.dead) continue;
            e.update(dt, world, player);
            if (e.type == Enemy.BOSS) bossEvents(e);
            // enemies that fall into lava die
            if (world.get(MathUtils.floor(e.cx() / World.TILE_SIZE), MathUtils.floor(e.y / World.TILE_SIZE)) == Tiles.LAVA) {
                e.hp = 0; e.dead = true; particles.burst(e.cx(), e.cy(), 12, 90f, 0.5f, 1f, 0.5f, 0.1f, true);
            }
            boolean harmless = e.type == Enemy.BOSS && e.bossState == Enemy.B_RECOVER;
            if (!player.dead && !harmless && e.bounds().overlaps(pb)) {
                float dir = Math.signum(player.centerX() - e.cx()); if (dir == 0) dir = 1;
                player.damage(e.damage, dir * Config.ENEMY_TOUCH_KNOCK, 150f, false);
            }
        }
        if (!pendingEnemies.isEmpty()) { enemies.addAll(pendingEnemies); pendingEnemies.clear(); }
        enemies.removeIf(e -> e.dead || e.y < -64f);
    }

    /** Slam shockwave: a horizontal band along the ground that hurts if the player is NOT airborne. */
    private void bossEvents(Enemy b) {
        if (b.slamLanded) {
            shake = Math.max(shake, 9f); evHurt = evHurt;   // screen shake only; sound is played by Main via evSlam
            evSlam = true;
            particles.burst(b.cx(), b.y, 30, 150f, 0.7f, 0.7f, 0.5f, 0.9f, true);
            float reach = b.enraged ? 190f : 150f;
            boolean onGround = player.grounded;
            if (!player.dead && onGround && Math.abs(player.centerX() - b.cx()) < reach && Math.abs(player.position.y - b.y) < 40f) {
                player.damage(Math.round(b.damage * 0.8f), Math.signum(player.centerX() - b.cx()) * 200f, 220f, false);
            }
        }
        if (b.wantsMinion) {
            int minions = 0; for (Enemy e : pendingEnemies) if (e.minion) minions++;
            for (Enemy e : enemies) if (!e.dead && e.minion) minions++;
            if (minions < 5) {
                Enemy m = new Enemy(Enemy.SLIME, b.cx() + (player.centerX() > b.cx() ? 40f : -40f), b.y + 10f);
                m.minion = true; m.vy = 200f; m.vx = MathUtils.random(-40f, 40f); pendingEnemies.add(m);
                particles.burst(m.x, m.y, 10, 80f, 0.4f, 0.4f, 0.9f, 0.4f, true);
            }
        }
    }

    private void updateProjectiles(float dt) {
        for (Projectile p : projectiles) p.update(dt, world, player);
        projectiles.removeIf(p -> p.dead);
    }

    private void updateDrops(float dt) {
        for (ItemDrop d : drops) {
            d.update(dt, world, player);
            if (d.collected && !player.dead && Math.hypot(player.centerX() - d.x, player.centerY() - d.y) < 14f) {
                collect(d);
            }
        }
        drops.removeIf(d -> d.collected);
    }

    private void collect(ItemDrop d) {
        if (d.id == ItemDrop.COIN) { player.coins++; player.addScore(Config.SCORE_COIN); evCoin = true; }
        else if (d.id == ItemDrop.HEART) { player.heal(Config.HEART_HEAL); evCoin = true; toast("+" + Config.HEART_HEAL + " HP"); }
        else {
            int left = player.inventory.add(d.id, d.amount);
            if (left > 0) drops.add(new ItemDrop(d.id, left, d.x, d.y)); // inventory full: keep on the ground
            else evPlace = false;
        }
    }

    // =================================================================== traps

    private void updateTraps(float dt) {
        // Crumbling blocks: standing on one starts a timer, then it breaks and later regrows
        int ts = World.TILE_SIZE;
        int footY = MathUtils.floor((player.position.y - 1f) / ts);
        int fx0 = MathUtils.floor(player.position.x / ts), fx1 = MathUtils.floor((player.position.x + player.width - 0.5f) / ts);
        for (int x = fx0; x <= fx1; x++) {
            if (player.grounded && world.get(x, footY) == Tiles.CRUMBLE) {
                Crumble c = findCrumble(x, footY);
                if (c == null) { c = new Crumble(); c.x = x; c.y = footY; crumbles.add(c); }
                if (!c.triggered) c.triggered = true;
            }
        }
        for (Crumble c : crumbles) {
            if (c.gone) {
                c.respawn -= dt;
                if (c.respawn <= 0 && world.get(c.x, c.y) == Tiles.AIR
                    && !player.getBounds().overlaps(new Rectangle(c.x * ts, c.y * ts, ts, ts))) {
                    world.set(c.x, c.y, Tiles.CRUMBLE); c.gone = false; c.triggered = false; c.timer = 0; c.respawn = 0;
                }
            } else if (c.triggered) {
                c.timer += dt;
                if (c.timer >= Config.CRUMBLE_DELAY && world.get(c.x, c.y) == Tiles.CRUMBLE) {
                    world.remove(c.x, c.y); c.gone = true; c.respawn = Config.CRUMBLE_RESPAWN;
                    particles.blockBreak((c.x + 0.5f) * ts, (c.y + 0.5f) * ts, Tiles.CRUMBLE);
                }
            }
        }

        // Arrow traps fire when the player is in line of sight and in range
        for (ArrowTrap t : arrowTraps) {
            if (world.get(t.x, t.y) != Tiles.ARROW_TRAP) continue;         // trap was mined out
            t.timer -= dt;
            if (t.timer > 0 || player.dead) continue;
            int ptx = MathUtils.floor(player.centerX() / ts), pty = MathUtils.floor(player.centerY() / ts);
            boolean sameRow = Math.abs(pty - t.y) <= 1;
            boolean inFront = (ptx - t.x) * t.dir > 0 && Math.abs(ptx - t.x) <= Config.ARROW_RANGE_TILES;
            if (sameRow && inFront && clearLine(t.x + t.dir, ptx, t.y)) {
                float ax = t.dir > 0 ? (t.x + 1) * ts : t.x * ts - 10f;
                projectiles.add(new Projectile(ax, t.y * ts + ts / 2f - 1.5f, t.dir * Config.ARROW_SPEED, Config.ARROW_DAMAGE));
                t.timer = Config.ARROW_INTERVAL;
            } else t.timer = 0.2f; // keep checking
        }

        // Falling rocks: walking under a loose stone drops it
        for (Rock r : rocks) {
            if (r.done) continue;
            if (!r.falling) {
                if (world.get(r.x, r.y) != Tiles.STONE) { r.done = true; continue; }
                int ptx = MathUtils.floor(player.centerX() / ts), pty = MathUtils.floor(player.centerY() / ts);
                if (!player.dead && Math.abs(ptx - r.x) <= 1 && pty < r.y && r.y - pty <= 7 && clearColumn(r.x, pty, r.y)) {
                    r.falling = true; r.py = r.y * ts; r.vy = 0;
                    world.remove(r.x, r.y);
                    toast("Look out!");
                }
            } else {
                r.vy += Config.GRAVITY * dt; r.py += r.vy * dt;
                int ty = MathUtils.floor(r.py / ts);
                Rectangle rb = new Rectangle(r.x * ts + 1, r.py, ts - 2, ts - 2);
                if (!player.dead && rb.overlaps(player.getBounds())) {
                    player.damage(Config.ROCK_DAMAGE, 0, -60f, false);
                    particles.blockBreak(r.x * ts + 8, r.py + 8, Tiles.STONE); r.done = true; continue;
                }
                if (world.isSolid(r.x, ty)) { particles.blockBreak(r.x * ts + 8, (ty + 1) * ts, Tiles.STONE); shake = Math.max(shake, 2f); r.done = true; }
            }
        }
    }

    private Crumble findCrumble(int x, int y) { for (Crumble c : crumbles) if (c.x == x && c.y == y && !c.gone) return c; return null; }

    private boolean clearLine(int fromX, int toX, int y) {
        int step = toX >= fromX ? 1 : -1;
        for (int x = fromX; x != toX; x += step) if (world.isSolid(x, y)) return false;
        return true;
    }

    private boolean clearColumn(int x, int fromY, int toY) {
        for (int y = fromY + 1; y < toY; y++) if (world.isSolid(x, y)) return false;
        return true;
    }

    // =================================================================== spawning

    public boolean isNight() { return timeOfDay > 0.75f || timeOfDay < 0.25f; }

    /** 0 (full day) .. 1 (full night), smooth. */
    public float darkness() {
        float t = timeOfDay;
        if (t >= 0.25f && t <= 0.75f) {                    // day
            float edge = Math.min(t - 0.25f, 0.75f - t);
            return MathUtils.clamp(1f - edge / 0.08f, 0f, 1f) * 0.55f;
        }
        return 0.85f;
    }

    private void updateSpawning(float dt) {
        spawnTimer -= dt;
        if (spawnTimer > 0) return;
        spawnTimer = isNight() ? Config.SPAWN_INTERVAL_NIGHT : Config.SPAWN_INTERVAL_DAY;
        int alive = 0; for (Enemy e : enemies) if (!e.dead) alive++;
        if (alive >= Config.ENEMY_CAP + level * 2) return;

        // pick a spawn tile 10-22 tiles from the player, off-screen, standing in open space
        int ptx = MathUtils.floor(player.centerX() / World.TILE_SIZE);
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = MathUtils.random(10, 22) * (MathUtils.randomBoolean() ? 1 : -1);
            int tx = ptx + dx;
            if (tx < 2 || tx >= World.WIDTH - 2 || Math.abs(tx - world.spawnX) < Config.SPAWN_SAFE_RADIUS) continue;
            int pty = MathUtils.floor(player.centerY() / World.TILE_SIZE);
            int ty = pty + MathUtils.random(-8, 8);
            // find floor near ty
            for (int y = ty; y > ty - 10 && y > 2; y--) {
                if (!world.isSolid(tx, y) && world.isSolid(tx, y - 1) && world.get(tx, y - 1) != Tiles.SPIKES) { ty = y; break; }
            }
            if (world.isSolid(tx, ty) || world.isSolid(tx, ty + 1) || world.isSolid(tx, ty + 2)) continue;
            if (world.get(tx, ty) == Tiles.LAVA || world.get(tx, ty - 1) == Tiles.LAVA) continue;
            int type = pickEnemyType(ty);
            if (type < 0) continue;
            Enemy e = new Enemy(type, tx * World.TILE_SIZE, ty * World.TILE_SIZE);
            scaleForLevel(e);
            enemies.add(e);
            return;
        }
    }

    private int pickEnemyType(int ty) {
        float depth = 1f - ty / (float) Math.max(1, world.surfaceY(0) + 20);
        boolean underground = ty < world.surfaceY(MathUtils.clamp(MathUtils.floor(player.centerX() / World.TILE_SIZE), 0, World.WIDTH - 1)) - 5;
        float r = MathUtils.random();
        if (underground && depth > 0.25f && r < 0.4f) return Enemy.SKELETON;   // skeletons deeper down only
        if (r < 0.5f) return Enemy.SLIME;
        if (isNight() || underground) return Enemy.BAT;
        return Enemy.SLIME;
    }

    private void scaleForLevel(Enemy e) {
        float f = 1f + (level - 1) * 0.2f;
        e.hp = e.maxHp = Math.round(e.hp * f);
        e.damage = Math.round(e.damage * (1f + (level - 1) * 0.1f));
    }

    /** Boss lives near the bottom of the world: spawn when the player first gets deep enough. */
    public void trySpawnBoss() {
        if (bossSpawned || bossDefeated) return;
        int depthTile = MathUtils.floor(player.centerY() / World.TILE_SIZE);
        if (depthTile < 20 && level >= 2) {
            Enemy b = new Enemy(Enemy.BOSS, player.position.x + 120f, player.position.y + 40f);
            b.hp = b.maxHp = Math.round(Config.BOSS_HP * (1f + (level - 2) * 0.3f));
            enemies.add(b); bossSpawned = true; toast("A giant slime awakens!");
        }
    }

    public Enemy activeBoss() { for (Enemy e : enemies) if (e.type == Enemy.BOSS && !e.dead) return e; return null; }

    // =================================================================== goal / checkpoints

    private void checkGoal() {
        trySpawnBoss();
        int ts = World.TILE_SIZE;
        int x0 = MathUtils.floor(player.position.x / ts), x1 = MathUtils.floor((player.position.x + player.width) / ts);
        int y0 = MathUtils.floor(player.position.y / ts), y1 = MathUtils.floor((player.position.y + player.height) / ts);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            int t = world.get(x, y);
            if (t == Tiles.FLAG) {
                Vector2Compat.setSpawn(player, x * ts + (ts - Config.PLAYER_W) / 2f, y * ts);
                world.set(x, y, Tiles.AIR);   // consumed; replaced by particles
                checkpointsHit++;
                particles.burst(x * ts + 8, y * ts + 8, 20, 90f, 1f, 1f, 0.9f, 0.2f, false);
                toast("Checkpoint saved!");
            } else if (t == Tiles.PORTAL) {
                if (level >= 2 && bossSpawned && !bossDefeated) { if (messageTimer <= 0) toast("Defeat the boss first!"); }
                else if (!levelComplete) {
                    levelComplete = true; evLevelUp = true;
                    int bonus = Config.SCORE_LEVEL + Math.max(0, 300 - (int) playTime);
                    player.score += bonus;
                    toast("Level complete!  +" + bonus);
                }
            }
        }
    }

    /** Small helper so we keep Player's spawn API narrow. */
    private static final class Vector2Compat {
        static void setSpawn(Player p, float x, float y) { p.setSpawn(x, y); }
    }

    public boolean shouldAutosave() { if (autosave <= 0) { autosave = Config.AUTOSAVE_INTERVAL; return true; } return false; }
    public void clearEvents() { evMine = evPlace = evHurt = evCoin = evDeath = evJump = evAttack = evKill = evLevelUp = evCraft = evSlam = false; }
}
