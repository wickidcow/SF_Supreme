# Supreme 1.0.21 — Tech Generator output identity

## Scope
This release isolates the proven **future-output-only** fix from draft PR #52:
- Resolve declared SlimefunItemStack card output IDs from the final registered Slimefun crafting definition, not setup-time visual snapshots; cache the resolution when selecting/changing a card rather than on every tick
- Preserve complete cloned item metadata and legal stack sizes in Tech Generator output slots and overflow handling
- Protect ordinary vanilla/custom outputs from any ID-based replacement; maintain production rates, electricity, recipes and original items

## Existing saved items
Items previously generated or stored in FluffyMachines barrels, player inventories, cargo or world entities may still have historical text-component formatting. No automatic normalization, migration, repair command, or storage rewrite is included. This is intentional, and the server owner has accepted historical non-stacking as a known limitation.

## Release gates
Run `scripts/verify-supreme-invariants.sh`, `scripts/verify-doctor-migration.sh` and `mvn -Plegacy clean verify` with the exact Legacy candidate; require all Paper 26.2, 26.3, rolling, Gugu and United compatibility jobs to pass before publishing.
No release from this temporary branch. GitHub may publish from a validated **main** merge with explicit commit marker `[release-supreme-1.0.21]`, or from tag `v1.0.21`, after passing the full matrix.

## Provenance
Source production/test changes isolated from `wickidcow/SF_Supreme` draft PR #52 at `93acc088dd0de306e66bbe8f10f13ef7d4f539e7`, based on `main` `f93fd7ed3b14f7420f2c3d9c922d58b3aeb5a3d3`. Historical repair and diagnostics code deliberately excluded.
