# GTNH Calc Export

Client-side Forge mod exporting Macerator and Electric Blast Furnace recipes directly to GTNH Calculator's `data.bin` and `atlas.webp`. No NESQL dump or C# processing is needed for this first slice.

Targets **GTNH 2.9.0**, GregTech **5.09.54.265**, NEI **2.8.171-GTNH**, CodeChickenCore **1.4.22**, and Java 21. Other GT-related machines and custom recipe systems are later milestones.

## Build and install

Install JDK 25 to run Gradle (required by the pinned GTNH plugin), and set `JAVA_HOME` to it. The mod targets Java 21; Gradle provisions its compilation toolchain if needed.

```bash
./build.sh
```

The script works from any directory and forwards arguments to Gradle. The first build needs internet access. Install **`build/libs/gtnh_calc_export-0.2.1.jar`** in your GTNH client's `mods/` directory, replacing the old scaffold JAR. Do not install the sources, dev, or preshadow JARs. No server installation is required.

The distributable bundles the WebP encoder and Linux x64 native library; no `cwebp` executable is needed. The upstream encoder also includes Windows x86/x64 and Intel macOS native libraries; those platforms have not been verified here. Its license notices are packaged under `META-INF/licenses/`. The build follows [GTNH's example mod tooling](https://github.com/GTNewHorizons/ExampleMod1.7.10).

## Export

1. Select **English (US)**, enter a world, and wait for NEI's item list to finish loading.
2. Type `/gtnh-calc-export`. Operator privileges are unnecessary; extra arguments show usage. Only one export runs at a time.
3. Watch chat for extraction, rendering, encoding, and completion messages.
4. Find the new timestamped directory in `.minecraft/gtnh-calc-export/`. It contains `data.bin`, `atlas.webp`, `report.txt`, and a `COMPLETE` marker on success.

Both files form a pair. Use only directories with `COMPLETE`; a failed export has `FAILED.txt`, and temporary or partially published files must be ignored. Existing exports are retained. Avoid changing worlds or reloading resources during extraction/rendering. Rendering is spread across frames; individual mod renderers and initial registry setup can still cause brief pauses. Encoding runs in the background.

The report accounts for candidates as exported, disabled/fake, unsupported, or duplicates. It lists source recipe indices, machine associations, chance rounding, and icon failures. Individual failed icons receive magenta checkerboards. Review this report even when the export completes.

## Included data and limits

- Enabled, nonfake recipes in the Macerator and Electric Blast Furnace maps, including hidden recipes.
- Referenced items/fluids, concrete wildcard variants from the unfiltered NEI catalogue, item alternative groups, registered fluid containers, required service icons, and machines advertising these maps.
- Base quantities, nonconsumed catalysts, output probabilities, duration, voltage, amperage, numeric/boolean metadata, EBF heat, reverse recipe links and search masks.
- Stable registry-based IDs and map-qualified content hashes. Canonically ordered NBT determines new tagged-item IDs. **Old NESQL recipe IDs and saved projects are not migrated.**

Version 7 stores whole-percent chances; native GT chances are rounded to this precision. Recipes using probabilistic consumption, distinct alternative fluids, tagged fluids, special-slot requirements, unsupported metadata or other unrepresentable values are skipped with reasons. Nonnumeric metadata is not silently discarded. Registered fluid containers are supported; custom container systems and dynamic machine modes may require adapters. Multiple singleblocks at one tier cannot all occupy the version-7 tier slot; additional associations are reported. Custom multiblocks without calculator rules retain the calculator's existing fallback behavior.

This is a deliberately partial dataset: ingredients normally produced by other machines appear as external inputs. It does not replace the checked-in full data, reproduce all C# filtering/fixes, or export other maps automatically. The existing [C# process](../export/README.md) remains available.

## Atlas reuse

Every current export renders and encodes a fresh `atlas.webp`; there is no skip/reuse command option yet. Icons are assigned from the sorted set of exported goods, so adding/removing referenced items or fluids can shift their positions. Recipe timing/quantity changes alone need not change the atlas, while resource packs, mod updates and animated textures can change its pixels. Reuse is deferred until an option can verify coverage and preserve the previous atlas's item-to-icon mapping. See [export scope](../docs/export-scope.md).

## Preview and verification

From the calculator repository root with Node 20 selected, run the following, substituting your completed export directory:

```bash
npm run build

export_dir="/absolute/path/to/completed/export"
preview_dir=$(mktemp -d /tmp/gtnh-calc-preview.XXXXXX)

test -f "$export_dir/COMPLETE"
cp -a dist/. "$preview_dir/"
cp "$export_dir/data.bin" "$export_dir/atlas.webp" "$preview_dir/data/"
python3 -m http.server 5501 --bind 127.0.0.1 --directory "$preview_dir"
```

Visit `http://localhost:5501` and start a fresh calculator project. Stop the server with Ctrl+C. The checked-in dataset stays unchanged; recipes from other machine maps are absent, so their products appear as external inputs.

Check Macerator and EBF recipes against NEI, including outputs/chances, nitrogen and other fluids, icons, zero-count circuits, machine selection, heat and overclock choices. Inspect `report.txt` for unexpected omissions. The initial 0.2.0 export completed in-game on 17–18 November 2026: 24,855 Macerator and 2,029 EBF recipes, 28,410 items, 276 fluids, and zero placeholder icons. Its complete graph loads with the real calculator reader, and sampled icons from the 8192×3616 atlas render correctly. Two Macerator candidates were skipped (a wildcard without NEI variants and a missing/malformed registry name); 140 normalized duplicates were removed.

That real export exposed incorrect steam machine placement in the electric LV/MV slots. Version 0.2.1 fixes this by checking the machine's electric capability, keeps steam machines in the alternative-crafter list, and places the standard EBF first for automatic selection instead of Volcanus. Unregistered stacks now have an explicit diagnostic rather than a substring error. The 0.2.1 export `20261118-001033-15413749498234349813` was verified on 18 November 2026: LV/MV slots contain Basic/Advanced Macerators, steam machines remain selectable alternatives, and the standard EBF is first in the alternative list. All 26,884 recipes and their referenced records load through the calculator reader; representative real Macerator and EBF recipes solve successfully with the intended automatic machine selections. The atlas decodes at 8192×3616 with zero reported placeholders. The two remaining skipped candidates reference unregistered `net.minecraft.item.ItemDoor` instances (damage 0 and wildcard 32767), now identified explicitly in the report.

`./build.sh` runs 16 Java tests, including native WebP encoding/decoding and a synthetic binary fixture, plus an isolated check of the packaged JAR's encoder on Java 21. The calculator's 39 tests and 29 snapshots pass on Node 20, and its application build passes. The calculator's Jest tests load that Java-generated fixture through the real reader and solve sample production. The fixture is committed at `src/tests/fixtures/direct-export.bin`; its source is `PackWriterTest.fixture()`. After an intentional fixture change, regenerate it through the Java test and copy `build/fixtures/direct-export.bin` to that location, review the changes, and rerun both suites. Java tests compare generated bytes against the committed fixture and fail if it is stale.

See [the version-7 format specification](../docs/export-format.md) for layouts and compatibility requirements.
