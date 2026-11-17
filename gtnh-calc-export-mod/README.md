# GTNH Calc Export

Client-side Forge mod for Minecraft 1.7.10 and modern GTNH on Java 21.

## Build

Install JDK 25 to run Gradle (required by the pinned GTNH plugin), and set `JAVA_HOME` to it. The mod targets Java 21; Gradle provisions the Java 21 compilation toolchain if needed. Then run:

```bash
./build.sh
```

The script works from any directory and forwards arguments to Gradle. The first build needs internet access to download Gradle and dependencies.
Install `build/libs/gtnh_calc_export-0.1.0.jar` (not a sources or development JAR) into your GTNH client's `mods/` directory. No server installation is required.

## Use

Enter a world and type `/gtnh-calc-export`. It prints exactly `Exporting` in local chat, without operator permissions. Extra arguments show command usage.

This is an initial scaffold. It does not yet generate `data.bin` or `atlas.webp`; the existing C# export process remains available.

The build uses the GTNH convention tooling from [ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10), pinned through the settings plugin and Gradle wrapper.

## Verification

The scaffold builds successfully, and its packaged classes and mod metadata have been inspected. The calculator test suite (33 tests and 29 snapshots) and application build also passed using Node 24; CI targets Node 20.

In-game verification of the scaffold was confirmed on 17 November 2026: `/gtnh-calc-export` prints `Exporting` in chat.

## Export format

See the [version 7 binary and atlas specification](../docs/export-format.md) for the browser contract and requirements for future direct exports.
