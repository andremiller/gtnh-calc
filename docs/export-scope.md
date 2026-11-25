# Export scope and filtering

Both pipelines produce version-7 `data.bin` and `atlas.webp`. The 0.3.2 client mod targets the existing NESQL/C# content and processing rules directly from the game; binary layout is documented in [export-format.md](export-format.md).

## Direct collector scope (0.3.2)

The default `/gtnh-calc-export` command now covers the same source families as the reference collectors, not a two-machine allowlist:

| Source | Collection |
| --- | --- |
| GregTech and addons | Registered GT recipe maps with NEI catalysts, including fuels and informational maps; frontend dimensions, translated names and catalysts |
| Minecraft | Supported shaped/shapeless crafting implementations and furnace recipes |
| Avaritia | Compressor and supported shaped/shapeless extreme crafting implementations |
| Thaumcraft | Crucible, arcane shaped/shapeless crafting, infusion and item aspects |
| Supporting data | Unfiltered NEI items, ore dictionary groups, Forge fluids and containers, and required service items |

Optional mods are read when installed. Adapter classes for optional systems use narrow live-API reflection; obfuscated Minecraft recipe members use the typed Minecraft/Forge APIs. Required adapter initialization failures stop the export. Individual bad/unsupported records are logged, as in the reference collectors. The exporter does not discover arbitrary custom recipe systems beyond this reference scope, and does not claim every recipe in the game is representable.

The pinned reference is [ShadowTheAge/nesql-exporter revision b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57](https://github.com/ShadowTheAge/nesql-exporter/tree/b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57). No NESQL persistence layer, SQL dump or C# runtime is used.

GT collection follows that source's reverse unification, slot maps, integer per-amp voltage and metadata conversions. It intentionally follows the reference's handling of fake/informational records rather than applying the old mod's enabled/nonfake/positive-duration exclusions. The reference does not encode every modern GT input chance, special-slot or alternative-fluid feature into calculator I/O; those limitations are not silently presented as new capabilities.

## C# selection and preprocessing

[PackConverter.cs](../export/PackConverter.cs) has no Macerator/EBF allowlist or general GT-only restriction. It imports all recipe types and recipes supplied by NESQL, subject to the following rules now ported into the client mod:

- [ItemBanlist.cs](../export/ItemBanlist.cs) removes many GT/Tinkers tools and parts, bees/genetic variants, filled Data Orbs and specialized items.
- Banned items are removed from ingredient groups. Recipes with banned item outputs or no surviving variants for an input are rejected.
- Outputs mark items as used. Input groups favor already-used variants, otherwise keeping a representative. They do not retain every possible registry permutation.
- Nonbanned used items, fluid containers and aspect items are retained. All imported fluids remain, including unused fluids.
- GT recipe-type names lose parenthesized suffixes before name-based grouping. The existing grouping can merge names across categories; this behavior is retained for content compatibility.

The direct mod computes raw legacy identities before these operations. Its deterministic group traversal can expose ordering-dependent representative choices in the old database pipeline; the comparison tool reports the resulting differences instead of assuming equal counts prove parity.

[PackPreProcessor.cs](../export/PackPreProcessor.cs) cleans tooltips, copies Empty Cell tooltips to fluids, adds synthetic Thaumcraft item-aspect recipes, and derives search masks, reverse links, container associations and machine choices. Font substitutions, circuit conflicts and Canner/Eye of Harmony layout corrections are also applied. Machine tier classification uses voltage tooltips; steam machines enter the alternative list rather than electric slots. Additional electric crafters at one tier use the C# last-crafter-wins policy. Deprecated machines are excluded from normal choices, but one can remain a fallback.

The standard EBF is placed first among alternatives for correct automatic selection. Rendering is client-thread-only; detached preprocessing and encoding run in a worker. Atlas placement and compressed bytes need not match the old pipeline, provided the binary's icon references match the generated image.

## Export performance

Version 0.3.1 filters recipes before calculating Thaumcraft item aspects, using the existing synthetic-recipe eligibility set rather than all nonbanned catalogue items. It reuses ingredient-group identities, aggregates type counts, and releases completed source structures. Collection uses a 12 ms client-tick budget; rendering remains at 4 ms per render event. All live-game and OpenGL access remains on the client thread, with exclusive graph ownership during detached worker stages. Chat identifies stages, counters, recent throughput, elapsed time and rate-limited slow tasks. The report retains stage timings, task counts, selected/skipped aspect counts and slowest tasks. The completed 0.3.1 and 0.3.2 runs took about 6 min 33 sec and 6 min 24 sec respectively. The user reports a substantial improvement, but no comparable pre-optimization timing is available for a speedup ratio.

Version 0.3.2 adds failure-only registry/stack snapshots and exception traces, plus live-to-binary Research Station numeric checks, through the existing export command. It does not broaden collectors or repair changed-recipe matches. The [completed 0.3.2 run](export-validation-2.9.0.md#completed-diagnostic-run-032) verified all 304 research records with zero mismatches and identified the five GT failures as unregistered source door objects (null registry, numeric ID -1). Their rejection is appropriate; no inferred identity replacement is applied.

## Legacy IDs and optional history

Recipe IDs reproduce the raw NESQL `r~…` algorithm: deterministic length-prefixed protobuf content, Java name-based UUID and URL-safe Base64. Raw ingredient alternatives, quantities, slots, NBT, chances and voltage-qualified GT type names participate. C# goods IDs use registry names, raw damage and the original NBT text. Synthetic aspects use `r:asp:<item-id>`.

`/gtnh-calc-export [previous.bin]` optionally reads version-6/7 history, with relative paths based at `.minecraft` and support for absolute paths/spaces. The normal command has no history dependency. With history supplied, matching follows [OldRecipesGenerator.cs](../export/OldRecipesGenerator.cs): current IDs first, then type/goods/quantities, then type/goods without quantities. Zero-count inputs are omitted. Probability, duration, energy and metadata are absent from these historical fingerprints. The existing `gregtech` namespace fallback and resolvable older aliases are retained.

Ambiguous fingerprints use stable recipe-ID order with last-match-wins and report that choice. Missing recipes are logged, not restored. Current IDs are never overridden by historical aliases. Supplied binaries are read-only, and invalid files are rejected before collection/rendering.

More robust handling of recipe renames, removed items, changing NBT representations, ambiguous matches and pack upgrades remains a documented follow-up. In particular, 0.2.x experimental `r:gt:` IDs and its different group/type rules are not guaranteed to migrate completely through legacy matching.

## Verification status

Automated checks cover storage, selection rules, legacy IDs, historical remapping, non-GT records and packaged JNI encoding. Eleven golden IDs match examples in the checked-in C# dataset across GT, Minecraft, Avaritia and Thaumcraft. Small Java-generated fixtures are loaded and solved by the actual TypeScript calculator. `scripts/compare-exports.py` reads versions 6/7 and reports category/type counts, missing/shared IDs, changed recipe data, metadata, machine associations and goods; its self-comparison of the checked-in dataset has no differences.

Full exports from GTNH 2.9.0 final have passed structural and solver smoke checks, the 0.3.2 source-to-binary research audit, and a user browser check. The latest run contains **225,991 recipes** and verifies all **304 research records** with zero mismatches. Five unregistered source door records are correctly rejected; the runic-augmentation recipe requires item-stack context and remains excluded. Seven icon placeholders are cosmetic limitations. See the [validation record](export-validation-2.9.0.md) for provenance, timings, exclusions and the changed Epoxid recipe that automatic matching cannot migrate.

This evidence supports using the data. Exhaustive record-by-record NEI coverage, a same-version C# parity comparison and equality of every saved-project numeric result have not been established. Run-to-run source ordering/content differences and best-effort aliases remain documented limitations; changed recipes need not match, and no recipe-specific aliases or inferred registry replacements are planned. The checked-in C# dataset remains unchanged.

### Previously verified two-map release

Version 0.2.1 run `20261118-001033-15413749498234349813` produced:

| Map | Candidates | Exported | Unsupported | Duplicates |
| --- | ---: | ---: | ---: | ---: |
| Macerator | 24,993 | 24,855 | 2 | 136 |
| Electric Blast Furnace | 2,033 | 2,029 | 0 | 4 |

The two unsupported candidates referenced unregistered `net.minecraft.item.ItemDoor` instances (damage 0 and wildcard 32767). The export contained 28,410 items, 276 fluids, 44 ingredient groups and 26,884 recipes. Its 8192×3616 atlas had 28,686 icons and zero reported placeholders. The complete graph loaded in the real reader; sample real recipes solved with electric LV/MV Macerators and the standard EBF selected correctly. These results establish that earlier milestone separately from the later full-export validation above.

## Deferred image reuse

Every current direct export renders and encodes a full atlas. No skip/reuse argument exists yet. C# has `--skipIcons`, with a warning that changes to item selection can invalidate icon indices. A future direct reuse option must preserve the old atlas's item-to-icon mapping, verify coverage and make artwork refresh explicit. Copying an arbitrary old atlas next to a newly generated binary is unsupported.

The active calculator dataset is GTNH 2.9.2. See the [2.9.2 validation record](export-validation-2.9.2.md) for current coverage and the [2.9.1 record](export-validation-2.9.1.md) for the previous update.
