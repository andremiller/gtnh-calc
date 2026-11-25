# GTNH 2.9.2 export validation

The export ran on 2026-11-25 in the Prism Launcher instance `GT_New_Horizons_2.9.2_Java_17-26`, run `20261125-210850-13298162240627178814`. It used calculator exporter 0.3.2, GregTech 5.09.54.277, and NEI 2.8.176-GTNH. The report identifies NESQL reference `b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57` and the repository's C# filtering rules. The run produced the `COMPLETE` marker.

## Dataset comparison

| Records | 2.9.1 | 2.9.2 |
| --- | ---: | ---: |
| Goods | 52,862 | 52,847 |
| Recipes | 226,564 | 226,444 |
| Recipe types | 179 | 179 |
| Historical aliases | 23,043 | 23,035 |

- The 2.9.2 binary contains 51,373 items, 1,474 fluids, 1,034 ingredient groups, and 823,230 recipe I/O entries. The comparison found 20 removed and five added goods identities, plus 1,798 tooltip changes and five display-name changes. The installed GTNH changelog attributes the removal of GT++ iodine items to GregTech's 2.9.2 update by boubou19. [GT5-Unofficial pull request #8920](https://github.com/GTNewHorizons/GT5-Unofficial/pull/8920)
- The comparison found 261 removed and 141 added recipe IDs. Of these, 96 pairs have matching ingredients, quantities, and chances after ignoring I/O order and slots, and 134 removed IDs use goods absent in 2.9.2. Among shared IDs, 640 differ in I/O, including 589 with otherwise equivalent I/O after ignoring order and slots. The 28 changed GregTech metadata records are Industrial Chisel circuit-conflict masks. All 179 recipe type definitions, including singleblock classifications, alternatives, and default crafters, match 2.9.1.
- History matching retained 226,303 recipe IDs, matched nine by quantity and two by fallback, and inherited 23,024 aliases. The report logged 3,957 ambiguous fingerprint matches under the existing last-match-wins rule. It lists 250 unresolved prior recipe IDs and 17 unresolved inherited aliases. Two former alias IDs now resolve directly. Four inherited aliases point to updated Arcane Infusion recipes for Aura Core or Element of Darkness with revised aspect quantities. Older projects using unresolved IDs may need recipe replacements.

## Report and verification

- The Research Station audit read back all 314 records with zero mismatches, errors, or missing exports.
- The report lists 2,743 unsupported Minecraft crafting records and 58 Thaumcraft records, including 50 infusion enchantments and the known runic infusion lookup failure. The five rejected GregTech records again have unregistered door inputs in Arc Furnace, Fluid Extractor, and Macerator. The seven icon placeholders cover five fluids without icons and two wildcard-damage items with rendering errors. These checks cover the exported dataset and sampled calculator behavior. Exhaustive in-game NEI coverage was outside this validation.
- The comparison tool parsed the version-7 binary. Additional checks reconstructed every goods record's production and consumption links, checked all 1,556 container records and their fluid references, and verified recipe I/O, machine, and alias pointers. ImageMagick decoded the 8192 × 6624 WebP. All 52,847 icon IDs are unique, contiguous, and within the atlas.
- All four comparison-tool tests and all 48 calculator tests passed with Node.js 20.19.0 for the calculator suite. All 29 solver snapshots passed unchanged, and the production build passed. The packaged pair matches the configured source files byte for byte.
- In Chrome, the built app showed GTNH 2.9.2 and requested both 2.9.2 files successfully. It reopened and solved the saved Fusion Compact, mebf, and Nitrobenzene scenarios and imported and solved Epoxid after confirming three historical recipe replacements. The Fusion machine resolved to its custom calculation rule, the standard Electric Blast Furnace was selected, and a rendered machine icon used the expected tile in the decoded atlas. The page still requests the legacy `assets/js/search.js` path, which returns 404 in the local build.

## SHA-256

The Prism export, checked-in pair, and production build contained identical copies.

- `data.bin` `b8b21cd09390c649ce6c060c24c7f32063078636eec8d26c53fb2b72682e38be`
- `atlas.webp` `9e22a75cde0af37cc98596285a321dcd64fba7a747d7f023668c4d2d2524451a`
