# Export scope and filtering

Both pipelines produce version-7 `data.bin` and `atlas.webp` files. Sharing the [binary format](export-format.md) does not imply identical item or recipe coverage.

## Current direct exporter: 0.2.1

The [client mod](../gtnh-calc-export-mod/README.md) targets GTNH 2.9.0 and directly reads two GregTech recipe maps: Macerator and Electric Blast Furnace. All other maps, crafting-table recipes and custom recipe systems are outside the current milestone. Broader GT-related machine coverage remains a future stage.

The mod exports enabled, nonfake recipes, including hidden ones, then retains the items and fluids needed for those recipes, ingredient alternatives, associated machines, registered containers and required UI service items. It does not export the entire item registry. Macerator recycling recipes account for much of the large item set.

Recipe instances with identical normalized content are deduplicated. Unsupported requirements are reported and skipped: these include probabilistic consumption, distinct fluid alternatives, tagged fluids, special-slot requirements, unrepresentable metadata and missing registry identities. Item wildcard alternatives come from the unfiltered NEI catalogue. It does not currently apply the C# item banlist or prune alternatives using the C# representative-selection rule.

The verified 0.2.1 run `20261118-001033-15413749498234349813` produced:

| Map | Candidates | Exported | Unsupported | Duplicates |
| --- | ---: | ---: | ---: | ---: |
| Macerator | 24,993 | 24,855 | 2 | 136 |
| Electric Blast Furnace | 2,033 | 2,029 | 0 | 4 |

There were no disabled/fake exclusions. The two unsupported candidates referenced unregistered `net.minecraft.item.ItemDoor` instances, one with damage 0 and one with wildcard damage 32767. These are observations from this pack/run, not fixed expected counts for future versions.

The pair contains 28,410 items, 276 fluids, 44 ingredient groups and 26,884 recipes. Its atlas is 8192×3616 with 28,686 assigned icons and zero reported placeholders. The entire graph was loaded with the real calculator reader, and representative real Macerator and EBF recipes solved with the intended automatic machine selections. The prior run's icons were visually sampled; the corrected run's atlas was decoded and its machine associations checked. See the mod README for build, local preview and verification details.

## Existing NESQL/C# pipeline

The C# converter has no Macerator/EBF allowlist and no general restriction to GregTech recipes. It starts with the recipe types and recipes already present in the NESQL export. Any selection or omissions made inside NESQL are a separate layer; this does not guarantee every recipe in the game is represented.

[PackConverter.cs](../export/PackConverter.cs) applies these rules:

- Import recipe types and associate recipes with them. For GregTech categories, remove parenthesized suffixes before grouping by type name; this groups types rather than filtering them out.
- Apply [ItemBanlist.cs](../export/ItemBanlist.cs) to item records. Examples include many GT/Tinkers tools and tool parts, bees/genetic variants, filled Data Orbs and certain specialized items.
- Remove banned items from ingredient alternatives. Reject a recipe if an item output is banned or an input group has no surviving variants.
- Mark recipe outputs as used. For each used ingredient group, retain variants already marked as used; if none is marked, select a representative. Consequently, the output need not contain every possible ingredient variant from NESQL.
- Retain nonbanned items marked as used, plus fluid-container and aspect items. Retain all imported fluids. This is broader than the new mod's referenced-fluid selection.

Machine associations come from NESQL's `RECIPE_TYPE_ITEM` records. [PackPreProcessor.cs](../export/PackPreProcessor.cs) classifies imported crafters using voltage information in tooltips: electric singleblocks occupy tier slots, and other machines enter the alternative-crafter list. Deprecated machines are excluded from normal choices, although one can remain as a fallback when no other crafter exists. This associates machines with recipe types rather than restricting the global recipe scope.

Preprocessing also adds synthetic Thaumcraft item-aspect recipes and derives reverse recipe links, container associations and search data. [PackGenerator.cs](../export/PackGenerator.cs) then applies layout/font corrections and circuit-conflict calculation. With a previous binary supplied, it can add historical recipe-ID remaps. These behaviors are not all reproduced by the direct exporter.

## Image generation and deferred work

The direct mod currently renders and encodes a complete atlas on every export. There is no supported `--reuse-icons` or skip-icons argument. The C# tool does have `--skipIcons`, with an explicit warning that changing item selection can invalidate the icon indices.

Recipe parameters such as amounts or duration do not inherently change an icon. Changing the exported goods set can change assigned atlas positions; mod/resource-pack changes can change artwork even when the item IDs remain the same. A future reuse option must verify required-item coverage, carry forward the existing icon mapping, and make artwork refresh explicit. Copying an arbitrary old atlas beside a newly generated binary is not a supported shortcut.
