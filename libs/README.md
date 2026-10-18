# Local development dependencies

Keep licensed input JARs local. They are excluded from source control and from the addon JAR.

Copy the original files with these filenames (renaming a copy does not change its bytes):

| Original | Local filename | SHA-256 |
| --- | --- | --- |
| Immersive Vehicles-1.20.1-24.0.0.jar | mts-24.0.0.jar | 274c68a6b6cf9bd5fd97af8358ce2d58fed2a49685f20079d744e8b74800277b |
| MTS Official Pack-1.20.1-V29.jar | mtsofficialpack-29.jar | a6ca5d37a89fd398cafd96fc8f87673cec5b0247c1651bc0b9f02b23f86c7908 |

These inputs were inspected from the supplied CurseForge instance. Obtain your own copies of the original mods; they are not distributed with this repository.
ForgeGradle deobfuscates the MTS core copy in its cache for development. Never install that cached development copy in a normal Minecraft instance.

The Official Pack is staged unchanged into `run/mods` before each development launch so MTS's own physical-file scan can discover its JSON definitions. To use a source JAR from an existing instance, put `official_pack_jar=C:/path/to/MTS Official Pack-1.20.1-V29.jar` in an ignored `gradle-local.properties` file at the project root, or pass `-Pofficial_pack_jar=...`. The local `mtsofficialpack-29.jar` copy is the default when no override is set. The pack is not also added as a Gradle runtime classpath dependency, preventing duplicate mod IDs.
