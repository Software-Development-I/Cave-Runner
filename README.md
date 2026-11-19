# Cave Runner

A 2D survival-platformer built with [libGDX](https://libgdx.com/) (Java 17). Dig through procedurally generated
caves, dodge traps, fight enemies, craft tools, and reach the portal at the end of each level.

## Run

```
./gradlew lwjgl3:run          # desktop
./gradlew lwjgl3:jar          # runnable jar in lwjgl3/build/libs
./gradlew android:installDebug  # Android (needs the Android SDK)
```

## Build an Android APK on Windows

With the Android SDK installed and configured, run this from the project root:

```powershell
.\gradlew.bat :android:assembleDebug
```

The debug APK is written to `android\build\outputs\apk\debug\android-debug.apk`.
To build and install it on a connected device or emulator instead, run:

```powershell
.\gradlew.bat :android:installDebug
```

## Controls

| Action | Keyboard / mouse | Touch |
|---|---|---|
| Move | A / D or arrows | left pad |
| Jump / wall-jump | W, Space, Up | JUMP |
| Attack | J or X | HIT |
| Mine (hold) | hold left click, or F | MINE (tile in front of you) |
| Place block | right click, or E | PUT |
| Hotbar | 1-9, mouse wheel, click | tap a slot |
| Inventory + crafting | Tab or I (mouse wheel scrolls recipes) | BAG |
| Pause | Esc or P | pause button |
| Skip tutorial | K | tap the tutorial banner |
| Music / sound | M / N | pause menu |

## Features

- **Health and lives:** 100 HP, 3 lives, fall damage, invincibility flashing, checkpoints, game over.
- **Traps:** spikes, lava, crumbling platforms, arrow traps (need line of sight), falling rocks.
- **Enemies:** slime, bat, skeleton (deeper only), and a giant slime boss from level 2.
- **Boss fight:** the giant slime squashes and flashes red before every slam (jump over the shockwave!),
  is harmless while it recovers (your opening to attack), summons up to 5 small slimes, and enrages below half health.
- **Level themes:** 5 levels, each with its own sky, ground tint, cave colour, sand/tree/lava mix and darkness
  (Green Hills, Sunbaked Dunes, Frozen Deep, Ember Caverns, The Abyss).
- **Tutorial:** a skippable 6-step first-run guide that advances when you actually do each action (K or tap to skip).
- **Score:** points per block, ore, kill, coin and level, a x1-x5 combo multiplier, and a saved top-5 list.
- **World:** layered-noise terrain, caves, coal / iron / gold by depth, trees, gravity sand.
- **Crafting:** wood, stone and iron pickaxes and swords; torches. Recipes live in `Recipe.java`.
- **Day/night:** nights are darker and spawn more enemies; torches and lava glow.
- **Save/load:** autosave every 60 s and on pause; Continue from the menu.
- **Audio:** all sound effects and the music loop are synthesised in code (`Sfx.java`), no audio files.

## Tuning

Every balance number (damage, speeds, spawn rates, costs) is in `core/.../Config.java`.

## Layout

- `core/` - all game code. `GameWorld` is the headless simulation; `Main` handles input and drawing.
- `lwjgl3/` - desktop launcher. `android/` - Android launcher.
- `assets/` - pixel-art textures. `player.png` is the hero sprite.
