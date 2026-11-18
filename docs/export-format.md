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

The active version is configured in `src/dataset.ts`; app loading and tests share it, and the app sets the CSS `--item-atlas-url` property to the matching atlas. Machine calculation rules resolve by stable item ID rather than display name. See [dataset updates](updating-datasets.md) for verification before changing versions.

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

## Direct mod export (0.3.2)

The [client mod](../gtnh-calc-export-mod/README.md) writes version 7 directly from the same live-game recipe systems as the reference NESQL collectors. Source collection and identity encoding are pinned to upstream revision `b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57`. Collection retains raw fingerprint inputs independently of the C#-filtered calculator graph. No SQL, Hibernate or NESQL runtime is needed.

Goods use the C# registry, raw damage and NBT-text identities. The NBT text is the game's original representation, not the 0.2.x canonicalized form. Tagged fluids retain separate raw records for fingerprinting even though the reference calculator fluid ID excludes NBT. Such legacy identity ambiguities remain a compatibility limitation.

Legacy recipe IDs are `r~` plus URL-safe Base64 of a Java name-based UUID (v3) over a deterministic protobuf message with its length prefix. The raw category/type, ordered slot maps, sorted ingredient stacks, registry identities, quantities, NBT and output chances participate. Raw GT type names include the original voltage suffix; C# grouping removes it only afterward. Duration and GT metadata are not direct identity fields. Schemas are retained as test resources, and eleven golden examples match IDs in the checked-in C# dataset. Synthetic aspect recipes use `r:asp:<item-id>`.

The calculator model stores recipe category, original shaped/shapeless flags and reference layout dimensions. Non-GT recipes have a `-1` GT-info pointer. Only electric singleblocks occupy voltage-indexed slots; steam machines enter the alternative list (`multiblocks` in the wire format). The preferred standard EBF is first in its list because the solver selects the first eligible alternative. Duplicate electric crafters at a tier use the C# last-crafter-wins policy and are reported.

Reference preprocessing includes item bans, output/ingredient rejection, representative alternatives, all imported fluids, tooltip inheritance and cleanup, font replacements, synthetic aspects, layout fixes, reverse links, containers, metadata cleanup and circuit conflicts. GT voltage uses the reference integer division by map amperage; ULV maps to calculator LV. Numeric metadata is converted through the reference's integer representation, and booleans become 0/1. Output probabilities use the C# float-to-whole-percent rounding behavior. The collector follows the reference's handling of special slots, input chances and alternative fluids; it does not claim the version-7 model represents every modern GT recipe condition.

The 0.3.1 pipeline filters the detached recipe graph before client-thread aspect lookup and calculates aspects only for the same retained-item eligibility set used by synthetic recipes. Cached raw ingredient-group identities avoid repeated hashing without changing legacy IDs. These scheduling optimizations do not change the version-7 layout. Collection uses 12 ms per client tick, while rendering uses 4 ms per render event; timing/progress reports are implementation details outside the binary.

Version 0.3.2 adds report-only diagnostics: failure snapshots and an independent read-back of serialized Research Station metadata, voltage, duration, amperage and signed special values against detached live snapshots after intentional metadata cleanup. Raw numeric types/values and packed research fields are recorded in the report. No binary fields, identities or history-matching rules change. An audit warning does not alter the existing completion policy. The [completed 0.3.2 run](export-validation-2.9.0.md#completed-diagnostic-run-032) verified all 304 research records without mismatches; this is verification evidence, not a change to the format.

### Optional previous binary

`/gtnh-calc-export [previous.bin]` accepts one optional path; relative paths resolve against `.minecraft`, and absolute paths/spaces are supported. Previous gzip buffers are validated before expensive game collection or rendering. Supported previous versions are 6 (recipe ID at offset 0) and 7 (search prefix, ID at offset 4); bounds, strings, I/O and alias targets are checked. Input files are read-only. Without this argument the new alias table is empty.

Current recipe IDs remain authoritative. Missing old IDs are matched with the reference's MD5 fingerprints over normalized recipe-type name and ordered goods IDs, first with little-endian quantities, then without them. Inputs with zero quantity are ignored; all outputs participate. Probability, duration, energy and metadata are omitted from historical matching. Missing goods identities also try the existing `gregtech` namespace fallback when that current identity exists.

Previous aliases whose target is unchanged or newly matched are carried forward. Fingerprint ambiguities follow stable recipe-ID ordering with last-match-wins and are reported. Unmatched recipes are logged and never resurrected. The resulting source-string/target-recipe records populate header word 7, so the existing browser can resolve saved IDs without format changes. This is best-effort migration; changed/removed recipes and historical identity ambiguities may remain unresolved. Do not force matches with recipe-specific aliases. Raw slot ordering participates in legacy IDs, so source ordering changes can also change identities between runs.

### Atlas and verification

Icons are rendered at 64×64 in client-thread batches, box-filtered with premultiplied alpha to 32×32, then placed in a 256-column atlas without the old eight-bit row wrap. The bundled encoder writes lossless WebP; it must retain its JNI package names and native resource paths. Failed icons produce checkerboards and report entries. Each run writes a fresh pair and creates `COMPLETE` only after both artifacts are ready. Icon reuse remains deferred.

The small Java-generated fixtures `src/tests/fixtures/direct-export.bin` and `src/tests/fixtures/full-export.bin` are loaded by the real TypeScript reader and solver. They cover GT and non-GT records, shaped recipes, aspects and historical aliases. The read-only `scripts/compare-exports.py` compares normalized content and machine associations. Fixtures and reference-ID tests establish compatibility, while full-source installed-pack coverage requires separate in-game acceptance. See [export scope](export-scope.md) for status and reference selection details.
