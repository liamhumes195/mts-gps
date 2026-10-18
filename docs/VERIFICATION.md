# Build verification — October 18, 2026

| Stage | Result | Minecraft acceptance |
| --- | --- | --- |
| 1. Project/dependencies | Java 17, Forge 1.20.1-47.4.28, Gradle 8.8, ForgeGradle 6.0.54; actual target JAR APIs inspected | Development startup passed; user's test-world login logged after pack staging fix |
| 2. Dashboard test screen | World mesh, bezel/support, body/chassis transforms, model-derived mount and calibration controls compiled | Placement, occlusion, camera/body motion and all colors not tested |
| 3. Roads/destinations/routes | Recording, directed graph, intersections, A*, saved destinations and JSON recovery implemented; behavioral checks passed | Driving/recording in game not tested |
| 4. Live map/speech | Cached terrain texture, recorded road/route display, heading, next turn, recalculation and native speech compiled | Visuals, controls, sound and runtime recalculation not tested |
| 5. Installable artifact | `build/libs/mtsgps-0.1.0.jar` produced by `reobfJar`; Java class major version 61 | Installation instructions and staged test procedure in README |

Final command: `gradlew.bat build --console=plain`, launched with Eclipse Temurin Java **17.0.20.1**.

Result: **BUILD SUCCESSFUL**, **49 behavioral checks passed**. The executable suite is `src/test/java/dev/mtsgps/NavigationTests.java`. `check` depends on `gpsTest`; the unused standard JUnit task is disabled. The latest build log after the development-launch and pack-discovery fixes is available locally at `inspection/build-after-pack-fix.log`.

The checks cover empty/disconnected networks, interior edge snapping, one-way routing, shortest permitted paths, physical road detours, same-level intersections, bridge separation, stable repeated junctions, recording spacing/gaps/teleports, automatic road joins, turn handedness, U-turns, smooth corners, route progression, off-route detection, missed-turn route calculation, MTS/JOML rotation and full transformation equivalence, destination persistence, backups, malformed/incomplete data and invalid edge references/coordinates.

The built archive was inspected: it contains addon classes, mod metadata and addon language resources. It contains no MTS or Official Pack classes/assets and no nested dependencies. Both original CurseForge mod files still match their initial SHA-256 hashes in `libs/README.md`.

Addon SHA-256:

```text
9a16dc49387194b77e2933b695fcb933a1809203c4ec91314f79e3dc9872f352  mtsgps-0.1.0.jar
```

The user's first development launch failed in MTS's `client.MinecraftMixin`: its released refmap resolved `allowsMultiplayer()Z` to the SRG name `m_91400_()Z`, which did not exist in the development namespace. The Gradle development configurations now enable runtime refmap remapping, use `build/createSrgToMcp/output.srg`, and depend on its generation. The original JARs remain byte-for-byte unchanged.

After the fix, `gradlew.bat runClient --console=plain` passed this injection, loaded the addon plus both target mods, initialized client resources and the sound engine, and opened the `Minecraft* Forge 1.20.1` window. The log confirms `Remapping refMap mts.refmap.json using .../build/createSrgToMcp/output.srg`. Startup evidence is in `inspection/run-client-fix.log` and `run/logs/latest.log`.

During the user's subsequent world test, Official Pack recipes reported missing items. Inspection of MTS's loader revealed that it scans physical JARs in `mods` and MTS's own directory; the separately cached Gradle classpath pack was outside those paths. `stageMtsOfficialPack` now copies the user's exact V29 JAR from the configured CurseForge path into `run/mods` before launch, checks for the Mercedes definition, and avoids a duplicate classpath pack.

After that change, MTS parsed Official Pack content and the log confirms that the integrated test world loaded and `Dev` joined. The staged pack's SHA-256 exactly matches the original (`a6ca5d37a89fd398cafd96fc8f87673cec5b0247c1651bc0b9f02b23f86c7908`). Launch evidence is in `inspection/run-client-pack-fix.log`. This world interaction was performed by the user; no dashboard placement, driving route, screenshot, GPS speech playback or multiplayer test has been independently validated. The development build still requires the README's remaining in-world acceptance checks.
