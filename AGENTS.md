# Repository Guidelines

## Project Structure & Module Organization

- `src/` contains the TypeScript application. Key modules include `solver.ts` for production calculations, `repository.ts` for recipe data, and `page.ts` for page models.
- `src/tests/` contains automated tests and committed snapshots; root `tests/` holds `.gtnh` scenario fixtures.
- `index.html` and `assets/` provide the browser interface and static resources.
- `data/` holds calculator datasets. `export/` contains the C# data processor; follow `export/README.md` when regenerating data.
- `gtnh-calc-export-mod/` contains the Java client-side export mod and its standalone Gradle build.
- `docs/` contains technical documentation: `docs/export-format.md` specifies the binary/atlas contract, and `docs/export-scope.md` compares direct and C# export coverage.
- `dist/` contains generated build output. Make application changes in source files.

## Build, Test, and Development Commands

Use Node.js 20 to match CI.

- `npm ci`: install dependencies from the lockfile.
- `npm run build`: compile TypeScript and copy HTML, assets, and datasets into `dist/`.
- `npm start`: watch TypeScript and serve the application on port 5500. Run the build first to populate static files.
- `npm test -- --ci`: compile TypeScript and run the Jest suite, matching CI.
- `npm run test:watch`: run tests continuously during development.
- `npm run test:snapshot`: update snapshots after reviewing intentional behavior changes.

### Client export mod

- Use JDK 25 for Gradle (`JAVA_HOME`); the mod compiles to Java 21 bytecode for GTNH 2.9.0. Dependencies are pinned in `gtnh-calc-export-mod/build.gradle.kts`.
- `./gtnh-calc-export-mod/build.sh`: build the distributable, run Java tests, and verify the packaged WebP encoder. Build output belongs in the mod's ignored `build/` directory.
- Keep WebP JNI packages and native resource paths intact: `relocateShadowedDependencies` and `minimizeShadowedDependencies` must remain false unless the packaged encoder check proves a replacement works.

## Coding Style & Naming Conventions

Use four-space indentation in TypeScript, semicolons, and the surrounding file’s quote style. Use camelCase for variables and functions, PascalCase for classes and types, and descriptive module names such as `searchQuery.ts`. Preserve existing naming when modifying established code.

TypeScript strict checking is enabled. No dedicated formatter or linter is configured; keep changes consistent with nearby code and avoid unrelated formatting.

## Testing Guidelines

Tests use Jest with ts-jest and the Node environment. Name tests `*.test.ts` under `src/`; share repository setup through `src/tests/setup.ts`.

Add regression cases for calculation or data-loading changes. Use `.gtnh` fixtures for solver scenarios, and inspect snapshot differences before accepting updates. No coverage threshold is configured. Run tests and the build before submitting changes.

For exporter changes, run the mod build as well as the calculator tests/build. Keep Minecraft registry access and OpenGL rendering on the client thread; workers may receive only detached data and image pixels. Tests under `gtnh-calc-export-mod/src/test/` cover serialization, identities, machine selection, file completion, and WebP packaging. `src/tests/directExport.test.ts` loads the Java-generated fixture through the real TypeScript reader and solver.

Regenerate `src/tests/fixtures/direct-export.bin` only after intentional fixture changes: the Java test writes `gtnh-calc-export-mod/build/fixtures/direct-export.bin` before comparing it to the committed fixture. Review and copy the generated fixture, then rerun both suites. Distinguish automated compatibility checks from actual in-game coverage verification.

The direct exporter currently supports only the Macerator and Electric Blast Furnace maps and their referenced goods, not every item or recipe. Electric singleblocks occupy voltage-indexed slots; steam machines use the alternative-crafter list. Keep the standard EBF first for automatic selection. Do not replace the checked-in full dataset with this subset unless explicitly requested. Preview exports using an isolated copy of `dist/` as documented in the mod README. Icon reuse/skip support is deferred; the mod currently renders a complete atlas on every run.

## Documentation Maintenance

Keep `README.md` up to date when adding features or changing behavior, setup, or deployment. Add a concise bullet under “Changes since fork” for each meaningful change, linking upstream pull requests and crediting their authors when applicable.

Keep `docs/export-format.md` in sync when changing binary serialization, repository loading, record layouts, search indexing, or icon atlas generation and consumption. Cross-check format changes against both producer and consumer source, and distinguish compatibility requirements from exporter implementation details. Update `gtnh-calc-export-mod/README.md` when changing the mod's build, installation, command behavior, or verified release status. Keep `docs/export-scope.md` aligned with recipe selection, item filtering, and deferred features. Record real-export provenance, recipe counts, exclusions, and the exact checks performed without committing full generated exports or local client files.

## Commit & Pull Request Guidelines

History uses short, descriptive subjects such as “Machine updates” and “Update tests”; no enforced Conventional Commits format is evident.

Keep commits focused. Pull requests should explain the problem, resulting behavior, and validation performed. Link relevant issues and include screenshots for visible interface changes. Describe dataset provenance when updating generated data.
