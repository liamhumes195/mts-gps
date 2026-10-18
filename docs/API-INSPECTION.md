# Inspection and attachment decision

The supplied Forge MDK targets Minecraft 1.20.1 and Forge 47.4.28. Its wrapper originally selected Gradle 9.7.1; this project pins Gradle 8.8 and ForgeGradle 6.0.54 and compiles with Java 17 (`--release 17`).

The exact binary inputs and their SHA-256 hashes are documented in `libs/README.md`. Both original files in the specified CurseForge instance remain untouched. Inspection extracts and `javap` reports are local in `inspection/`; they are not packaged or committed.

## APIs verified against Immersive Vehicles 24.0.0

* `InterfaceManager.clientInterface.getClientWorld()` exposes the MTS world. `EntityManager.renderableEntities` contains MTS renderable entities, including `EntityVehicleF_Physics`. These are MTS entities; a normal Forge living-entity render event is not the right attachment hook.
* `IInterfaceClient.getClientPlayer()` and `IWrapperEntity.getEntityRiding()` resolve the actual MTS `PartSeat`. `APart.vehicleOn` resolves its car; `placementDefinition.isController` distinguishes the driver. Recording uses the car's position, not the player's head position.
* `AEntityD_Definable.definition.packID` and `.systemName` identify `mtsofficialpack:merc230`. Matching does not depend on `.subDefinition` or its paint suffix, so it includes all 17 subdefinitions in V29.
* `AEntityB_Existing.prevPosition`, `.position`, and `getInterpolatedOrientation(RotationMatrix,double)` give the interpolated chassis transform. `AEntityC_Renderable.prevScale`/`.scale` supply scale. Bytecode inspection of `AEntityC_Renderable.render(boolean,float)` verified that ordering.
* `AEntityD_Definable.animatedObjectSwitchboxes.get("body")` is the existing Mercedes body animation. `AnimationSwitchbox.runSwitchbox(float,boolean)` evaluates it; `.netMatrix` contains its local transform. The screen follows this matrix after chassis scale, preserving V29's animated body lean.
* MTS mathematical matrix field names are row/column; JOML field names are column/row. `MtsBridge` converts them explicitly. The behavioral suite compares rotation, scale and translation results with MTS's own `Point3D` transformations.
* `RenderInstrument`, `JSONInstrument`, and `JSONInstrumentComponent` implement the pack's declarative textured/animated instrument rendering. This addon does not depend on a hypothetical instrument map callback or replace pack instruments.

MTS's Forge adapter registers a `BuilderEntityRenderForwarder` renderer and renders its core entities through `InterfaceRender.doRenderCall(boolean,float)`. Its own `LevelRendererMixin` also performs world passes. The addon needs no mixin or modification to either original JAR: it submits its own casing and dynamic screen mesh during Forge `RenderLevelStageEvent.Stage.AFTER_PARTICLES` with normal depth testing.

The actual Forge 47.4.28 sources were also checked: its `GameRenderer` dispatches `AFTER_LEVEL` with the projection pose stack. Using that as a world-view stack would misplace the GPS. `AFTER_PARTICLES` supplies the world pose and retains MTS's camera orientation. The screen never uses a HUD rendering event, billboarding, or disabled depth testing.

The control menu displays the same map texture for selecting destinations; it is supplementary to the physical dashboard screen.

## Development pack discovery

Bytecode inspection of the exact 24.0.0 `InterfaceLoader.init` shows that it calls `PackParser.parsePacks` with two physical directories: the game's `mods` folder and the parent directory of MTS's own loaded JAR. It does not enumerate every Forge mod's classpath JAR. `PackParser.parsePacks` calls `File.listFiles` and examines JARs in those directories.

ForgeGradle places deobfuscated dependencies in separate artifact cache directories. Consequently, putting the Official Pack only on `runtimeOnly fg.deobf(...)` made its resources visible to Forge but left its vehicle/item definitions undiscovered by MTS. The development launch now stages the user's exact original V29 JAR, unchanged, into `run/mods`; the duplicate runtime classpath dependency has been removed. This does not affect normal installation, where both original JARs already live together in `mods`.

## Mercedes model coordinates

From `assets/mtsofficialpack/jsondefs/vehicles/merc230.json` and `objmodels/vehicles/merc230.obj`:

| Feature | Vehicle-local coordinates, blocks |
| --- | --- |
| Driver seat | `(0.5, 0.1875, 1.5)` |
| Steering wheel pivot | approximately `(0.5047, 0.5625, 2.1188)` |
| Gear shifter | around `(0, 0.1875–0.4375, 2.0)` |
| Instrument slots | around `x=0.5–0.672, y=0.64–0.656, z=2.340625` |
| Dashboard upper geometry | vertices around `y=0.8125, z=2.28125` |
| Body animation pivot | `(0, 0.0625, 1.25)` |
| Initial GPS center | `(0.12, 0.94, 2.27)` |

Vehicle forward is `+Z`; smaller X places the screen to the driver's right. The default screen is 0.38 × 0.25 blocks, yaw −20°, pitch +12°. Its face points toward local −Z and toward the driver's positive-X side. A small housing and support are rendered behind/below it.

These defaults are inferred from the actual model, **not visually calibrated in Minecraft**. All offsets, dimensions, yaw, pitch, and roll are editable through the mount menu or `config/mtsgps-client.toml`. The test grid uses the same mesh and transforms as the real GPS.

Upstream reference: [MTS source repository](https://github.com/DonBruce64/MinecraftTransportSimulator). The supplied binary, rather than current upstream master, determines the APIs used here.
