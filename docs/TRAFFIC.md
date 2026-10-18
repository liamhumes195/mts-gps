# Recorded-road traffic

Version 0.2.0 adds optional server-controlled NPC traffic. The original MTS and Official Pack JARs are unchanged. The GPS renderer and local destinations remain client features.

## Verified integration points

The supplied MTS 24.0.0 binary was inspected with `javap`. Upstream sources at commit `ed968e2c26ed8a211a857e03fbf8b1887c14b7f9`, selected from the supplied JAR's April 14, 2026 class timestamps, helped explain behavior; the binary and successful compilation determine API compatibility.

- `WrapperWorld.getWrapperFor(ServerLevel)` and `AWrapperWorld.spawnEntity` create an actual `EntityVehicleF_Physics`.
- `PackParser.getAllPackItems()` supplies registered vehicle/paint and part items. Only the inspected `merc230`, `fordmustang69` and `scout` road cars are eligible; the pack's aircraft, tanks and trailers are excluded.
- `addPartsPostAddition` installs declared defaults. Empty wheel, engine and controller-seat slots are filled with compatible Official Pack items using `isPartValidForPackDef` and `addPartFromStack`. Failed assemblies are removed.
- `PartSeat.setRider(WrapperEntity.getWrapperFor(villager), true)` mounts a real villager and updates MTS's controller count. Vanilla mob AI is disabled; road following is handled by this addon.
- `rudderInputVar`, `throttleVar`, `brakeVar` and `parkingBrakeVar` are the normal MTS controls, synchronized through `ComputedVariable.setTo(value, true)`. No tick-by-tick teleportation or fabricated steering API is used.
- MTS's steering input has the opposite sign to yaw. Its `getTurningForce` speed/wheelbase scaling informed the pure-pursuit gain.
- Engines use real compatible tank contents, MTS auto-start and automatic shifting. Generated traffic bypasses server fuel wear using MTS's creative-vehicle flag; player vehicles are never changed.

Upstream: [vehicle physics](https://github.com/DonBruce64/MinecraftTransportSimulator/blob/ed968e2c26ed8a211a857e03fbf8b1887c14b7f9/mccore/src/main/java/minecrafttransportsimulator/entities/instances/EntityVehicleF_Physics.java), [part/seat assembly](https://github.com/DonBruce64/MinecraftTransportSimulator/blob/ed968e2c26ed8a211a857e03fbf8b1887c14b7f9/mccore/src/main/java/minecrafttransportsimulator/entities/components/AEntityF_Multipart.java).

## Controls, scope and lifetime

Use F8 → Spawn traffic / Clear traffic, or `/mtsgps traffic start [1–8]` and `/mtsgps traffic stop`. Only the integrated-world owner or a permission-level-2 operator can start a session. The server must have MTS GPS installed; normal GPS navigation remains usable against servers without it. A server operator can stop all active sessions with `/mtsgpstraffic clear`.

Starting traffic uploads only this client's current dimension and recorded road graph. The server validates node/edge counts and coordinates, associates the graph with the actual packet sender, applies permission and rate checks, and never accepts a client-selected vehicle, owner or spawn coordinate directly. Destinations are not uploaded. Traffic snapshots are held in memory and are not a shared road editor.

Spawning uses clear, already loaded recorded sections 18–64 blocks from the requesting player, with at least 14 blocks of connected driving path. Cars stay within 112 blocks of their owner. The spawn/controller loop has count and attempt limits. Chunk loading is never forced.

Cars select outgoing recorded directed edges without immediately reversing. One-way roads and disconnected components are respected. At a dead end they brake and are removed after four seconds; the session attempts a replacement. Obstacles and overspeed apply brakes. Once braking for an obstacle, the detection range stays latched until it clears so slowing does not cause repeated stop/start behavior. Leaving the route, losing the driver, a long standstill or a five-minute lifetime removes the generated car. Traffic pauses while a player is aboard.

Stop, logout, dimension change and server shutdown remove the session's vehicles/drivers. Cars have an MTS variable marker, which participates in its normal NBT save, and drivers have a vanilla entity tag. A periodic loaded-entity pass removes stale generated entities after a crash/reload. Untagged player-owned vehicles and ordinary villagers are not selected for cleanup.

## Testing

Pure behavioral checks are in `NavigationTests`: directed traffic progression, no invented connections/U-turns, random intersection branches, steering sign, acceleration/braking, turn slowdown, road-end stopping, corridor obstacles and bridge separation.

`gradlew.bat runGameTestServer` runs physical-engine integration fixtures on an isolated server in `run-traffic-tests`. Their code and empty structure live under `src/gameTest` and are excluded from the installable JAR. They install parts, seat real villagers and use the production controller to follow a recorded bend and stop at its end. Mercedes also stops for a real mob and resumes when it is removed. The batch disables natural mob spawning temporarily, restores the original rule afterward, and clears pre-existing entities inside each fixture's bounds. The final run passed all three model fixtures; `VERIFICATION.md` records the evidence and remaining limits.

Manual acceptance still requires a client: record a wide loop and T-junction, start three cars, observe the villagers and random vehicle paints, follow them through branches, step into their lane, and verify braking. Test one-way lanes, an elevated crossing, two players, disconnect/reload cleanup and each supported car model. Existing dashboard rendering and speech checks remain separate.
