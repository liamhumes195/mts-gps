# MTS GPS

A dashboard GPS addon for the Mercedes **merc230** in the **MTS Official Content Pack**. The screen is mounted inside the car, to the right of the steering wheel and above the gear shifter, with an adjustable position, angle and size.

This is an **early alpha for Minecraft 1.20.1 Forge**.

## Features

- A live, north-up map with the vehicle's position and direction.
- Saved destinations and a highlighted driving route.
- Next-turn instructions, distance and local spoken guidance.
- Route recalculation after a missed turn.
- Road recording by driving, with two-way and one-way roads and connections at intersections.
- A configurable physical dashboard screen and a calibration grid.

Routes follow **roads you have recorded**. Record a connected network first; the addon does not generate roads or use Minecraft walking paths. Height checks help keep bridges separate from roads below them. Matching uses the vehicle's internal name, so all merc230 paint variants are included.

## Required mods

Install these alongside MTS GPS:

- [Immersive Vehicles (MTS)](https://www.curseforge.com/minecraft/mc-mods/minecraft-transport-simulator) **24.0.0 for Minecraft 1.20.1**.
- [MTS Official Content Pack](https://www.curseforge.com/minecraft/mc-mods/transport-simulator-official-vehicle-set) **V29 for Minecraft 1.20.1**.

Use **Java 17** and **Forge 47.4.28**. Other Minecraft versions and mod loaders are not supported by this file.

MTS GPS is a separate addon. It does not include or replace either required mod.

## Getting started

1. Install the addon JAR in your Minecraft instance's `mods` folder with both required mods.
2. Assemble a Mercedes merc230 and enter its driver seat.
3. Press **F8** to open GPS controls. The key can be changed in Minecraft's Controls menu.
4. Use **Mount settings** and the test grid to adjust the dashboard placement if needed, then switch back to the live map.
5. Choose **Record roads**, close the menu, and drive a small road network. Stop recording when finished. Use **Join junction** to connect nearby road segments at an intersection.
6. Give a destination a name and choose **Save car here**, or click the map and choose **Save map pin**. Select the saved entry and choose **Navigate saved**.

Only the driver can record roads. Recording stops when leaving the car or after a large movement gap. Roads and destinations are saved locally for each world/server and dimension; they are not shared between players.

Configuration is stored in `config/mtsgps-client.toml`. Recorded roads and destinations are in `config/mtsgps/data`.

## Alpha status and limits

The Java 17 build, startup and test-world loading have been checked. Dashboard alignment, camera/render-mod compatibility, road guidance and speech still need broader in-game testing. Use the calibration controls and try a short recorded route before relying on it for a long drive.

Terrain appears progressively from already-loaded client chunks. Unknown terrain has a plain background. Spoken guidance depends on the operating system's local speech backend; the controls include a speech status and **Test voice** button.

This is an independent addon, not an official release from the Immersive Vehicles or Official Content Pack teams.
