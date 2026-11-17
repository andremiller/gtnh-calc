# Calculator export format (version 7)

This specifies the existing browser contract for the direct client exporter. It does not specify NESQL's intermediate database. The C# reference writer is [MemoryMappedPackConverter](../export/MemoryMappedPackConverter.cs). The direct mod uses [PackWriter](../gtnh-calc-export-mod/src/main/java/tools/gtnh/calc/export/PackWriter.java) and [BinaryWriter](../gtnh-calc-export-mod/src/main/java/tools/gtnh/calc/export/BinaryWriter.java); the reader and record getters are in [repository.ts](../src/repository.ts). See [export scope](export-scope.md) for coverage and filtering differences.

## Binary envelope and primitives

`data.bin` is a gzip stream, decompressed by [index.ts](../src/index.ts) before constructing the repository. Its payload is a little-endian array of 32-bit words. There is no additional magic signature: word zero is the version, currently **7**, which the reader checks strictly.

All offsets below are in **words**, not bytes. A pointer `p` addresses byte `4*p` in the decompressed payload. Object records have no length or type tag; the referring field determines their type. Signed integers use two's complement. Booleans are 0 or 1. A double occupies two words, low bits first, and is read as little-endian IEEE 754 binary64; eight-byte alignment is unnecessary.

- Object/string null pointers are `-1`. The reader handles this for objects and strings, but not arrays: always emit a real array record, including for an empty list.
- An array is a word count followed by that many words. Its elements can be integers, references, or packed recipe I/O words, according to context. Lists of strings contain string pointers.
- A string is a UTF-8 **byte** length followed by bytes padded to a word boundary. There is no terminator. The C# writer maps null and empty strings to `-1`, trims nonempty strings and removes carriage returns before encoding. It deduplicates by original string value. A safe replacement writer should zero padding: the current writer's partial-word unaligned read does not establish meaningful padding bytes, and the reader ignores them.
- The C# writer emits queued objects in stack order and patches references afterward. Ordering and deduplication are implementation details; valid references and array order are the contract. Cycles are supported. The browser caches decoded values by pointer, so do not reuse one address for incompatible types.

### Header

| Word | Value |
| --- | --- |
| 0 | Integer version, 7 |
| 1 | Item pointer array |
| 2 | Fluid pointer array |
| 3 | Ore dictionary pointer array |
| 4 | Recipe type pointer array |
| 5 | Recipe pointer array |
| 6 | Service item pointer array: Quest Book, then Anvil |
| 7 | Recipe remap pointer array |

Service ordering is consumed by [nei.ts](../src/nei.ts). Remaps map old recipe IDs to current recipe pointers and populate the same ID lookup map after normal records.

## Record layouts

Offsets are relative to a record's start. `str` means string pointer, `ref` object pointer, and `array` a pointer to a length-prefixed array. Unless specified otherwise, scalar values are signed 32-bit integers.

### Searchable prefix and goods

Items, fluids, ore dictionaries and recipes start with:

| Offset | Field |
| --- | --- |
| 0–3 | 128 search bits, lowest bits in first word |
| 4 | ID (str) |

Items and fluids extend this prefix with the following common goods fields:

| Offset | Field |
| --- | --- |
| 5 | Display name (str, HTML formatted) |
| 6 | Mod (str) |
| 7 | Internal name (str) |
| 8 | Numeric ID (serialized, no getter in current browser) |
| 9 | Atlas icon ID |
| 10 | Tooltip string-pointer array (HTML formatted lines) |
| 11 | Unlocalized name (str) |
| 12 | NBT text (str, nullable) |
| 13 | Production recipe-pointer array |
| 14 | Consumption recipe-pointer array |

An **item** has 18 words: offsets 15 = stack size, 16 = damage, 17 = nullable fluid-container ref. A **fluid** has 17 words: 15 = gas flag, 16 = array of **indices into the header's item list**, not item pointers.

A **fluid container** has three words: 0 = fluid ref, 1 = fluid amount, 2 = empty item ref. Fluid amounts use the recipe/container game's units (normally millibuckets), with no conversion in serialization.

An **ore dictionary** has six words: searchable prefix, then offset 5 = variant item-pointer array.

### Recipes and recipe types

A **recipe** has eight words: searchable prefix, 5 = packed I/O array, 6 = recipe-type ref, 7 = nullable GregTech-info ref.

The I/O array's word count is five times the entry count. Each entry is `[type, goodsPointer, slot, amount, probabilityPercent]`:

| Type | Meaning | Pointer target |
| --- | --- | --- |
| 0 | Item input | Item |
| 1 | Ore dictionary input | Ore dictionary |
| 2 | Fluid input | Fluid |
| 3 | Item output | Item |
| 4 | Fluid output | Fluid |

Entries are grouped in ascending type order; the UI traverses them in that order. Slots are zero-based row-major positions within the relevant grid (`x = slot % width`, `y = floor(slot / width)`). Item and ore dictionary inputs share the item-input grid. Amount is an integer, including zero for nonconsumed inputs. Inputs store probability 100. Outputs store `round(probability * 100)`; the reader divides by 100. This is percentage precision, not GregTech's native chance scale; C# uses midpoint-to-even rounding.

A **recipe type** has seven words:

| Offset | Field |
| --- | --- |
| 0 | Name (str) |
| 1 | Category (str) |
| 2 | Eight-integer dimensions array |
| 3 | Multiblock item-pointer array |
| 4 | Shapeless flag |
| 5 | Singleblock item-pointer array, indexed by voltage tier; nullable entries allowed |
| 6 | Default crafter item ref |

Dimensions are `[itemInputWidth, itemInputHeight, fluidInputWidth, fluidInputHeight, itemOutputWidth, itemOutputHeight, fluidOutputWidth, fluidOutputHeight]`. See [nei.ts](../src/nei.ts) for grid placement and [machines.ts](../src/machines.ts) for machine selection and calculation.

A **GregTech-info** record has seven words: 0 = voltage, 1 = duration ticks (20 ticks/second), 2 = amperage, 3 = voltage tier, 4 = metadata-pointer array, 5 = circuit-conflict bitmask, 6 = special value. Special value interpretation depends on the machine, for example required heat. A **metadata** record has three words: 0 = key (str), 1–2 = double value.

A **recipe remap** has two words: 0 = old ID (str), 1 = target recipe ref. It has no searchable prefix.

## Identity, derived data and browser usage

[Goods.cs](../export/Data/Goods.cs) generates item IDs as `i:mod:internalName:damage`, optionally followed by `:` and the lowercase SHA-1 hex digest of UTF-8 NBT text. Fluid IDs are `f:mod:internalName`. Ore dictionaries use `o:` plus their named identity or a generated identity based on variants. Recipes use `r:` IDs supplied or generated by conversion. Stable IDs matter to [saved projects](../src/page.ts); offsets need not remain stable across exports. Existing remaps and [historical recipe handling](../export/OldRecipesGenerator.cs) preserve older references.

[PackPreProcessor](../export/PackPreProcessor.cs) builds production/consumption lists from recipes. Ore dictionary inputs add consumption links to every variant. The writer converts recipe-list indices to recipe pointers, but deliberately leaves fluid container item-list indices unchanged. These links power recipe navigation and solver discovery; merely exporting recipe records is insufficient.

Search masks are generated by [SearchIndex.cs](../export/Data/SearchIndex.cs) and tested by [searchQuery.ts](../src/searchQuery.ts). Map digits to 0–9 and letters case-insensitively to 10–35. Set bit `92+c` for each character, bit `(previous*36+c)%92` for adjacent pairs, and bit `((previousPrevious*36+previous)*36+c)%92` for triples. Other characters reset adjacency; the exporter also recognizes subscript digits. OR masks over goods names/tooltips, ore dictionary variant names, and recipe input/output names (using ore dictionary masks for dictionary inputs), as the preprocessor does. These masks filter before text matching; incorrect masks can hide valid search results. One-character queries return directly after mask matching.

Display names and tooltips are converted to HTML by [MinecraftFormatter](../export/MinecraftFormatter.cs), not emitted as raw Minecraft formatting codes. Tooltips, mod names and NBT also support search/debug display. Machine associations, layout dimensions, GT metadata and quantities feed recipe display and production calculations. Numeric ID at goods offset 8 is still part of version 7 even though the current reader does not expose it.

## Atlas contract

The browser uses [itemIcon.ts](../src/itemIcon.ts) and [general.css](../assets/styles/general.css) to display `atlas.webp` at natural size, with 32×32 pixel tiles and **256 columns**:

- `column = iconId % 256`, `row = floor(iconId / 256)`.
- Pixel origin is `(column*32, row*32)`; CSS background position is its negative.
- Atlas width is 8192 pixels; height must cover all assigned rows. Retain transparency and avoid tile bleed. There is no separate coordinate manifest.
- Goods icon IDs must match the atlas generated with the binary. Ore dictionary icons cycle through variant items. Missing source images currently leave transparent tiles.

The existing [AtlasBuilder](../export/AtlasBuilder.cs) first renders a 256-column atlas with 64-pixel tiles, then halves it using box filtering. It writes WebP using lossless mode with near-lossless enabled at quality 60 and metadata skipped. These rendering/encoder choices describe current output, not required binary semantics. A direct exporter may render final-size icons with another encoder if the browser contract is preserved. [IconAtlas](../export/IconAtlas.cs) uses an eight-bit row mask, so the existing placement code wraps after 65,536 icons; treat that as an implementation limitation, not a safe behavior to reproduce.

## Observed dataset and direct-export implications

Read-only inspection of the checked-in files found version 7, 17,699,195 compressed bytes and 50,756,772 decompressed bytes. Header list counts are 50,811 items, 1,476 fluids, 982 ore dictionaries, 176 recipe types, 221,760 recipes, two service items and 21,282 remaps. The WebP dimensions are 8192×6560. These are dataset observations, not required counts or capacities.

A direct client exporter needs only the reachable calculator object graph and corresponding icons, rather than a full NESQL dump. It must still derive identities, search masks, reverse recipe links, container indices, machine associations and service entries before writing references. Emit valid empty arrays for absent lists and preserve nullable objects only where consumers tolerate them.

Binary compatibility and content parity are separate goals. [PackGenerator](../export/PackGenerator.cs) runs conversion, preprocessing, hardcoded fixes, font corrections, circuit-conflict calculation and optional historical-recipe inclusion before serialization. Filtering, synthetic aspect recipes, fixes and recipe identity policies need separate decisions when implementing the new exporter; this specification does not imply they can all be discarded. The format remains version 7; the direct mod implementation is described below.

## Direct mod export (0.2.1)

The [client mod](../gtnh-calc-export-mod/README.md) writes this format directly for the Macerator and Electric Blast Furnace maps in GTNH 2.9.0. It collects a detached graph, assigns record offsets before writing references, and stores strings with zero padding. All list fields have real array records, and the remap list is empty.

Goods retain registry-based identities. Tagged-item NBT compounds are recursively sorted by key before hashing, so their IDs need not match the old C# pipeline's textual NBT hashes. Recipe IDs are `r:gt:<map identifier>:<SHA-1>` over length-delimited normalized fields: map ID, energy/duration/tier/heat, ordered I/O and sorted numeric metadata. Alternative item sets use `o:gt:<SHA-1>` over sorted, length-delimited variant IDs. Identical normalized recipes are deduplicated and reported. There is no historical ID migration.

Only electric singleblocks occupy the voltage-indexed slots. Steam singleblocks use the alternative-crafter list (the format calls this list `multiblocks`), allowing the calculator's existing steam rules to apply. For maps without electric singleblocks, the default crafter is also placed first in that list because the solver selects the first eligible alternative.

The mod translates GT's ULV-based machine tiers to LV-based singleblock slots, clamps ULV recipe requirements to calculator LV, and stores per-amp voltage plus amperage so their product preserves native EU/t. Fractional per-amp voltage is unsupported. `COIL_HEAT` metadata populates EBF special value, falling back to legacy `mSpecialValue`. Other numeric metadata and booleans (0/1) are retained. Layout grids are row-major and at most four columns wide; this is a calculator layout, not a pixel copy of NEI.

The graph includes reverse links, fluid-container item-list indices, zero-count catalysts, and service items. Search masks use names/tooltips before HTML encoding. Registry container associations add filled and empty items for referenced fluids; custom container adapters are deferred. Unsupported special slots, probabilistic consumption, tagged fluids, differing fluid alternatives and nonnumeric metadata cause a reported recipe skip. Whole-percent output chances use midpoint-to-even rounding. The report accounts for every source candidate, including disabled/fake entries and duplicates.

Icons are rendered at 64×64 in client-thread batches, box-filtered with premultiplied alpha to 32×32, then placed without the old eight-bit row wrap. The bundled encoder writes lossless WebP. Rendering failures produce checkerboards and report entries. Encoding and serialization operate on detached data in a worker. Each run gets a fresh directory; temporary files become final artifacts before a `COMPLETE` marker is written. Failed or interrupted runs without that marker must not be used.

The synthetic fixture in `src/tests/fixtures/direct-export.bin` is generated by the mod's `PackWriterTest` and loaded by the browser's real repository and solver in `src/tests/directExport.test.ts`. It verifies storage and consumer compatibility, not installed-pack coverage or OpenGL behavior; those require separate in-game verification, with the current real-export results recorded in [export scope](export-scope.md).
