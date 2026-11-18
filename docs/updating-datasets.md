# Updating the active dataset

1. Export with the current binary as optional history. Check `COMPLETE`, report diagnostics and the matching atlas. Preserve both artifacts together under the new `data/<version>/` directory and verify their source hashes.
2. Compare against the active dataset with `scripts/compare-exports.py`. Review exclusions, changed recipes, machine classifications, aliases and metadata. Record provenance and findings in the validation documentation.
3. Change `datasetVersion` in `src/dataset.ts`. App loading, CSS atlas selection and test setup share this setting. Keep archived versions unchanged.
4. Run `npm test -- --ci` with Node 20, without updating snapshots first. Missing project references must be resolved in the example projects; do not invent runtime recipe aliases. Review each changed calculation.
5. Machine rules resolve by stable item ID through `src/machineIds.ts`. A display-name change must not remove a machine's custom rules. For new machines, implement the appropriate rule or explicitly review their generic fallback in `src/tests/fixtures/generic-machines.json`.
6. After checking calculation differences, use `npm run test:snapshot` to accept intentional expectations. Snapshots store expected results, not application behavior. Review the snapshot diff, rerun `npm test -- --ci`, then `npm run build`. Never accept missing recipes or zero-result chains merely to pass tests.
7. Browser-check the built application, including renamed machines, icons, existing projects and representative chains. Verify the deployed pair is the configured version.
8. Commit the paired data, configuration, required calculation/scenario fixes, reviewed snapshots and provenance together. Push/deploy only after checks pass.

The 2.9.0 migration changed electric-machine classification and exposed renamed Fusion machines. Existing rule identities now survive display-name changes. The Epoxid example selects the current recipe explicitly; no automatic recipe-specific migration was introduced. Legacy scenario item IDs using old separators/namespaces were updated to the current dataset.
