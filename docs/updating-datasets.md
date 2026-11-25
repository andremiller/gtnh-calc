# Updating the active dataset

1. Export with the current binary as optional history. Check `COMPLETE`, report diagnostics and the matching atlas. Once validated, preserve both artifacts together under the new `data/<version>/` directory and verify their source hashes.
2. Compare against the active dataset with `scripts/compare-exports.py`. Review exclusions, changed recipes, machine classifications, aliases and metadata. Record provenance and findings in the validation documentation.
3. Change `datasetVersion` in `src/dataset.ts`. App loading, CSS atlas selection and test setup share this setting. Put “Updated the data export for GTNH <version>” at the top of the README “Changes since fork” list, link it to the new validation record, and remove older dataset-update bullets. Keep archived datasets and validation records.
4. Confirm `node --version` reports Node 20, then run `npm test -- --ci --silent` to keep solver debug logs out of the output. Review the existing snapshots first. Missing project references must be resolved in the example projects; do not invent runtime recipe aliases. Review each changed calculation.
5. Machine rules resolve by stable item ID through `src/machineIds.ts`. A display-name change must not remove a machine's custom rules. For new machines, implement the appropriate rule or explicitly review their generic fallback in `src/tests/fixtures/generic-machines.json`.
6. After checking calculation differences, use `npm run test:snapshot` to accept intentional expectations. Snapshots store expected results, not application behavior. Review the snapshot diff, rerun `npm test -- --ci`, then `npm run build`. Never accept missing recipes or zero-result chains merely to pass tests.
7. Browser-check the built application, including renamed machines, icons, existing projects and representative chains. Verify the built pair is the configured version; verify the deployed pair only when publishing is separately requested.
8. Commit the paired data, configuration, required calculation/scenario fixes, reviewed snapshots and provenance together. Check the live upstream tip before reporting which commits will be pushed. A cached remote-tracking ref can show commits as ahead even after GitHub has them. Use `git fetch` to refresh it or `git ls-remote <remote> refs/heads/<branch>` to inspect it without updating local refs. After checks pass, push to the configured upstream branch. A version-and-export-folder update request authorizes this workflow, including commit and push. If the sandbox mounts `.git` read-only, staging and committing require a tool escalation. Leave GitHub Pages publishing to the user’s manual trigger unless separately requested.

The 2.9.0 migration changed electric-machine classification and exposed renamed Fusion machines. Existing rule identities now survive display-name changes. The Epoxid example selects the current recipe explicitly; no automatic recipe-specific migration was introduced. Legacy scenario item IDs using old separators/namespaces were updated to the current dataset.

## Reading the export report

Start with the summaries and unexpected failures. Successful Research Station read-backs and individual remap entries can span thousands of lines. Count the routine entries, then read representative excerpts and any failures in full.

```bash
rg '^(Exporter version:|Installed |Previous export:|RESEARCH summary:|Items=|Icon placeholders:|TIMING )' "$export_dir/report.txt"
rg -n '^(FAILURE|RESEARCH MISMATCH|UNSUPPORTED GT|ICON )' "$export_dir/report.txt"
rg '^UNSUPPORTED ' "$export_dir/report.txt" | cut -d ' ' -f 2 | sort | uniq -c
```

`COMPLETE` confirms both files were written. Review research mismatches and unexpected failures before activation. Decode the atlas and check every goods icon index against its 32-pixel tile coordinates. Compare the candidate against the version selected by `src/dataset.ts`. When IDs change in bulk, compare recipe I/O both in serialized order and with slots and order ignored. Check unresolved aliases and whether affected goods are still present before interpreting the change as a loss of recipe coverage.

## Checking the production build in a browser

Serve `dist/` on a local HTTP port after building, and use a fresh browser context so the test does not modify existing user projects. Check the version label and the requests for both configured dataset files. Use the deployment workflow's `cmp` checks to verify the built files match the same pair under `data/`.

Load representative `.gtnh` fixtures through **Load from file** and reopen at least one as a saved page. If the app shows a recipe migration prompt, review the changed and missing references before continuing. Confirm that the project solves with the expected recipe rows and machine selections. A solved status alone can miss recipes that were dropped during migration. Fusion Compact, mebf, Nitrobenzene, and Epoxid give useful coverage of custom and default machines, multi-step chains, and historical recipe IDs. Check that a visible item or machine icon uses the configured atlas and the tile expected for its goods ID.
