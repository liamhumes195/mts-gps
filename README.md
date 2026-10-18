# MTS GPS 0.1.0

A separate client-side Forge addon for the **MTS Official Pack V29 Mercedes `merc230`**, across every paint variant. It renders a physical dashboard screen, housing and support inside the vehicle. The map menu is an additional control panel.

**Status: compiled development candidate; development-client startup verified. Dashboard rendering, placement, driving and GPS speech playback have not been tested in a world.** The initial mount is based on the inspected pack geometry and requires the calibration check below. This is not a claim of a completed, in-game-validated dashboard GPS.

## Build and dependencies

* Java 17
* Minecraft 1.20.1, Forge 47.4.28 (the MDK's version)
* Immersive Vehicles **1.20.1-24.0.0**
* MTS Official Pack **1.20.1-V29**

The addon declares these exact MTS/pack versions. It does not embed, replace, or edit either mod. See `libs/README.md` for local build dependency names and hashes; see `docs/API-INSPECTION.md` for the attachment decision and inspected APIs.

Copy the dependencies into `libs` with the names listed in [`libs/README.md`](libs/README.md). These third-party JARs are not included in this repository.

To use an Official Pack JAR from an existing instance, create an ignored `gradle-local.properties` file in the project root:

```properties
official_pack_jar=C:/path/to/your/instance/mods/MTS Official Pack-1.20.1-V29.jar
```

Before a development launch, `stageMtsOfficialPack` checks that this JAR contains `merc230` and copies it unchanged to `run/mods/MTS Official Pack-1.20.1-V29.jar`. MTS 24 scans physical JARs in that folder for vehicle definitions, so the pack must be there even when Forge can already see it on a development classpath. It is loaded once, from `run/mods`. Update the property if you move the source JAR; without that property, the staging task uses `libs/mtsofficialpack-29.jar`.

With Java 17 selected:

```powershell
.\gradlew.bat build --console=plain
```

If Java 17 is not selected already, set `JAVA_HOME` to your JDK installation before running Gradle:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\gradlew.bat build --console=plain
```

Output: **`build/libs/mtsgps-0.1.0.jar`**, reobfuscated by ForgeGradle for installation.

`build` runs the executable behavioral suite through `gpsTest`. The standard JUnit `test` task is disabled because the suite uses an ordinary Java main. Run just the suite with `gradlew.bat gpsTest`. No Minecraft window is needed for these checks.

## Installation

1. Close Minecraft.
2. Copy only `build/libs/mtsgps-0.1.0.jar` into your Minecraft instance's `mods` folder.
3. Keep the original Immersive Vehicles and Official Pack JARs there. Do not install any development/cache copies from this project.
4. Start that Forge 1.20.1 instance with Java 17. Use a test world for the following checks.

No server installation is required. Roads, destinations and navigation are local to each client; they are not shared between players.

## Staged Minecraft testing

1. **Dependencies/load:** confirm MTS GPS appears in the Mods list and the world loads with both target mods. If loading fails, collect `logs/latest.log`. Development launch is available with `.\gradlew.bat runClient`, using an isolated `run` folder and the local dependencies. Its startup has been smoke-tested; entering a world still needs validation.
2. **Physical dashboard:** spawn and assemble a `merc230`, install its seats and enter the driver seat. Press **F8** (remappable under Controls → MTS GPS), then **Mount settings → Test grid**. Close the menus. The grid must be on the dashboard to the right of the wheel and above the shifter, with readable LEFT/RIGHT/TOP labels. Turn the car and your head; drive over a slope and observe body lean. The screen must follow the car instead of the viewport. Walk around outside: bodywork must occlude it. Repeat with a second paint, then the other variants. Adjust X/Y/Z, yaw/pitch/roll and width/height as needed. Changes save immediately. Switch the test grid back to **Live GPS**.
3. **Recorded road graph:** on a level road, open F8, choose **Two way**, and **Record roads**. Close the menu and drive a small network with at least one T-junction and a detour. Stop at the end and stop recording. To add another branch, start a new recording; end it at the existing intersection. Automatic snapping joins a nearby existing road segment; **Join junction** explicitly connects segments within 2.5 blocks at the same elevation. Test **One way** by recording a separate street. Dismount, re-enter, and verify recordings never connect across a gap. Record a bridge crossing and verify the elevated road remains separate.
4. **Destinations/map/navigation/speech:** at a recorded destination, enter a name and choose **Save car here**. Alternatively click the map, enter a name, and **Save map pin**. Saved destinations cycle with `<` / `Next saved >`; use **Navigate saved**. The north-up map should show terrain, recorded roads, a bright teal road route, saved destination diamonds and a white car arrow following vehicle orientation. Check the next turn and its distance on the actual in-car screen with the menu closed. **Test voice** checks the local speech backend. Drive the route, then deliberately miss a turn onto another recorded branch: after sustained deviation the addon should recalculate along recorded roads.
5. **Persistence and failure cases:** leave and re-enter the world and confirm destinations/roads remain. Confirm a disconnected destination reports no connected driving route instead of drawing a walking path. Try a destination far from all recorded roads. Switch dimensions/worlds and confirm their data is separate. Repeat the route with voice disabled and with the control menu closed.

## Controls and data

F8 opens the control menu while seated in a target Mercedes. Click the map to place a pin (snapped to a nearby recorded road when possible), name it, save it, and navigate to the saved entry. Recording requires the **driver** seat. Stop a recording before switching between two-way and one-way.

Client commands:

```text
/mtsgps                  Open controls
/mtsgps test             Toggle the physical calibration grid
/mtsgps record           Start/stop recording (current one/two-way setting)
/mtsgps junction         Connect same-height road segments at the car
/mtsgps save Home        Save current car position as Home
/mtsgps go Home          Route to Home
/mtsgps stop             Stop navigation
```

Configuration: `config/mtsgps-client.toml`. Mount values also have live +/- controls; position/size step is 0.01 block and angle step is 1 degree. Reset mount restores the model-derived defaults. `spokenDirections` controls speech; `terrainMap` enables the sampled surface background; map radius and road snap/recalculation distances are configurable.

Roads and destinations: `config/mtsgps/data/<SHA-256 of world/server and dimension>.json`. The exact mapping is written to `latest.log`. Local worlds use their absolute save path; multiplayer uses the server address plus dimension. Moving/renaming a world directory or changing the server address changes its data identity. Back up this folder to transfer/preserve your recordings.

Changes autosave every five seconds while in a world and when explicitly saving or stopping recording. Files use an atomic replacement where supported and retain a validated `.bak`. Invalid input is preserved; unrecoverable data disables editing rather than silently overwriting it. Network limits are 50,000 nodes, 150,000 directed edges and 1,000 destinations per world/dimension.

## Behavior and current limits

* Routes use A* on recorded road edges, with Euclidean road length as cost. One block is spoken/displayed as one meter. There is no Minecraft walking pathfinder, inferred street network, traffic model or automatic road detection.
* Drive near the road center while recording. Samples default to 3 blocks apart and join an existing node/segment within 2 blocks, with a 1.25-block vertical limit. Manual junction connection uses 2.5 blocks by default. Closely parallel roads can join if recorded within this tolerance; lower `junctionSnapBlocks` for closely spaced lanes.
* Dismounting, changing vehicles/drivers/dimensions, or a movement gap over 24 blocks stops recording. Stopping/restarting creates a separate segment unless it meets an existing road.
* Route endpoints are projected onto a recorded road within 24 blocks and a 4-block height limit. Off-road portions are not included in the highlighted driving route. If a destination is farther than 8 blocks from the reached road end, the screen reports the remaining offset instead of declaring arrival.
* Recalculation requires being over 10 blocks from the remaining route for 30 client ticks, with a 60-tick interval between route attempts. There must be a connected recorded road near the new position. A missing connection is reported; no straight-line fallback is drawn.
* Terrain uses only already-loaded client chunks. Sampling is bounded, cached and refreshed progressively; unknown areas have a plain background. It is a surface map, so the Nether ceiling/caves are not mapped as underground road layers. The car/map update at 5 Hz; physical screen transforms interpolate every render frame.
* Speech uses Mojang's native local text-to-speech backend in a dedicated narrator instance. It does not enable Minecraft's global accessibility narrator or call an online speech service. Availability depends on the platform/native speech backend. Check the menu's speech status and Test voice; OS speech volume/device settings apply. Playback remains untested here.
* A parked/unoccupied Mercedes displays a standby screen; the currently occupied target car displays this client's live navigation. Passengers can use the display and destinations; only the controller seat records roads.
* The screen has actual world depth testing. Shader packs, Fabulous rendering, third-party render mods and alternate camera modes still require compatibility checks.

## Verification record

The final Java 17 `build` and `reobfJar` succeeded, and **49 behavioral checks passed**. The checks exercise directed routes, shortest-road detours, edge-interior endpoints, segment splitting, explicit and automatic junctions, bridges, recording gaps, smooth/sharp turns, missed-turn rerouting, persistence recovery, malformed files and MTS/JOML transform agreement. See `docs/VERIFICATION.md` for the staged result.

**Startup smoke test passed:** the Java 17 development client remapped MTS's mixins, loaded MTS GPS and the two target mods, initialized resources/sound, and opened its Minecraft window. **Not tested in a Minecraft world:** physical placement/occlusion, vehicle/camera motion, all paint variants, terrain appearance, UI interaction while driving, GPS speech output and multiplayer behavior. Until the staged checks above pass, treat the JAR as an installable development build requiring in-game acceptance.

## Development launch troubleshooting

If an older run configuration fails with `inject_ivAllowsMultiplayer` / `m_91400_()Z` / `mts.refmap.json`, the released MTS mixin references are still using SRG names while the development Minecraft uses official names. The Gradle run configurations now enable `mixin.env.remapRefMap` and point `mixin.env.refMapRemappingFile` at the output of `createSrgToMcp`; all development Java launch tasks depend on that mapping task. This follows [Mixin's runtime reference-map remapping mechanism](https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/mixin/refmap/RemappingReferenceMapper.java).

Run `.\gradlew.bat runClient` again. For generated IDE run configurations, regenerate them with `genIntellijRuns` or `genEclipseRuns` before retrying. The fix is for development launches and does not alter either original mod JAR or disable MTS's mixins. The asset-copying lines before the exception are normal first-launch setup.

If the Official Pack appears loaded but its vehicles/items are missing, confirm `run/mods/MTS Official Pack-1.20.1-V29.jar` exists. Running `.\gradlew.bat runClient` stages that file automatically from `official_pack_jar` in `gradle-local.properties`, a `-Pofficial_pack_jar=...` override, or the local `libs` fallback. Do not also add a second Official Pack copy to the development runtime classpath or `run/mods`.
