# GTNH Data Export Process

This document explains the existing NESQL/C# process for the GTNH Calculator. The [client mod](../gtnh-calc-export-mod/README.md) now targets equivalent collectors and processing directly on GTNH 2.9.0; its full-source release still needs in-game acceptance. See [export scope and filtering](../docs/export-scope.md) for how the two pipelines select data.

## Prerequisites

- Java Development Kit (modern versions might not work, jdk 8 can be downloaded [here](https://www.openlogic.com/openjdk-downloads) for example, or otherwise refer to [GTNH development](https://gtnh.miraheze.org/wiki/Development))
- .NET SDK 8.0 or later
- Minecraft with GTNH modpack installed

## Step 1: Export Data from Minecraft

1. Build the NESQL Exporter mod from [ShadowTheAge/nesql-exporter](https://github.com/ShadowTheAge/nesql-exporter) fork:
   ```bash
   ./gradlew build
   ```
2. Follow the steps from readme there, including removing bugtorch.
3. Wait for the export to complete. The exported data will be saved in `.minecraft/nesql`

## Step 2: Process Exported Data

1. Navigate to the export project directory
2. Run the C# project:
   ```bash
   dotnet run <path to nesql export directory> [--output <path>]
   ```
   Arguments:
   - `<path to nesql export directory>`: Required. Path to the directory containing the NESQL export
   - `--output <path>`: Path to the data directory (if skipped, generated files will be put in the current directory)

3. The project will process the exported NESQL data and generate:
   - `atlas.webp`: A texture atlas containing all item icons
   - `data.bin`: A binary file containing processed recipe and item data

## Previous-export compatibility

Pass `--previous /path/to/old-data.bin` to populate the new binary's recipe-remap table. This matches current recipe-type names and ordered goods/quantities first, then ignores quantities, carries forward resolvable aliases, and reports unresolved old recipes. It does not restore removed recipes. The direct mod exposes the corresponding optional argument as `/gtnh-calc-export previous.bin`.
