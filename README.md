# GTNH Calculator

This is a fork of [ShadowTheAge's GTNH Calculator](https://github.com/ShadowTheAge/gtnh) for testing local changes, with AI assistance used for coding.

A calculator for GregTech: New Horizons recipes and production chains.
Large parts of this project were written using AI assistance, so code style and quality are all over the place.

[View the calculator online](https://andremiller.github.io/gtnh-calc/).

## Changes since fork

- Item tooltips show an icon beside the name and include the hovered icon’s quantity.

- Added quoted exact-phrase search, from [upstream PR #135](https://github.com/ShadowTheAge/gtnh/pull/135) by [Bmandk](https://github.com/Bmandk).

- Keep zero-quantity products visible in the product list, from [upstream PR #134](https://github.com/ShadowTheAge/gtnh/pull/134) by [Bmandk](https://github.com/Bmandk).

- Added middle-click to add an item directly to products from recipe groups, from [upstream PR #133](https://github.com/ShadowTheAge/gtnh/pull/133) by [Bmandk](https://github.com/Bmandk).

- Versioned datasets are now tracked in the same repo under `/data`; the UI version label follows the active dataset.

- Added a [client-side export mod](gtnh-calc-export-mod/README.md) for GTNH 2.9.0 that writes calculator recipe data and a WebP icon atlas directly from the game, with optional previous-export compatibility. Compatibility adapters reference [NESQL Exporter contributors](https://github.com/ShadowTheAge/nesql-exporter).

- Merged [upstream PR #132](https://github.com/ShadowTheAge/gtnh/pull/132) by [Bmandk](https://github.com/Bmandk): adds a “Whole recipes only” setting to prevent fractional crafts, preserving the original commit and authorship.



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
