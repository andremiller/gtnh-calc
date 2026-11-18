# Direct export sanity check: GTNH 2.9.0 final

## Current outcome

Version **0.3.2** is suitable for use with GTNH **2.9.0 final**, based on completed structural/solver checks, source-to-binary diagnostics and the user's successful browser check. The latest run has **225,991 recipes**, verifies all **304 research records** without mismatches and correctly excludes five unregistered source door records. Seven icon placeholders and unsupported dynamic recipes remain limitations. No further exporter repair is required for the investigated registry and research concerns.

The sections below distinguish the initial 0.3.1 sanity check from its 0.3.2 diagnostic follow-up. Exhaustive NEI coverage and same-version C# parity are not claimed. Both source pairs are preserved unchanged in the versioned directories.

## Provenance and limits

Initial sanity-check candidate: exporter **0.3.1**, GTNH **2.9.0 final**, GregTech **5.09.54.265**, NEI **2.8.171-GTNH**, run `20261118-121304-10035753893549066714` on 2026-11-18. The original files remain in the user's Prism instance; the validated pair is now tracked in `data/2.9.0/`.

Reference: the archived C# dataset in `data/2.9.0-beta/`, built from a GTNH 2.9 release candidate according to the user. It is not a same-version baseline, so this comparison cannot attribute every difference to either a pack change or exporter behavior.

The candidate is structurally sound and usable for local preview, with exclusions and compatibility gaps listed below. This is a sanity check, not full NEI-by-NEI acceptance or proof of parity with a C# export of 2.9.0 final.

## Initial 0.3.1 content comparison

| Records | C# reference | Direct candidate |
| --- | ---: | ---: |
| Total recipes | 221,760 | 225,991 |
| GregTech category | 147,502 | 148,766 |
| Minecraft category | 35,581 | 37,645 |
| Thaumcraft category | 37,405 | 38,303 |
| Avaritia category | 1,272 | 1,277 |
| Items | 50,811 | 51,019 |
| Fluids | 1,476 | 1,475 |
| Historical aliases | 21,282 | 22,982 |

216,195 recipe IDs are shared exactly (97.49% of reference recipes). There are 5,565 reference-only IDs and 9,796 candidate-only IDs. Including aliases, 219,783 reference recipes resolve in the candidate (99.11%); 1,977 do not. The report records 3,445 quantity-sensitive matches, 143 quantity-free matches, 19,394 inherited aliases and 4,005 ambiguous fingerprint collisions. Historical matches are heuristic, so resolution alone does not prove the selected recipe is equivalent.

Raw comparison exaggerates some differences: shared recipes include their machine-type records, and the reference lists electric machines as alternatives while the direct exporter supplies voltage-indexed slots. Standard EBF alternatives and preferred EBF match exactly. Of 18,464 shared recipes with different I/O records, 17,134 differ only in slot/order after normalization; 1,330 have different goods/quantities/chances, including 960 synthetic aspect recipes. Alternatives and aspect contents can depend on pack and player state.

Examples consistent with version changes include 105 Ice Cream Machine recipes, Coke Oven/Industrial Coke Oven splitting, renamed Naquadah reactor types, new wire/circuit machinery, changed material names and 162 fewer Circuit Assembler recipes. These are observations, not independently verified pack changelog claims. The sole reference-only fluid is `f:gregtech:potion.saltywater`.

## Checks performed on the 0.3.1 export

- Confirmed `COMPLETE` and both final artifacts exist; parsed the gzip version-7 binary with the read-only comparison tool.
- Compiled the actual TypeScript reader/solver into an isolated temporary directory; loaded all **225,991 recipes and 819,736 I/O entries** through the real reader.
- Reconstructed expected production/consumption links and checked every goods record: **zero mismatches**. Checked **1,555** item-container records and reciprocal fluid-container indices.
- Decoded the WebP: **8192 × 6592**. All **52,494** binary icon indices are unique and contiguous from 0 to 52,493 and fit the atlas. Visually inspected sampled Macerator, EBF, iron ingot, water, Ignis, Alchemical Furnace, Quest Book and anvil tiles.
- Loaded and ran the real solver on all **29** committed `.gtnh` scenarios. All runs returned `solved`, but only **28 retain every referenced recipe**; a solved status alone must not be treated as proof that an old project migrated completely.
- Did not update scenario snapshots, compare all numeric solver results, inspect the full browser UI, or verify each record against NEI.

`Epoxid.gtnh` references unresolved recipe `r~GGFsnPVuObGs6ap0fkUTWQ==`. The candidate has a closely corresponding Large Chemical Reactor recipe, `r~QjYoyAptPNCFKvAHPqbVbw==`, with the same inputs and the old outputs **plus 2,000 mB diluted hydrochloric acid**. The extra product prevents the historical fingerprint match. This scenario needs manual recipe replacement before its result can be trusted.

## Initial exclusions and their diagnostic outcomes

The report records **2,806 collection errors/unsupported records**, not 2,806 missing calculator recipes:

- **2,743** unsupported custom crafting implementations, including 2,304 `ScriptEFR$ShulkerNBTRecipe`, 142 AE2 shaped recipes and 61 shaped Blood Orb recipes. One additional crafting record has no static output. The direct collector deliberately follows the reference collector's four supported vanilla/Forge crafting classes; these exclusions are not newly established regressions against C#.
- **50** unsupported infusion-enchantment records and seven other unsupported/dynamic Thaumcraft records, including one `InfusionRunicAugmentRecipe` reflection failure. The initial report lacked the underlying exception; the completed 0.3.2 run below identifies a null-stack aspect lookup in this dynamic recipe.
- **Five GT source records fail on an unregistered item/fluid identity**: Arc Furnace #1148/#2494, Fluid Extractor #3450, Macerator #14107/#15926. Source indices identify this run only. The completed 0.3.2 diagnostics below identify all five as unregistered source door objects, appropriately rejected.
- The Water Bottle container records **0 mB** of water in both the reference and candidate; this is an inherited data quirk, not a newly introduced difference.
- Two Forestry pollen/sapling variants produce tooltip/name warnings because their species are missing.
- **Seven icon placeholders**: Galacticraft fuel/oil, GT++ sludge, Witchery brew gas/liquid, wildcard Tainted Magic katana and wildcard Thaumcraft golem placer. Other sampled icons look correct.

Some research-station numeric metadata and special values differ substantially; for example a shared recipe has metadata 2,147,418,144 in the reference versus 8,388,608,032 in the candidate, with a negative signed special value. The subsequent 0.3.2 read-back audit verifies agreement with all 304 live research records and explains the packed representation, closing the suspected serialization issue. Several frontend dimensions also differ (including fluid fuel maps and Target Chamber). The user subsequently reported a successful browser check; no exhaustive per-layout verification was recorded.

## Performance from the 0.3.1 run

| Stage | Seconds |
| --- | ---: |
| Source collection (466,810 tasks) | 24.4 |
| Recipe filtering | 0.8 |
| Item aspects (51,019 selected; 8,679 skipped) | 230.0 |
| Final preprocessing | 13.0 |
| Historical matching | 0.4 |
| Binary serialization | 9.4 |
| Icon rendering | 98.2 |
| WebP/file writing | 16.7 |
| Total of reported stages | 392.9 |

Approximately **6 min 33 sec**. Aspects and rendering dominate. The user reports a substantial improvement over the preceding version, but no comparable completed pre-optimization timing was available to calculate a speedup ratio.

## Diagnostic follow-up (0.3.2)

The user's browser check looked good. Changed recipes such as Epoxid should remain subject to general automatic matching; no recipe-specific alias is planned. Inspection of the installed GT frontend confirms that Research Station data is a packed 64-bit value, with the upper field obtained by unsigned shift by 16 and the lower field cast to a signed short. The example 8,388,608,032 decodes to upper=128,000, lower=32; its legacy signed-int representation is -201,326,560. The reference value decodes to upper=32,767, lower=32. This explains why a negative legacy special value alone is not evidence of corrupt export data.

Version 0.3.2 logs original numeric types/values and independently checks generated research records against detached source snapshots during the normal export. It also captures detailed raw GT failure stacks and reflected exception causes. The subsequent live export described below confirmed the research values and identified the unregistered source objects. Existing recipe selection, binary layout and completion policy are unchanged; diagnostic mismatches are warnings requiring report review.

## Completed diagnostic run: 0.3.2

Run `20261118-124215-1261595546582269294` completed on 2026-11-18 with `COMPLETE`, 225,991 recipes, 51,019 items, 1,475 fluids and seven icon placeholders. Every category count matches the preceding 0.3.1 run. Reported stages total **384.0 seconds (6 min 24 sec)**.

**Research verification passes:** all **304** Research Station recipes were exported and read back successfully, with **zero mismatches/errors and zero excluded IDs**. The serialized numeric metadata, special values, voltage, amperage and duration agree with the detached live source under the existing metadata cleanup rules. This closes the suspected source-to-binary corruption issue; it does not add new calculator semantics for packed research data.

**All five GT failures are unregistered source door objects:** the failing original input, its unified stack and its only reverse-unified alternative are `net.minecraft.item.ItemDoor`, with raw registry name `null` and numeric ID **-1**. Three recipes use damage 0; two use wildcard damage 32767. The affected maps are Arc Furnace (two records), Fluid Extractor (one) and Macerator (two). Outputs include registered iron-related products; the invalid object is the input. These are not successful registry lookups that the exporter then misidentifies. Rejecting them is appropriate; inventing a replacement identity or choosing a registered door by class would be unsafe. No recipe-specific repair is required.

**The reflected Thaumcraft failure is identified:** `InfusionRunicAugmentRecipe.getAspects()` reaches `EventHandlerRunic.getFinalCharge()` with a null item stack. The exporter invokes the no-argument aspect API, as the reference collector does. This dynamic/context-dependent recipe cannot supply a static aspect list through that call and remains excluded. It does not invalidate ordinary infusion records or the rest of the dataset.

Comparison with 0.3.1 finds identical goods identities and category counts, 492 goods records with tooltip differences, 131 replaced recipe IDs, and 706 changed shared recipes. Of the replaced IDs, 112 have equivalent full content after ignoring I/O slots/order (Eye of Harmony and centrifuge maps); the remaining 19 belong to Crop Breeder (17) and Arcane Infusion (2). Of the changed shared recipes, 667 differ only in slots/order; the remaining 39 are item-aspect contents. No shared recipe's GT numeric metadata/timing/conflict fields changed. The remaining content differences have not been individually attributed; this is not a byte-for-byte deterministic export across launches. Raw legacy IDs include slots, so source ordering differences can change identities. The supplied history was the C# reference, not the previous direct export, and therefore does not guarantee aliases for those 131 prior direct-export IDs.

The diagnostic run supports using this data, together with the user's successful browser check. Remaining documented exclusions and icon placeholders are limitations, rather than evidence of a broken binary or lookup bug. The five invalid source records and successful research audit no longer block adoption. No hardcoded recipe mappings, registry replacements or checked-in dataset changes were made.

## Versioned repository storage

The validated 0.3.2 binary/atlas pair is now tracked directly in `data/2.9.0/`, used by the app and tests. The previous submodule pair is preserved in `data/2.9.0-beta/`. Both pairs were verified against their source hashes; earlier statements about not replacing the dataset describe the validation work before this explicitly requested migration. The data submodule has been removed.

## Calculator integration corrections after activating 2.9.0

Switching the automated tests to the new active dataset exposed 12 snapshot failures; the earlier successful solver smoke runs did not compare numeric expectations. Machine dispatch now resolves existing rules by stable item ID, preserving custom behavior for renamed Fusion machines. This is current calculation correctness, not a recipe migration feature.

Scenario maintenance updates the CBD nitric-acid namespace and Epoxid recipe selection, and clears obsolete machine 797 selections in the ore-processing and UHV naquadah projects so current defaults apply. Snapshots were reviewed after restoring machine rules: remaining differences reflect current singleblock classification/default selection, timing/overclock behavior and the explicit Epoxid reference update. No dataset binaries or exporter matching rules were changed. The final suite passes 45 tests and 29 snapshots; the build passes. These automated checks do not replace in-game verification of every machine formula.

Future changes follow [the dataset update workflow](updating-datasets.md), using the shared active version configuration, machine coverage checks and unresolved-reference assertions before accepting snapshot updates.
