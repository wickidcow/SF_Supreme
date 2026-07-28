# Changelog

## Supreme Legacy 1.0.0-SNAPSHOT

### Compatibility

- Updated compile target to Paper 1.21.11 and Java 21 bytecode.
- Added Maven profiles for Slimefun Legacy, Slimefun Gugu, Slimefun United, and original Slimefun 4 RC-37.
- Added GitHub Actions builds for the primary Legacy artifact and API compatibility matrix.
- Removed Spring and external GuizhanLib runtime requirements.
- Embedded only the small localization/menu helper implementation Supreme uses.
- Added version-safe enchantment and particle resolution.
- Disabled the original Supreme Dev-channel auto-updater; releases are now controlled by the maintained fork.

### Fixed

- Machine block-break null errors and unsafe state cleanup.
- Input loss when a machine cannot place its full output.
- Partial output insertion and output voiding.
- Recipes stalling when separate ingredients arrive over multiple cargo cycles.
- Generic machines reserving partial inputs while waiting for the rest of a recipe or for power.
- Cargo failing to fill another empty slot for duplicate/same-item recipe inputs.
- Tech Mutation same-item recipes and output-full reroll behavior.
- Tech Robotic static recipe stack mutation and incomplete amount validation.
- MobTech Collector charging 200 J/tick despite its 20 J/tick item description.
- MobTech Collector consuming Empty MobTech twice per operation.
- Mob Collector charging bottle/tool cost before a successful completion.
- Mob Collector Tool III using 5,000 J instead of its intended 50,000 J capacity.
- Virtual Aquarium rerolling its output while blocked.
- Shared processing maps between machine tiers/instances.
- Shared quarry enabled toggle and delay counter.
- Shared generator cache/delay state between placed generators.
- Quarry insertion limited to a small hard-coded container set.
- Quarry energy use before successful insertion.
- Unsafe enum parsing from stale MobTech metadata.
- Recipe helper out-of-bounds access for missing inputs/outputs.

### Safety behavior

- Complete outputs are simulated before recipe reservation.
- Reserved inputs are tracked and returned on block break when processing did not finish.
- Unexpected insertion leftovers are dropped at the machine instead of silently deleted.
- Block/world/inventory/entity operations are kept on synchronized Slimefun tickers.
