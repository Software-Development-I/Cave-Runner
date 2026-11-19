# Module 12 - Cave Runner

## Project overview

Cave Runner is a Java 17, libGDX survival-platformer. Its shared `core` module contains the game rules and rendering; `lwjgl3` and `android` provide desktop and mobile launchers.

## Gameplay included

- Five themed, seeded cave levels with procedural terrain, caves, ores, trees, sand, checkpoints, and portals.
- Player movement, wall-jumping, mining and block placement, health/lives, fall damage, and respawning.
- Spikes, lava, crumbling platforms, line-of-sight arrow traps, and falling rocks.
- Slimes, bats, skeletons, and a giant slime boss with telegraphed slams, shockwaves, recovery windows, minions, and an enrage phase.
- Inventory, hotbar, crafting, pickaxe/sword upgrades, drops, coins, score combos, and high scores.
- Day/night lighting, torches, synthesized audio, touch controls, and a skippable action-based tutorial.
- Save/continue, periodic autosaves, pause handling, and saved preferences.

## Modules and key classes

- `core`: shared game code. `World` generates tile data, `GameWorld` runs the headless simulation, `Player` implements physics/combat, and `Main` owns application input, screen states, and rendering.
- `lwjgl3`: desktop launcher.
- `android`: Android launcher, manifest, resources, and native dependency setup.
- `assets`: pixel-art textures. Sound effects and music are synthesized by `Sfx`.
- `tests`: standalone JVM test programs for world generation, player physics, simulation, boss behavior, saving/crafting, UI layout/render order, tutorial, and soak testing.

## Controls

| Action | Keyboard / mouse | Touch |
|---|---|---|
| Move | A / D or arrow keys | Left movement pad |
| Jump / wall-jump | W, Space, or Up | JUMP |
| Attack | J or X | HIT |
| Mine | Hold left mouse or F | Hold MINE |
| Place block | Right-click or E | PUT |
| Select hotbar slot | 1-9, mouse wheel, or click | Tap a slot |
| Inventory and crafting | Tab or I; mouse wheel scrolls recipes | BAG |
| Pause | Esc or P | Pause button |
| Skip tutorial | K | Tap the tutorial banner |
| Toggle music / sound | M / N | Pause menu |

Touch controls are shown by default on desktop as well as mobile; F2 toggles them.

## Build and run

From the project root on Windows:

```powershell
.\gradlew.bat lwjgl3:run
.\gradlew.bat lwjgl3:jar
.\gradlew.bat android:installDebug # Requires Android SDK and a connected device/emulator
```

Gameplay balance values are centralized in `core/src/main/java/com/badlogic/Terraria/Config.java`.

## Headless tests

The files in `tests/` are standalone `main` programs, not Gradle/JUnit tests. Compile them with the libGDX core JAR on the classpath, as described in `tests/README.txt`, then run the test classes individually. `Soak` and `Soak2` run longer randomized simulations.
