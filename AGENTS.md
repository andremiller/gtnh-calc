# Repository Guidelines

## Project Structure & Module Organization

- `src/` contains the TypeScript application. Key modules include `solver.ts` for production calculations, `repository.ts` for recipe data, and `page.ts` for page models.
- `src/tests/` contains automated tests and committed snapshots; root `tests/` holds `.gtnh` scenario fixtures.
- `index.html` and `assets/` provide the browser interface and static resources.
- `data/` holds calculator datasets. `export/` contains the C# data processor; follow `export/README.md` when regenerating data.
- `gtnh-calc-export-mod/` contains the Java client-side export mod and its standalone Gradle build.
- `docs/` contains technical documentation: `docs/export-format.md` specifies the binary/atlas contract, and `docs/export-scope.md` compares direct and C# export coverage. `docs/export-validation-2.9.0.md` records the first full 0.3.1 in-game export sanity check, its provenance, timings and acceptance limitations, plus the completed 0.3.2 diagnostic run (304 research records verified; five invalid source door records identified).
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

- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v`: verify the read-only comparison tool. `python3 scripts/compare-exports.py <reference> <candidate>` accepts export directories or binary paths.

## Coding Style & Naming Conventions

Use four-space indentation in TypeScript, semicolons, and the surrounding file’s quote style. Use camelCase for variables and functions, PascalCase for classes and types, and descriptive module names such as `searchQuery.ts`. Preserve existing naming when modifying established code.

TypeScript strict checking is enabled. No dedicated formatter or linter is configured; keep changes consistent with nearby code and avoid unrelated formatting.

## Testing Guidelines

Tests use Jest with ts-jest and the Node environment. Name tests `*.test.ts` under `src/`; share repository setup through `src/tests/setup.ts`.

Add regression cases for calculation or data-loading changes. Use `.gtnh` fixtures for solver scenarios, and inspect snapshot differences before accepting updates. No coverage threshold is configured. Run tests and the build before submitting changes.

For exporter changes, run the mod build as well as the calculator tests/build. Keep Minecraft registry access and OpenGL rendering on the client thread; workers may receive only detached data and image pixels. Tests under `gtnh-calc-export-mod/src/test/` cover serialization, legacy identities, C# filtering, historical remapping, machine selection, file completion, and WebP packaging. `src/tests/directExport.test.ts` loads the Java-generated fixture through the real TypeScript reader and solver.

Regenerate `src/tests/fixtures/direct-export.bin` and `src/tests/fixtures/full-export.bin` only after intentional fixture changes: the Java tests write candidate files under `gtnh-calc-export-mod/build/fixtures/` before comparing them to committed fixtures. Review and copy the generated fixture, then rerun both suites. Distinguish automated compatibility checks from actual in-game coverage verification.

The 0.3.x direct exporter targets the same collectors and filtering as NESQL/C#: GT maps with NEI catalysts, Minecraft crafting/furnace recipes, Avaritia and Thaumcraft, including synthetic aspects. The 0.3.2 full export passed structural/solver checks, a 304-record research audit with zero mismatches, and a user browser check; preserve the distinction from exhaustive NEI coverage and the earlier 0.2.1 two-map release. Five invalid source ItemDoor records (null registry, numeric ID -1) are correctly excluded, alongside documented dynamic recipes and seven icon placeholders. Electric singleblocks occupy voltage-indexed slots; steam machines use the alternative-crafter list. Keep the standard EBF first for automatic selection. Generate legacy `r~` IDs from raw ingredients before C# filtering; tagged goods use the reference NBT-text rules rather than canonical NBT. `/gtnh-calc-export [previous.bin]` optionally reads version-6/7 history; relative paths resolve against `.minecraft`. Follow the reference quantity-first/quantity-free matching, namespace fallback, inherited aliases and reported stable last-match-wins behavior. Changed recipes may legitimately remain unmatched; do not hardcode recipe-specific aliases or invent registry replacements. Record run-to-run ordering/content differences without claiming byte-for-byte determinism. Do not replace checked-in datasets with new exports unless explicitly requested. Preview exports using an isolated copy of `dist/` as documented in the mod README. Since 0.3.1, run detached recipe filtering before client-thread aspects; use the shared aspect eligibility set and never access the graph concurrently with its worker. Preserve cached raw group identities and release temporary sources only after filtering. Collection uses 12 ms per client tick and rendering 4 ms per render event. Since 0.3.2, retain failure-only GT snapshots, underlying unexpected exception traces and the Research Station source-to-binary audit. Diagnostic logging must not change collection failures, matching or serialized records; audit expectations apply the same metadata cleanup as filtering. Raw game snapshots stay on the client thread and binary read-back stays detached. Keep phase progress and slow-task timings outside the binary; chat warnings are throttled while full slow-task details remain in the report. Icon reuse/skip support is deferred; the mod currently renders a complete atlas on every run.

## Documentation Maintenance

Keep `README.md` up to date when adding features or changing behavior, setup, or deployment. Add a concise bullet under “Changes since fork” for each meaningful change, linking upstream pull requests and crediting their authors when applicable.

Keep `docs/export-format.md` in sync when changing binary serialization, repository loading, record layouts, search indexing, or icon atlas generation and consumption. Cross-check format changes against both producer and consumer source, and distinguish compatibility requirements from exporter implementation details. Update `gtnh-calc-export-mod/README.md` when changing the mod's build, installation, command behavior, or verified release status. Keep `docs/export-scope.md` aligned with recipe selection, item filtering, and deferred features. Keep reference provenance and licensing for adapted compatibility code; modified LGPL adapter files retain their headers. Record real-export provenance, recipe counts, exclusions, and the exact checks performed without committing full generated exports or local client files.

## Commit & Pull Request Guidelines

History uses short, descriptive subjects such as “Machine updates” and “Update tests”; no enforced Conventional Commits format is evident.

Keep commits focused. Pull requests should explain the problem, resulting behavior, and validation performed. Link relevant issues and include screenshots for visible interface changes. Describe dataset provenance when updating generated data.
