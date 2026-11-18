# Calculator datasets

These files are tracked directly in this repository; no data submodule is required. The app and tests use `2.9.0/`.

- `2.9.0/`: GTNH 2.9.0 final, direct exporter 0.3.2, validated run `20261118-124215-1261595546582269294`.
- `2.9.0-beta/`: archived RC-era C# dataset from [ShadowTheAge/gtnh-data](https://github.com/ShadowTheAge/gtnh-data), submodule revision `d200440ea31d0357068a294d7312acf1146de909`.

Each directory contains `data.bin` and its matching `atlas.webp`. See [validation](../docs/export-validation-2.9.0.md) for checks and limitations.

## Important Notes

- **Binary Format**: The exported files are binary and cannot be easily diffed or version-controlled in the traditional sense.
  Keep each binary paired with its matching atlas.

- **Copyright Disclaimer**:
  These data files include:
  - Icons
  - Textual content
  derived from the game **Minecraft** and various mods.

  The content is originally created and owned by:
  - Mojang (Minecraft)
  - The GTNH development team
  - Various mod authors

  The use of this content in this repository is done under the principle of **fair use**, solely for non-commercial, educational, and interoperability purposes with the GTNH Calculator.

- **No License**:
  Due to the inclusion of copyrighted materials, this repository is provided *without a license*.
  I do not claim ownership over any of the content contained in the binary export files.
