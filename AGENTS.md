# Repository Guidelines

## Project Structure & Module Organization

- `src/` contains the TypeScript application. Key modules include `solver.ts` for production calculations, `repository.ts` for recipe data, and `page.ts` for page models.
- `src/tests/` contains automated tests and committed snapshots; root `tests/` holds `.gtnh` scenario fixtures.
- `index.html` and `assets/` provide the browser interface and static resources.
- `data/` holds calculator datasets. `export/` contains the C# data processor; follow `export/README.md` when regenerating data.
- `gtnh-calc-export-mod/` contains the Java client-side export mod and its standalone Gradle build.
- `docs/` contains technical documentation, including `docs/export-format.md` for the binary and icon atlas contract.
- `dist/` contains generated build output. Make application changes in source files.

## Build, Test, and Development Commands

Use Node.js 20 to match CI.

- `npm ci`: install dependencies from the lockfile.
- `npm run build`: compile TypeScript and copy HTML, assets, and datasets into `dist/`.
- `npm start`: watch TypeScript and serve the application on port 5500. Run the build first to populate static files.
- `npm test -- --ci`: compile TypeScript and run the Jest suite, matching CI.
- `npm run test:watch`: run tests continuously during development.
- `npm run test:snapshot`: update snapshots after reviewing intentional behavior changes.

## Coding Style & Naming Conventions

Use four-space indentation in TypeScript, semicolons, and the surrounding file’s quote style. Use camelCase for variables and functions, PascalCase for classes and types, and descriptive module names such as `searchQuery.ts`. Preserve existing naming when modifying established code.

TypeScript strict checking is enabled. No dedicated formatter or linter is configured; keep changes consistent with nearby code and avoid unrelated formatting.

## Testing Guidelines

Tests use Jest with ts-jest and the Node environment. Name tests `*.test.ts` under `src/`; share repository setup through `src/tests/setup.ts`.

Add regression cases for calculation or data-loading changes. Use `.gtnh` fixtures for solver scenarios, and inspect snapshot differences before accepting updates. No coverage threshold is configured. Run tests and the build before submitting changes.

## Documentation Maintenance

Keep `README.md` up to date when adding features or changing behavior, setup, or deployment. Add a concise bullet under “Changes since fork” for each meaningful change, linking upstream pull requests and crediting their authors when applicable.

Keep `docs/export-format.md` in sync when changing binary serialization, repository loading, record layouts, search indexing, or icon atlas generation and consumption. Cross-check format changes against both producer and consumer source, and distinguish compatibility requirements from exporter implementation details. Update `gtnh-calc-export-mod/README.md` when changing the mod's build, installation, or command behavior.

## Commit & Pull Request Guidelines

History uses short, descriptive subjects such as “Machine updates” and “Update tests”; no enforced Conventional Commits format is evident.

Keep commits focused. Pull requests should explain the problem, resulting behavior, and validation performed. Link relevant issues and include screenshots for visible interface changes. Describe dataset provenance when updating generated data.
