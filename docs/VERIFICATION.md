# Build verification — October 18, 2026

| Stage | Result | Minecraft acceptance |
| --- | --- | --- |
| 1. Project/dependencies | Java 17, Forge 1.20.1-47.4.28, Gradle 8.8, ForgeGradle 6.0.54; actual target JAR APIs inspected | Development startup passed; user's test-world login logged after pack staging fix |
| 2. Dashboard test screen | World mesh, bezel/support, body/chassis transforms, model-derived mount and calibration controls compiled | Placement, occlusion, camera/body motion and all colors not tested |
| 3. Roads/destinations/routes | Recording, directed graph, intersections, A*, saved destinations and JSON recovery implemented; behavioral checks passed | Driving/recording in game not tested |
| 4. Live map/speech | Cached terrain texture, recorded road/route display, heading, next turn, recalculation and native speech compiled | Visuals, controls, sound and runtime recalculation not tested |
| 5. Installable artifact | `build/libs/mtsgps-0.2.0.jar` produced by `reobfJar`; Java class major version 61 | Installation instructions and staged test procedure in README |
| 6. Villager traffic | Real MTS vehicle assembly, random supported models/paints, villager controller seats, directed road following, engine/brake controls, spawning and cleanup implemented | 3 headless Minecraft GameTests passed: Mustang, Mercedes and Scout each drive a recorded bend and stop; Mercedes also stops for a mob and resumes. Client UI/network sessions and broader gameplay remain untested |

Final command: `gradlew.bat build runGameTestServer --console=plain`, launched with Eclipse Temurin Java **17.0.20.1**.

Result: **BUILD SUCCESSFUL**, **73 behavioral checks passed**, **all 3 required Minecraft GameTests passed**. The executable suite is `src/test/java/dev/mtsgps/NavigationTests.java`. `check` depends on `gpsTest`; the unused standard JUnit task is disabled. The complete release build and server integration log is available locally at `inspection/traffic-final-checks.log`. The integration run reported `All 3 required tests passed :)` at 16:44:49 and finished successfully.

The checks cover empty/disconnected networks, interior edge snapping, one-way routing, shortest permitted paths, physical road detours, same-level intersections, bridge separation, stable repeated junctions, recording spacing/gaps/teleports, automatic road joins, turn handedness, U-turns, smooth corners, route progression, off-route detection, missed-turn route calculation, MTS/JOML rotation and full transformation equivalence, destination persistence, backups, malformed/incomplete data and invalid edge references/coordinates.

The built archive was inspected: it contains addon classes, mod metadata and addon language resources. It contains no MTS or Official Pack classes/assets and no nested dependencies. Both original CurseForge mod files still match their initial SHA-256 hashes in `libs/README.md`.

Addon SHA-256:

```text
8d561b308a07495e171117ae15af08e757ea9ddfa7a69b26e8a5744dd1dbfcc6  mtsgps-0.2.0.jar
```

The user's first development launch failed in MTS's `client.MinecraftMixin`: its released refmap resolved `allowsMultiplayer()Z` to the SRG name `m_91400_()Z`, which did not exist in the development namespace. The Gradle development configurations now enable runtime refmap remapping, use `build/createSrgToMcp/output.srg`, and depend on its generation. The original JARs remain byte-for-byte unchanged.

After the fix, `gradlew.bat runClient --console=plain` passed this injection, loaded the addon plus both target mods, initialized client resources and the sound engine, and opened the `Minecraft* Forge 1.20.1` window. The log confirms `Remapping refMap mts.refmap.json using .../build/createSrgToMcp/output.srg`. Startup evidence is in `inspection/run-client-fix.log` and `run/logs/latest.log`.

During the user's subsequent world test, Official Pack recipes reported missing items. Inspection of MTS's loader revealed that it scans physical JARs in `mods` and MTS's own directory; the separately cached Gradle classpath pack was outside those paths. `stageMtsOfficialPack` now copies the user's exact V29 JAR from the configured CurseForge path into `run/mods` before launch, checks for the Mercedes definition, and avoids a duplicate classpath pack.

After that change, MTS parsed Official Pack content and the log confirms that the integrated test world loaded and `Dev` joined. The staged pack's SHA-256 exactly matches the original (`a6ca5d37a89fd398cafd96fc8f87673cec5b0247c1651bc0b9f02b23f86c7908`). Launch evidence is in `inspection/run-client-pack-fix.log`. This world interaction was performed by the user; no dashboard placement, driving route, screenshot, GPS speech playback or multiplayer test has been independently validated. The development build still requires the README's remaining in-world acceptance checks.

The user's installed-instance log subsequently confirmed MTS GPS loading in their CurseForge world, followed by four rejected destination-save attempts with the old combined warning. That warning covered several causes, so the log alone cannot distinguish a blank name from a missing map pin. Version 0.1.1 separates those messages, labels/focuses the name box, preserves typed names across widget rebuilds, and reads the current vehicle position for Save car here and the save command. Java 17 compilation, reobfuscation and the existing 49 navigation/persistence checks passed. Those checks do not exercise Minecraft GUI interactions; the destination-saving correction still requires this in-game regression check:

1. In the Mercedes, open F8 and try Save map pin without clicking the map: it should explain that a map pin is required.
2. Click inside the map and try saving with a blank name: it should tell you to type a destination name.
3. Type `Home`, toggle One way/Two way while not recording, and resize the window: the typed name should remain.
4. Click Save map pin: it should report Saved destination: Home. Reopen the world and verify Home persists.
5. Try Save car here and `/mtsgps save Garage` while seated, then repeat the command after leaving the car: the command should reject saving a stale vehicle location.

## Traffic integration acceptance

`src/gameTest/java/dev/mtsgps/TrafficGameTests.java` runs with the exact supplied MTS and Official Pack dependencies on Forge 47.4.28 in the separate `run-traffic-tests` world. It uses the production spawning and driving code. Each fixture checks the selected model, a real villager remaining mounted in its controller seat, MTS serialization of the cleanup marker, travel along a recorded bend within four blocks of its line, and braking at its recorded end. The Mercedes fixture places a second real villager in its lane, checks a full stop before that mob, removes it and verifies resumed driving. This exposed and corrected an obstacle detection range that shrank during stopping; per-car braking memory now retains that range until the obstruction clears. Pure regression checks cover the retention/reset behavior.

The three model fixtures passed with real engine-driven motion. This is a Minecraft **server** runtime result. It does not establish dashboard rendering, visual driver alignment, every paint/part combination, traffic request packets from a real client, owner permissions, multiplayer behavior, native MTS road-block junctions or every terrain/road geometry. The client procedure in README and `docs/TRAFFIC.md` remains required for those cases. No test fixture classes or structures are included in the release JAR.
