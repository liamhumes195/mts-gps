# MTS GPS 0.1.1 - Alpha

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

Validation: Java 17 release build and 49 automated navigation/persistence checks passed. Development startup and test-world loading were verified for the previous build. The 0.1.1 destination-menu correction has been compiled but still requires in-game verification. Broader dashboard, route and speech testing remains pending.
