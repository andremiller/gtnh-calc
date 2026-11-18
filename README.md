# GTNH Calculator

This is a fork of [ShadowTheAge's GTNH Calculator](https://github.com/ShadowTheAge/gtnh) for testing local changes, with AI assistance used for coding.

[View the calculator online](https://andremiller.github.io/gtnh-calc/).

## Changes since fork

- Verified direct exporter **0.3.2** on GTNH **2.9.0 final**: 225,991 recipes, 304 successful research read-back checks and a successful user browser check; see the [validation record](docs/export-validation-2.9.0.md) for provenance and limitations.

- Added detailed exporter failure reports and automatic Research Station metadata read-back checks to the [normal export command](gtnh-calc-export-mod/README.md).

- Sped up the [client exporter](gtnh-calc-export-mod/README.md) by filtering before aspect calculation, reusing ingredient identities, and increasing the collection budget; added chat progress and timing reports. The first full 2.9.0 export has a [recorded sanity check](docs/export-validation-2.9.0.md) with remaining exclusions and compatibility caveats.

- Expanded the [direct client exporter](gtnh-calc-export-mod/README.md) to the NESQL/C# recipe systems and filtering, legacy recipe IDs, optional previous-binary remaps, and a read-only export comparison tool; compatibility adapters reference [NESQL Exporter contributors](https://github.com/ShadowTheAge/nesql-exporter/tree/b5b896ebd9bcf4cfe1f4fad19ccdc7de8414ee57). Full exports have passed structural, diagnostic and browser checks; exhaustive NEI coverage is not claimed.

- Added [direct client exports](gtnh-calc-export-mod/README.md) for GTNH 2.9.0 Macerator and Electric Blast Furnace recipes, including a matching WebP atlas, export report, and separate electric/steam machine selection.

- Documented the [calculator binary and icon atlas formats](docs/export-format.md) for direct client exports.

- Added a [client-side export mod scaffold](gtnh-calc-export-mod/README.md) with a local `/gtnh-calc-export` command.

- Merged [upstream PR #132](https://github.com/ShadowTheAge/gtnh/pull/132) by [Bmandk](https://github.com/Bmandk): adds a “Whole recipes only” setting to prevent fractional crafts, preserving the original commit and authorship.

[![Deploy](https://github.com/andremiller/gtnh-calc/actions/workflows/deploy.yml/badge.svg)](https://github.com/andremiller/gtnh-calc/actions/workflows/deploy.yml)

A calculator for GregTech: New Horizons recipes and production chains.
Large parts of this project were written using AI assistance, so code style and quality are all over the place.
I just wanted to quickly put together something I can use to calculate GTNH chains. After this goal is done, it won't be an actively maintained project.

Full datasets are built using the NESQL Exporter mod and a C# processing tool. The [client export mod](gtnh-calc-export-mod/README.md) targets equivalent output directly from the game, with optional previous-export compatibility. Version 0.3.2 has completed in-game export diagnostics and a user browser check, with documented exclusions and best-effort historical matching. See [export/README.md](export/README.md) for the C# process and [export scope](docs/export-scope.md) for coverage differences.

## Development

To run this project locally:

1. Clone the repository
2. Install dependencies:
```bash
npm install
```
3. Start the development server:
```bash
npm start
```

## Deployment

Tests, builds, and GitHub Pages deployment run only when manually triggered; pushes do not start the workflow.

1. In the repository's **Settings → Pages**, set **Source** to **GitHub Actions** (one-time setup).
2. Open [Actions → Deploy to GitHub Pages](https://github.com/andremiller/gtnh-calc/actions/workflows/deploy.yml).
3. Click **Run workflow**, select `main`, and confirm **Run workflow**.

The workflow runs tests and builds the app before publishing. Deployment requires both to succeed. The published site URL is available in the workflow's deployment output.

## License

Original repository code is under the MIT License - see [LICENSE](LICENSE). Adapted client-export compatibility files identify their LGPL-3.0 license in source headers; see the [mod licensing notes](gtnh-calc-export-mod/README.md#source-and-licensing) and bundled third-party notices.

However, the project contains some assets from Minecraft (Mojang trademark and copyright), the GTNH development team, and respective mod authors. These assets are used under fair use.

Font used: [F77 Minecraft by 123467](https://www.fontspace.com/f77-minecraft-font-f30628)
