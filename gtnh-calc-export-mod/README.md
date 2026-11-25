# GTNH Calc Export

Client-side Forge exporter targeting the same content and preprocessing as the NESQL → C# → calculator pipeline. It reads the running game directly and writes version-7 `data.bin` and `atlas.webp`, with an optional previous binary for historical recipe aliases. No database or NESQL/C# runtime is required.

Version **0.3.2** targets **GTNH 2.9.0**, GregTech **5.09.54.265**, NEI **2.8.171-GTNH**, CodeChickenCore **1.4.22**, and Java 21. Full exports passed [structural and solver smoke checks, 0.3.2 diagnostics and a user browser check](../docs/export-validation-2.9.0.md). The latest run contains 225,991 recipes and verifies all 304 Research Station records with zero mismatches. Five invalid source door records are correctly excluded; seven icon placeholders and unsupported dynamic recipes remain documented limitations. Exhaustive NEI coverage is not claimed. The earlier two-map 0.2.1 exporter was verified in-game; those results are retained in [export scope](../docs/export-scope.md).

## Build and install

Set `JAVA_HOME` to JDK 25 for Gradle. Compilation targets Java 21; Gradle provisions the compilation toolchain if needed.

```bash
./build.sh
```

The executable script resolves its own directory and forwards additional arguments to Gradle. Install **`build/libs/gtnh_calc_export-0.3.2.jar`** in the client's `mods/` directory, replacing the previous exporter JAR. Do not install sources, dev or preshadow JARs. No server installation is required.

The distributable bundles a WebP encoder and Linux x64 native library; no `cwebp` executable is needed. Other native libraries included upstream are unverified here. JNI class names and native resource paths must not be relocated or minimized. Every build checks the actual packaged encoder in an isolated Java 21 classloader.

## Export commands

```text
/gtnh-calc-export
/gtnh-calc-export previous.bin
/gtnh-calc-export gtnh-calc-export/<previous-run>/data.bin
/gtnh-calc-export "/absolute/path/with spaces/data.bin"
```

The optional argument is a file path, not a flag. Relative paths resolve against the instance's `.minecraft` directory; absolute paths and spaces are supported. Previous input is optional. Its gzip/word buffer is checked before collection or rendering; supported input versions are 6 and 7. Missing, corrupt, unreadable and unsupported files fail clearly. The supplied file is never modified.

Select **English (US)**, enter a world and wait for NEI item loading. Use a character/player state appropriate for comparison with the reference exporter: tooltips and Thaumcraft aspect calculations can depend on the loaded pack and player state. The exporter does not change the player's knowledge or world.

Only one export runs at a time. Collection uses a 12 ms budget per client tick; rendering retains a 4 ms budget per render event. This trades some game responsiveness for collection throughput; individual mod calls can exceed either budget. Source collection finishes before detached recipe filtering, then only retained items receive client-thread Thaumcraft aspect calculations. Final preprocessing, previous-recipe matching, serialization and WebP encoding run in the background. The client never accesses the graph while a preprocessing worker owns it. Avoid changing worlds or reloading resources during collection/rendering.

Chat messages start with `[GTNH Calc Export]` and announce each stage. Progress appears roughly every two seconds with completed/total tasks, recent tasks per second and stage elapsed time. The source total grows when adapters enqueue recipes; the aspect and icon totals are fixed. Background stages show elapsed time. Aspect selection reports how many candidates were selected/skipped. Slow tasks exceeding 250 ms are reported after returning, with chat warnings limited to one per ten seconds; every slow-task timing and the ten slowest tasks are retained in `report.txt`, along with stage timings and collection task counts. A single game API call cannot be safely interrupted, so it can temporarily delay chat updates.

Version 0.3.1 also reuses raw ingredient-group identities and aggregates recipe counts once during final preprocessing. Its binary contract and command arguments are unchanged. The first full 0.3.1 run completed in about 6 minutes 33 seconds; the 0.3.2 run completed in about 6 minutes 24 seconds. Replace the old JAR and restart Minecraft to use these improvements; an already running export continues with the old code.

For a Flatpak-installed launcher, an absolute host path may be invisible to Minecraft. Copy the previous binary into the instance's `.minecraft` directory and use `/gtnh-calc-export previous.bin` instead of granting broad filesystem access.

Each run creates a fresh directory under `.minecraft/gtnh-calc-export/` containing:

- `data.bin` and `atlas.webp`: the matched calculator pair.
- `report.txt`: source maps/types, collection errors, filtering, duplicates, alias statistics and icon failures.
- `COMPLETE`: written only after both final artifacts are ready.

Failed runs have `FAILED.txt`; interrupted runs may have temporary or incomplete files without `COMPLETE`. Use only completed directories. Individual failed icons receive checkerboards and are counted in the report.

## Failure and research diagnostics (0.3.2)

Run the normal `/gtnh-calc-export [previous.bin]` command; no diagnostic flag or separate command is needed. Replace the JAR and restart Minecraft first. The next run's `report.txt` includes:

- `DIAGNOSTIC GT failure`: map and failing operation/slot, followed by raw item/fluid registry identities, classes, numeric IDs, quantities, damage/NBT, outputs and reverse-unified alternatives. These snapshots are gathered only on failures, without tooltips or rendering. The source map/index in the existing `UNSUPPORTED` line is specific to that run.
- `FAILURE`: full exception traces, including reflected causes, for unexpected collection failures; these also reach the client log. Known unsupported reference crafting implementations remain concise. Icon failures also retain full traces. A failed diagnostic field is marked unavailable and does not replace the original collection error.
- `RESEARCH source`, `DIAGNOSTIC RESEARCH` and `RESEARCH decoded`: live Research Station recipe identities, identifiable raw inputs/outputs, numeric metadata types/values, duration/EU/t, the packed value's upper field and signed-short lower field, and signed legacy special value.
- `RESEARCH read-back OK` or `RESEARCH MISMATCH`: after serialization, the generated gzip binary is independently read back and compared with detached source snapshots for metadata, special value, voltage, amperage and duration. Expected metadata observes the existing C# cleanup rules. Filtered/unexported source IDs are reported separately. The summary counts checked records, mismatches/read-back errors and unexported IDs.

Chat shows a compact diagnostic summary before rendering and on completion, with a warning when research checks report mismatches/errors. Inspect the detailed report before accepting an export with a warning. Diagnostics preserve the existing export completion/failure policy, selection, matching and binary layout; a diagnostic mismatch alone does not suppress `COMPLETE`. These checks prove source-to-binary agreement, not full calculator support for the meaning of each metadata field. A completed [0.3.2 diagnostic run](../docs/export-validation-2.9.0.md#completed-diagnostic-run-032) verified all 304 Research Station recipes with zero mismatches and identified all five GT failures as unregistered source door objects. The remaining runic-infusion reflection failure is a null-stack aspect lookup.

## Scope and compatibility

Collectors cover GT maps with NEI catalysts (including addons, fuels and informational recipes), ordinary crafting/furnace recipes, Avaritia compressor/extreme crafting, Thaumcraft crafting/infusion/crucible recipes and synthetic item-aspect recipes. Optional mods are inspected when installed. A missing required adapter API fails the export rather than silently publishing an absent category; individual unsupported records are reported as in the reference collector.

The selection stage follows C# item bans, alternative pruning, used-item retention, all-fluid retention, tooltip/font fixes, layouts, containers and circuit conflicts. GT collection follows the reference's reverse unification and metadata rules instead of the 0.2.x blanket exclusions. Legacy behavior also retains its limitations: nonnumeric GT metadata and some GT special-slot/chance/alternative details are not represented by the reference pipeline. See [export scope](../docs/export-scope.md) for details.

Legacy `r~…` IDs are generated from the original raw recipe inputs before C# pruning and display-name normalization. Item IDs use the C# registry/damage/NBT-text rules. Synthetic aspect recipes retain `r:asp:<item-id>`. Generation is deterministic for the same raw identity inputs; it does not preserve identities across arbitrary recipe changes.

With a previous binary, historical matching follows `OldRecipesGenerator`: unchanged IDs remain authoritative; missing IDs are matched by recipe-type name and ordered goods/amounts, then without amounts. Zero-count inputs are ignored. The existing `gregtech` namespace fallback and resolvable inherited aliases are carried forward. Ambiguous fingerprints use stable recipe-ID order with last-match-wins and are reported. Removed recipes are not restored. The resulting aliases are serialized in the existing remap table so the calculator can load old project references.

Historical matching remains best effort. Changed recipes may legitimately remain unmatched; do not add recipe-specific aliases or infer registry replacements to force migration. Renamed recipe types, substantially changed ingredients, missing items and ambiguities can leave projects unresolved. Experimental 0.2.x recipe IDs/grouping differ from legacy C# output and are not guaranteed to migrate completely. More robust pack-upgrade identity tracking is a documented follow-up; the optional previous-file workflow is the current compatibility mechanism.

## Atlas generation

Every run renders 64px icons, box-filters them to 32px and encodes a complete lossless `atlas.webp`, with 256 columns. There is no skip/reuse argument yet. Goods selection can shift icon indices, and resource packs/mods/animation state can change artwork. Keep each atlas with its matching binary.

## Local preview

From the calculator repository root, with Node 20 selected:

```bash
npm run build
export_dir="/absolute/path/to/completed/export"
version=$(sed -n 's/^export const datasetVersion = "\([^"]*\)";$/\1/p' src/dataset.ts)
preview_dir=$(mktemp -d /tmp/gtnh-calc-preview.XXXXXX)
test -n "$version" && test -f "$export_dir/COMPLETE" && \
    cp -a dist/. "$preview_dir/" && \
    cp "$export_dir/data.bin" "$export_dir/atlas.webp" "$preview_dir/data/$version/" && \
    python3 -m http.server 5501 --bind 127.0.0.1 --directory "$preview_dir"
```

Visit `http://127.0.0.1:5501` in a fresh browser context. This preview serves the candidate pair at the configured dataset path, so the version label still comes from the build's `src/dataset.ts`. Check the label again after updating the configured version and rebuilding for activation. Test fresh projects and representative existing projects, then stop the server with Ctrl+C. See the [dataset update runbook](../docs/updating-datasets.md#checking-the-production-build-in-a-browser) for browser checks.

Compare a candidate against the checked-in reference or another export:

```bash
python3 scripts/compare-exports.py data/2.9.0-beta /path/to/new/export --samples 10
```

This read-only tool reports category/type counts, missing/shared identities, changed recipe fields, metadata, machine associations, goods and alias counts as JSON. Counts alone are not acceptance criteria: pack changes and reference-player state can cause legitimate differences.

## Tests and acceptance

The mod build runs Java regression tests and packaged JNI encoding checks. Eleven golden legacy IDs are extracted from the checked-in C# dataset across every supported category. The Java writer produces two small committed fixtures consumed by the actual TypeScript reader and solver, including shaped/non-GT recipes, aspects and historical aliases. To change a fixture intentionally, review the generated `build/fixtures/` file, copy it to `src/tests/fixtures/` at the repository root, then rerun both suites. A stale fixture causes the Java test to fail after writing its new candidate.

Run calculator tests/build using Node 20, and comparison-tool tests with `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v`.

Completed validation includes real full exports, comparison with the RC-era C# dataset, structural and solver smoke checks, sampled icon inspection, a successful user browser check and the 304-record research read-back audit. The five rejected GT door records have null registry names and item ID -1, so no exporter lookup repair is required. An old Epoxid project needs manual recipe selection because the game recipe gained an output; no hardcoded migration is planned. Exhaustive record-by-record NEI verification and a same-version C# parity comparison have not been performed. See the validation record for exact per-run checks, exclusions and run-to-run differences.

## Source and licensing

Compatibility collectors and identity encoding reference [ShadowTheAge/nesql-exporter](https://github.com/ShadowTheAge/nesql-exporter/tree/b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57), revision `b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57`. Adapted source files and reference test schemas are LGPL-3.0; their headers identify them. C#-derived selection rules and the remaining original repository code retain MIT licensing. Bundled library notices and LGPL/GPL texts are packaged under `META-INF/licenses/`. Build tooling follows [GTNH's example mod](https://github.com/GTNewHorizons/ExampleMod1.7.10).

See [the version-7 format specification](../docs/export-format.md) for binary layouts.
