# GTNH Calculator

This is a fork of [ShadowTheAge's GTNH Calculator](https://github.com/ShadowTheAge/gtnh) for testing local changes, with AI assistance used for coding.

[View the calculator online](https://andremiller.github.io/gtnh-calc/).

## Changes since fork

- Added [direct client exports](gtnh-calc-export-mod/README.md) for GTNH 2.9.0 Macerator and Electric Blast Furnace recipes, including a matching WebP atlas, export report, and separate electric/steam machine selection.

- Documented the [calculator binary and icon atlas formats](docs/export-format.md) for direct client exports.

- Added a [client-side export mod scaffold](gtnh-calc-export-mod/README.md) with a local `/gtnh-calc-export` command.

- Merged [upstream PR #132](https://github.com/ShadowTheAge/gtnh/pull/132) by [Bmandk](https://github.com/Bmandk): adds a “Whole recipes only” setting to prevent fractional crafts, preserving the original commit and authorship.

[![Deploy](https://github.com/andremiller/gtnh-calc/actions/workflows/deploy.yml/badge.svg)](https://github.com/andremiller/gtnh-calc/actions/workflows/deploy.yml)

A calculator for GregTech: New Horizons recipes and production chains.
Large parts of this project were written using AI assistance, so code style and quality are all over the place.
I just wanted to quickly put together something I can use to calculate GTNH chains. After this goal is done, it won't be an actively maintained project.

Full datasets are built using the NESQL Exporter mod and a C# processing tool. The [client export mod](gtnh-calc-export-mod/README.md) can directly export a Macerator and Electric Blast Furnace subset. See [export/README.md](export/README.md) for the C# process and [export scope](docs/export-scope.md) for coverage differences.

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

The code is under the MIT License - see the [LICENSE](LICENSE) file for details.

However, the project contains some assets from Minecraft (Mojang trademark and copyright), the GTNH development team, and respective mod authors. These assets are used under fair use.

Font used: [F77 Minecraft by 123467](https://www.fontspace.com/f77-minecraft-font-f30628)
