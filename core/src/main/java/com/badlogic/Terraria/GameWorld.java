package com.badlogic.Terraria;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Whole game simulation: world, player, drops, and mining without rendering or input polling. */
public class GameWorld {
    public final World world;
    public final Player player;
    public final List<ItemDrop> drops = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final Tutorial tutorial = new Tutorial();

    public boolean evMine, evPlace, evCraft, evHurt, evDeath, evAttack, evKill, evCoin;
    public float shake = 0f;
    public boolean gameOver = false;
    private float deathTimer = 0f;
    public String message = "";
    public float messageTimer = 0f;

    private float timeOfDay = 0.30f;
    private static class Crumble { int x, y; float timer; boolean triggered, gone; float respawn; }
    private static class ArrowTrap { int x, y, dir; float timer; }
    private static class Rock { int x, y; float vy, py; boolean falling, done; }
    private final List<Crumble> crumbles = new ArrayList<>();
    private final List<ArrowTrap> arrowTraps = new ArrayList<>();
    private final List<Rock> rocks = new ArrayList<>();
    private float spawnTimer = 3f;

    private int mineX = -1;
    private int mineY = -1;
    private float mineProgress;

    public GameWorld(World world, Player player) {
        this.world = world;
        this.player = player;
        buildTraps();
    }

    private void buildTraps() {
        for (int[] a : world.arrowTraps) {
            ArrowTrap trap = new ArrowTrap();
            trap.x = a[0]; trap.y = a[1]; trap.dir = a[2];
            trap.timer = MathUtils.random(0.5f, Config.ARROW_INTERVAL);
            arrowTraps.add(trap);
        }
        for (int[] r : world.fallingRocks) {
            Rock rock = new Rock(); rock.x = r[0]; rock.y = r[1]; rocks.add(rock);
        }
    }

    public void update(float dt, Player.Controls controls) {
        dt = Math.min(dt, 1 / 30f);
        if (messageTimer > 0) messageTimer -= dt;
        if (shake > 0) shake = Math.max(0, shake - dt * 30f);
        timeOfDay = (timeOfDay + dt / Config.DAY_LENGTH) % 1f;
        if (gameOver) return;

        if (player.dead) {
            deathTimer += dt;
            if (deathTimer > 1.4f) {
                deathTimer = 0f;
                if (!player.loseLifeAndRespawn()) gameOver = true;
                else {
                    enemies.removeIf(enemy -> Math.abs(enemy.cx() - player.centerX()) < 96f);
                    toast("Lives left: " + player.lives);
                }
            }
            return;
        }

        player.update(dt, world, controls);
        if (controls.jumpPressed) tutorial.jumpCount++;
        if (player.tookDamageThisFrame) { evHurt = true; shake = 6f; }

        Rectangle hit = player.tryAttack(controls);
        if (hit != null) { evAttack = true; tutorial.attackCount++; doMelee(hit); }

        updateTraps(dt);
        updateEnemies(dt);
        updateProjectiles(dt);
        updateDrops(dt);
        updateSpawning(dt);
        tutorial.update(dt, player, controls);
    }

    public void mineTile(int tx, int ty, float dt) {
        int tile = world.get(tx, ty);
        if (!Tiles.isBreakable(tile)
            || !inReach(tx, ty)
            || Tiles.requiredTier(tile) > player.inventory.pickTier()) {
            resetMining();
            return;
        }

        if (tx != mineX || ty != mineY) {
            resetMining();
            mineX = tx;
            mineY = ty;
        }

        mineProgress += dt * player.miningPower() / Tiles.hardness(tile);
        world.setMining(tx, ty, Math.min(mineProgress, 1f));
        if (mineProgress >= 1f) {
            breakTile(tx, ty);
            resetMining();
        }
    }

    public void resetMining() {
        if (mineX >= 0) {
            world.setMining(mineX, mineY, 0f);
        }
        mineX = -1;
        mineY = -1;
        mineProgress = 0f;
    }

    private void breakTile(int tx, int ty) {
        int tile = world.get(tx, ty);
        world.remove(tx, ty);
        evMine = true;
        tutorial.minedCount++;

        float cx = (tx + 0.5f) * World.TILE_SIZE;
        float cy = (ty + 0.5f) * World.TILE_SIZE;
        int drop = Tiles.drop(tile);
        if (drop != 0) {
            drops.add(new ItemDrop(drop, 1, cx - 4f, cy - 4f));
        }
        settleSandAbove(tx, ty);
    }

    private void settleSandAbove(int tx, int ty) {
        for (int y = ty + 1; y < World.HEIGHT; y++) {
            if (world.get(tx, y) != Tiles.SAND) {
                break;
            }
            int land = y;
            while (land > 1 && !world.isSolid(tx, land - 1)) {
                land--;
            }
            if (land != y) {
                world.set(tx, y, Tiles.AIR);
                world.set(tx, land, Tiles.SAND);
            }
        }
    }

    public boolean inReach(int tx, int ty) {
        int ts = World.TILE_SIZE;
        int minX = MathUtils.floor(player.position.x / ts) - Config.REACH_TILES;
        int maxX = MathUtils.floor((player.position.x + player.width) / ts) + Config.REACH_TILES;
        int minY = MathUtils.floor(player.position.y / ts) - Config.REACH_TILES;
        int maxY = MathUtils.floor((player.position.y + player.height) / ts) + Config.REACH_TILES;
        return tx >= minX && tx <= maxX && ty >= minY && ty <= maxY;
    }

    public boolean placeTile(int tx, int ty) {
        int id = player.inventory.selectedId();
        if (!Inventory.isPlaceable(id)) return false;
        if (world.get(tx, ty) != Tiles.AIR || !inReach(tx, ty)) {
            return false;
        }

        float ts = World.TILE_SIZE;
        Rectangle tile = new Rectangle(tx * ts, ty * ts, ts, ts);
        if (player.getBounds().overlaps(tile)) {
            return false;
        }

        boolean attached = world.isSolid(tx - 1, ty)
            || world.isSolid(tx + 1, ty)
            || world.isSolid(tx, ty - 1)
            || world.isSolid(tx, ty + 1);
        if (!attached && id != Tiles.TORCH) {
            return false;
        }

        world.set(tx, ty, id);
        player.inventory.useSelected();
        evPlace = true;
        tutorial.placedCount++;
        return true;
    }

    public boolean craft(Recipe recipe) {
        boolean ok = recipe.craft(player.inventory);
        if (ok) evCraft = true;
        return ok;
    }

    public void toast(String text) {
        message = text;
        messageTimer = 2.5f;
    }

    private void doMelee(Rectangle box) {
        boolean hitAny = false;
        for (Enemy enemy : enemies) {
            if (enemy.dead || !box.overlaps(enemy.bounds())) continue;
            hitAny = true;
            float direction = player.facingRight ? 1f : -1f;
            boolean killed = enemy.hurt(player.attackDamage(), direction * Config.KNOCKBACK, 130f);
            if (killed) onEnemyKilled(enemy);
        }
        if (hitAny) shake = Math.max(shake, 2f);
    }

    private void onEnemyKilled(Enemy enemy) {
        evKill = true;
        player.addScore(Config.SCORE_ENEMY * (enemy.type + 1));
        int coins = 1 + MathUtils.random(0, 1 + enemy.type);
        for (int i = 0; i < coins; i++) {
            drops.add(new ItemDrop(ItemDrop.COIN, 1, enemy.cx(), enemy.cy()));
        }
        if (MathUtils.random() < 0.22f) {
            drops.add(new ItemDrop(ItemDrop.HEART, 1, enemy.cx(), enemy.cy()));
        }
    }

    private void updateEnemies(float dt) {
        Rectangle playerBounds = player.getBounds();
        for (Enemy enemy : enemies) {
            if (enemy.dead) continue;
            enemy.update(dt, world, player);
            int enemyX = MathUtils.floor(enemy.cx() / World.TILE_SIZE);
            int enemyY = MathUtils.floor(enemy.y / World.TILE_SIZE);
            if (world.get(enemyX, enemyY) == Tiles.LAVA) {
                enemy.hp = 0;
                enemy.dead = true;
            }
            if (!player.dead && enemy.bounds().overlaps(playerBounds)) {
                float direction = Math.signum(player.centerX() - enemy.cx());
                if (direction == 0) direction = 1;
                player.damage(enemy.damage, direction * Config.ENEMY_TOUCH_KNOCK, 150f, false);
            }
        }
        enemies.removeIf(enemy -> enemy.dead || enemy.y < -64f);
    }

    private void updateProjectiles(float dt) {
        for (Projectile projectile : projectiles) projectile.update(dt, world, player);
        projectiles.removeIf(projectile -> projectile.dead);
    }

    private void updateDrops(float dt) {
        for (ItemDrop drop : drops) {
            drop.update(dt, world, player);
            if (drop.collected && !player.dead) collect(drop);
        }
        drops.removeIf(drop -> drop.collected);
    }

    private void collect(ItemDrop drop) {
        if (drop.id == ItemDrop.COIN) {
            player.coins++;
            player.addScore(Config.SCORE_COIN);
            evCoin = true;
        } else if (drop.id == ItemDrop.HEART) {
            player.heal(Config.HEART_HEAL);
            evCoin = true;
            toast("+" + Config.HEART_HEAL + " HP");
        } else {
            player.inventory.add(drop.id, drop.amount);
        }
    }

    private void updateTraps(float dt) {
        int ts = World.TILE_SIZE;
        int footY = MathUtils.floor((player.position.y - 1f) / ts);
        int footX0 = MathUtils.floor(player.position.x / ts);
        int footX1 = MathUtils.floor((player.position.x + player.width - 0.5f) / ts);

        for (int x = footX0; x <= footX1; x++) {
            if (player.grounded && world.get(x, footY) == Tiles.CRUMBLE) {
                Crumble crumble = findCrumble(x, footY);
                if (crumble == null) {
                    crumble = new Crumble();
                    crumble.x = x;
                    crumble.y = footY;
                    crumbles.add(crumble);
                }
                crumble.triggered = true;
            }
        }

        for (Crumble crumble : crumbles) {
            if (crumble.gone) {
                crumble.respawn -= dt;
                if (crumble.respawn <= 0 && world.get(crumble.x, crumble.y) == Tiles.AIR
                    && !player.getBounds().overlaps(new Rectangle(crumble.x * ts, crumble.y * ts, ts, ts))) {
                    world.set(crumble.x, crumble.y, Tiles.CRUMBLE);
                    crumble.gone = false;
                    crumble.triggered = false;
                    crumble.timer = 0;
                    crumble.respawn = 0;
                }
            } else if (crumble.triggered) {
                crumble.timer += dt;
                if (crumble.timer >= Config.CRUMBLE_DELAY
                    && world.get(crumble.x, crumble.y) == Tiles.CRUMBLE) {
                    world.remove(crumble.x, crumble.y);
                    crumble.gone = true;
                    crumble.respawn = Config.CRUMBLE_RESPAWN;
                }
            }
        }

        for (ArrowTrap trap : arrowTraps) {
            if (world.get(trap.x, trap.y) != Tiles.ARROW_TRAP) continue;
            trap.timer -= dt;
            if (trap.timer > 0 || player.dead) continue;
            int playerTileX = MathUtils.floor(player.centerX() / ts);
            int playerTileY = MathUtils.floor(player.centerY() / ts);
            boolean sameRow = Math.abs(playerTileY - trap.y) <= 1;
            boolean inFront = (playerTileX - trap.x) * trap.dir > 0
                && Math.abs(playerTileX - trap.x) <= Config.ARROW_RANGE_TILES;
            if (sameRow && inFront && clearLine(trap.x + trap.dir, playerTileX, trap.y)) {
                float arrowX = trap.dir > 0 ? (trap.x + 1) * ts : trap.x * ts - 10f;
                projectiles.add(new Projectile(arrowX,
                    trap.y * ts + ts / 2f - 1.5f,
                    trap.dir * Config.ARROW_SPEED, Config.ARROW_DAMAGE));
                trap.timer = Config.ARROW_INTERVAL;
            } else trap.timer = 0.2f;
        }

        for (Rock rock : rocks) {
            if (rock.done) continue;
            if (!rock.falling) {
                if (world.get(rock.x, rock.y) != Tiles.STONE) {
                    rock.done = true;
                    continue;
                }
                int playerTileX = MathUtils.floor(player.centerX() / ts);
                int playerTileY = MathUtils.floor(player.centerY() / ts);
                if (!player.dead && Math.abs(playerTileX - rock.x) <= 1
                    && playerTileY < rock.y && rock.y - playerTileY <= 7
                    && clearColumn(rock.x, playerTileY, rock.y)) {
                    rock.falling = true;
                    rock.py = rock.y * ts;
                    rock.vy = 0;
                    world.remove(rock.x, rock.y);
                    toast("Look out!");
                }
            } else {
                rock.vy += Config.GRAVITY * dt;
                rock.py += rock.vy * dt;
                int rockTileY = MathUtils.floor(rock.py / ts);
                Rectangle rockBounds = new Rectangle(rock.x * ts + 1, rock.py, ts - 2, ts - 2);
                if (!player.dead && rockBounds.overlaps(player.getBounds())) {
                    player.damage(Config.ROCK_DAMAGE, 0, -60f, false);
                    rock.done = true;
                    continue;
                }
                if (world.isSolid(rock.x, rockTileY)) {
                    shake = Math.max(shake, 2f);
                    rock.done = true;
                }
            }
        }
    }

    private Crumble findCrumble(int x, int y) {
        for (Crumble crumble : crumbles) {
            if (crumble.x == x && crumble.y == y && !crumble.gone) return crumble;
        }
        return null;
    }

    private boolean clearLine(int fromX, int toX, int y) {
        int step = toX >= fromX ? 1 : -1;
        for (int x = fromX; x != toX; x += step) {
            if (world.isSolid(x, y)) return false;
        }
        return true;
    }

    private boolean clearColumn(int x, int fromY, int toY) {
        for (int y = fromY + 1; y < toY; y++) {
            if (world.isSolid(x, y)) return false;
        }
        return true;
    }

    public boolean isNight() {
        return timeOfDay > 0.75f || timeOfDay < 0.25f;
    }

    private void updateSpawning(float dt) {
        spawnTimer -= dt;
        if (spawnTimer > 0) return;
        spawnTimer = isNight() ? Config.SPAWN_INTERVAL_NIGHT : Config.SPAWN_INTERVAL_DAY;

        int alive = 0;
        for (Enemy enemy : enemies) if (!enemy.dead) alive++;
        if (alive >= Config.ENEMY_CAP) return;

        int playerTileX = MathUtils.floor(player.centerX() / World.TILE_SIZE);
        for (int attempt = 0; attempt < 12; attempt++) {
            int offset = MathUtils.random(10, 22) * (MathUtils.randomBoolean() ? 1 : -1);
            int tx = playerTileX + offset;
            if (tx < 2 || tx >= World.WIDTH - 2
                || Math.abs(tx - world.spawnX) < Config.SPAWN_SAFE_RADIUS) continue;

            int playerTileY = MathUtils.floor(player.centerY() / World.TILE_SIZE);
            int ty = playerTileY + MathUtils.random(-8, 8);
            for (int y = ty; y > ty - 10 && y > 2; y--) {
                if (!world.isSolid(tx, y) && world.isSolid(tx, y - 1)
                    && world.get(tx, y - 1) != Tiles.SPIKES) {
                    ty = y;
                    break;
                }
            }
            if (world.isSolid(tx, ty) || world.isSolid(tx, ty + 1)
                || world.isSolid(tx, ty + 2)) continue;
            if (world.get(tx, ty) == Tiles.LAVA
                || world.get(tx, ty - 1) == Tiles.LAVA) continue;

            enemies.add(new Enemy(pickEnemyType(ty, tx),
                tx * World.TILE_SIZE, ty * World.TILE_SIZE));
            return;
        }
    }

    private int pickEnemyType(int ty, int tx) {
        boolean underground = ty < world.surfaceY(tx) - 5;
        float roll = MathUtils.random();
        if (underground && roll < 0.4f) return Enemy.SKELETON;
        if (roll < 0.5f) return Enemy.SLIME;
        if (isNight() || underground) return Enemy.BAT;
        return Enemy.SLIME;
    }

    public void clearEvents() {
        evMine = false;
        evPlace = false;
        evCraft = false;
        evHurt = false;
        evDeath = false;
        evAttack = false;
        evKill = false;
        evCoin = false;
    }
}
