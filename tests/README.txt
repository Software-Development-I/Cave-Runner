Headless tests (no window needed). They exercise the real game classes.
Run from the project root, with the libGDX core jar (gdx, 1.12+) on the classpath:

  javac -cp gdx.jar -d out core/src/main/java/com/badlogic/Terraria/*.java tests/*.java
  java  -cp gdx.jar:out WorldTest             # 125 generated worlds: spawn safety + reachability
  java  -cp gdx.jar:out PlayerTest            # physics, damage, lives, wall-jump
  java  -cp gdx.jar:out SimTest               # mining, traps, enemies, checkpoints, levels
  java  -cp gdx.jar:out SaveTest              # save/load round trip, corruption, crafting
  java  -cp gdx.jar:out BossTest              # boss telegraph, dodge window, kiting regression, minions, enrage
  java  -cp gdx.jar:out TutTest                # tutorial steps advance on real actions
  java  -cp gdx.jar:out LayoutTest             # touch + HUD geometry at 15 resolutions (1,822 checks)
  java  -cp gdx.jar:out RenderOrderTest        # static guard: HUD uses the pixel projection, never the world camera
  java  -cp gdx.jar:out TouchVisibilityTest    # static guard: on-screen controls are visible by default everywhere
  java  -cp gdx.jar:out Soak                   # 20 game-minutes of randomised play
  java  -cp gdx.jar:out Soak2                  # 40 game-minutes with the boss fight running

Note: tests/*.java are for verifying game logic on a JVM only; they are not part of the app itself.
