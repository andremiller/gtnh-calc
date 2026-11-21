# GTNH 2.9.1 export validation

Source: Prism instance `GT_New_Horizons_2.9.1_Java_17-26`, run `20261122-000925-12320163968256437997`, 2026-11-22. Exporter 0.3.2; GregTech 5.09.54.275; NEI 2.8.175-GTNH. COMPLETE marker present.

Read-only comparison parsed both datasets. Recipes: 225,991 → 226,564; 902 removed identities and 1,475 added. Research audit: all 314 records checked, zero mismatches or missing exports. Seven icon placeholders remain. This is not exhaustive NEI coverage.

- `data.bin` SHA-256: `d38bd4ce4d920f52dc9555aeae28c2164325c5ea8ec374afeda5ae6ae46e93ef`
- `atlas.webp` SHA-256: `49cfe5caf5e141934db43d76860eba44c57370e44c10b86d39e919f9308d7756`

Atlas decoding verified with ImageMagick: 8192×6624. Source and destination hashes match. Comparison-tool tests: four passed. All 29 solver snapshots pass unchanged. Newly exported Void Miner I/II/III are explicitly accepted with generic behavior; specialized dimension, fluid and tier-dependent mining behavior is not modeled. Existing datasets remain archived unchanged. Production build passed. Browser interaction was not verified.
