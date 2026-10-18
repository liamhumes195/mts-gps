# MTS GPS 0.2.0 - Alpha

- Added optional villager traffic on recorded roads, with randomly chosen Mercedes, Mustang and Scout models/paints.
- Traffic cars are assembled with real MTS engines, wheels and controller seats, and steer through MTS's vehicle physics.
- Added F8 Spawn traffic / Clear traffic and `/mtsgps traffic start [1–8]` / `/mtsgps traffic stop`.
- Added directed intersection choices, turn slowdown, basic obstacle braking, road-end handling and temporary-entity cleanup.
- Added server permission checks, car/snapshot limits and configurable traffic speed. Traffic requires the addon on the server; single-player includes it automatically.

## Included from 0.1.1

- Replaced the ambiguous destination-save warning with specific messages for a missing name, missing map pin or invalid data.
- Added a visible destination-name label and automatic name-box focus when selecting a map pin.
- Preserved typed destination names when changing road controls or resizing the window.
- Ensured Save car here and `/mtsgps save` read the current Mercedes position and require being seated in it.

## Included from 0.1.0

Initial development build for Minecraft 1.20.1 Forge, Immersive Vehicles 24.0.0 and MTS Official Content Pack V29.

- Added a physical dashboard GPS screen for all merc230 paint variants.
- Added configurable mounting position, rotation, dimensions and a calibration grid.
- Added a live terrain/road map with a vehicle direction marker.
- Added saved destinations and routing along recorded roads.
- Added two-way/one-way road recording and intersection connections.
- Added next-turn distances, spoken instructions and missed-turn recalculation.
- Added per-world/server/dimension local data with backup recovery.
- Corrected development mixin remapping and Official Pack discovery.

Validation: Java 17 release build, 73 behavioral checks and all 3 headless Minecraft traffic tests passed. Mustang, Mercedes and Scout each drove a recorded bend and stopped at the end; Mercedes also stopped for a real mob and resumed when it was removed. Client traffic controls/networking, visual driver alignment, broader dashboard/route/speech and multiplayer testing remain pending.
