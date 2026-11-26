# Glance

A client-only Fabric mod with a **sound detector**, **through-wall marker boxes** and a tabbed in-game menu,
plus a small info HUD.

- **Minecraft:** 26.2
- **Loader:** Fabric Loader 0.19.3+
- **Requires:** Fabric API, Java 25
- **Side:** client only (nothing is loaded on servers)

## Sound detector

Glance listens to every positional sound the game plays to you and shows where it came from:

- **Marker boxes**: a coloured square drawn on your screen around each sound, visible through walls.
  **Red** = close (and always for danger sounds like TNT, lightning, warden), **orange** = mid range,
  **yellow** = far. New sounds pulse. Sounds off-screen or behind you get a small marker on the screen edge
  pointing the way. Name and distance are shown under each box.
- **Radar**: a top-down panel with a sweep animation. Up is where you face.
- **Crosshair ring**: coloured marks around your crosshair pointing toward each sound.
- **Sound list**: newest sounds with a direction arrow, distance and repeat count.

Sounds are classified as players, hostile mobs, passive mobs, danger, footsteps, blocks, ambient or other.
Each type can be switched off in the Filters tab.

### Sensitivity and volume

- It catches **every** positional sound, including ones with no vanilla subtitle (named from the sound id).
- Detection happens before the game applies your master volume, so you can set **Master Volume to 0%** and the
  detector should still work. Keep the individual category sliders (Hostile Creatures, Players, Blocks, ...)
  above 0%, because the game drops sounds in a category set to 0% before Glance can see them.
- **The range cannot be boosted.** There is no way for a client mod to hear louder than the server lets it: the
  server only sends a sound to players within its range (about 16 blocks for most sounds, more for loud ones).
  Glance already shows everything that reaches you, and the distance setting (up to 128 blocks) defaults to its maximum. Loud sounds such as thunder and explosions can arrive from well past 64 blocks; ordinary sounds such as footsteps and mob noises stop at about 16.

### Box accuracy

Boxes are projected from your base FOV setting and work in first person only. They can drift slightly while
sprinting or under effects that change FOV. For entity sounds the box sits around the middle of the entity;
for blocks it sits on the block.

### Using it on servers

Marker boxes show where sounds are through walls, which works like a "sound ESP". Many multiplayer servers
ban mods like that, and Modrinth has content rules for such mods. Check both before you use or publish it.

## Menu

Press **Right Shift** to open the Glance menu (Detector, Boxes, Radar, Ring & List, Filters, Info HUD). It has
switches, sliders, position pickers, five accent colours and a live preview. Changes save automatically.

## Controls

| Default key | Action |
|---|---|
| Right Shift | Open the Glance menu |
| H | Toggle the info HUD |
| J | Move the info HUD to the next corner |

All can be rebound under Options > Controls > Key Binds > Glance.

## Config

`config/glance.json` is created on first launch. Everything in the menu is stored there, plus a few extras
(`boxSize` in blocks, `boxMinPx` for the smallest box). Values are clamped on load so a bad edit cannot break rendering.

## Building

1. Install JDK 25.
2. Create the Gradle wrapper once (see below).
3. Run `./gradlew build`. The jar is in `build/libs/` (use the one without `-sources`).
4. Test with `./gradlew runClient`.

### Building on GitHub

Push this repo to GitHub and open the **Actions** tab. The `build` workflow compiles the mod on every push and
attaches the jar to the run as an artifact named `glance`. No local setup needed.

### First-time setup

The Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) are not included. Create them with
a recent Gradle (9.x):

```
gradle wrapper --gradle-version latest
```

or copy them from a project generated at https://fabricmc.net/develop/template/ .

If a version in `gradle.properties` is rejected, check the current values on that page and update them.

## Publishing on Modrinth

1. Replace `YOUR_NAME` in `fabric.mod.json` and `LICENSE`, and the GitHub URL in `fabric.mod.json`.
2. Build the jar with `./gradlew build`.
3. On https://modrinth.com/dashboard/projects create a new project: type **Mod**, name **Glance**,
   a summary, and a longer description (this file works as a starting point).
4. Set environments: **Client** required, **Server** unsupported.
5. Upload `build/libs/glance-1.2.1.jar` as a version: game version **26.2**, loader **Fabric**,
   dependency **Fabric API** (required).
6. Submit the project for review.

Replace the placeholder icon at `src/main/resources/assets/glance/icon.png` with your own artwork before release,
and upload a project icon and screenshots on the Modrinth page.
