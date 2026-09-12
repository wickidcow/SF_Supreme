# Changelog

## Supreme Legacy 1.0.9

### Specialized Machine Idle Efficiency

- Virtual Garden, Virtual Aquarium, Tech Mutation, and Tech Robotic no longer repeat unchanged idle recipe scans every synchronized tick.
- Idle retry checks use a lightweight per-block inventory fingerprint and a 4-tick backoff only while the relevant inventory state is unchanged.
- Input or output changes wake Virtual Garden and Virtual Aquarium immediately, preserving responsiveness when automation inserts an input or output space becomes available.
- Mutation and Robotic input changes wake their recipe checks immediately; once a valid recipe matches, output-full checks continue every tick exactly as before.
- Active processing remains full-speed. Mutation result rolls, persisted success/failure, Virtual Aquarium selected outputs, tool durability, reserved upgrade inputs, processing time, and energy use are unchanged.
- Idle timing/fingerprint data is runtime-only and is cleared when processing starts, persisted state is restored, the machine finishes, or the block is broken.

### New Minecraft / Paper Compatibility

- Added Java 25 CI compile checks against both the Paper 26.2 API line and the Paper 26.3 API line, in addition to Slimefun Legacy, Gugu, and United builds.
- The compatibility matrix is detection-only: the shipped plugin keeps its existing gameplay behavior, item IDs, block data, recipes, and conservative plugin API declaration instead of unnecessarily raising the minimum server version.
- Expanded safety invariants to require inventory-aware idle wakeups so future performance refactors cannot silently add input-response delays.
- Preserved existing recipes, outputs, drop tables, item IDs, machine speeds, energy costs, progression, persistent state, Networks/Cargo behavior, and rollback protections.

## Supreme Legacy 1.0.8

### Mob Scanner Performance

- Mob Collector now performs at most one nearby LivingEntity query per recipe-selection pass and reuses that fresh snapshot across all candidate tool recipes.
- Removed the previous pattern where a sword, shears, or bottle could trigger another world proximity query for each candidate Mob Collector recipe.
- Idle Mob Collectors that find no usable recipe/mob now retry every 4 ticks instead of repeating synchronized entity scans every tick; active processing remains full-speed.
- MobTech Collector now reuses one nearby LivingEntity snapshot while checking enabled Bee, Iron Golem, and Zombie recipes.
- MobTech Collector still stores the exact matched entity and revalidates its validity, world, range, and recipe predicate immediately before processing starts.
- `/supreme doctor machine` now reports the Mob Collector idle scan backoff alongside its scan range.
- Expanded CI invariants to require a single nearby-entity query implementation for both Mob Collector and MobTech Collector.
- Preserved existing recipes, drops, output quantities, scan ranges, bottle/tool costs, staged inputs, persistence, processing speeds, and energy behavior.

## Supreme Legacy 1.0.7

### Networks / Cargo Routing Performance

- Precomputes grouped recipe requirements when GenericMachine recipe sets are registered instead of rebuilding those maps during every automated insertion query.
- Indexes candidate recipes by incoming Bukkit material, then keeps Slimefun item-similarity checks as the authoritative identity check for custom items.
- Reuses grouped requirements for active staged recipes and rebuilds the per-block cache when persisted machine state is restored after a restart.
- Replaces temporary partial-slot list allocation and sorting with a single-pass fullest-stack selection while preserving first-slot tie behavior.
- Applies the existing 4-tick heavy-check backoff when staged recipes make no input progress; successful 64-item staging remains full-speed.
- `/supreme doctor machine` now reports the current no-progress attempt count and staging backoff while a machine waits for additional material.
- Electric Core, Electric Magical, Electric Gear, Forge, Foundry, Magic Altar, Electric Crafter and other GenericMachine-based systems receive the shared routing optimization.

### Correctness

- Tech Robotic II and Tech Robotic III now register their own tier-specific machine identifiers instead of reusing the base Tech Robotic identifier.
- Expanded CI invariants to guard recipe-routing caches, no-progress staging backoff, allocation-free partial-slot selection and Tech Robotic tier identifiers.
- Preserved existing item IDs, recipes, output quantities, processing speeds, energy costs, staged quantities, persistent state, rollback protections and zero-capacity transport sentinels.

## Supreme Legacy 1.0.6

### Rollback Backpressure Safety

- Staged GenericMachine rollback no longer spills reserved inputs when Networks or Cargo has filled the visible input inventory.
- Rollback now enters a persistent waiting state, blocks further automated insertion, and retries only after enough input capacity is available to restore the hidden materials.
- Added an inventory-capacity preflight so a normal rollback is all-or-nothing whenever possible; a late concurrent transport write keeps only the still-hidden remainder reserved for the next retry.
- Fully reserved ingredients and rollback-pending machines now return an occupied zero-capacity transport sentinel, preventing compatibility fallbacks from reopening all input slots.
- `/supreme doctor machine` now reports `ROLLBACK WAITING FOR INPUT SPACE` when a machine is protecting staged material from an overfilled input inventory.
- Forced recovery and deliberate block-break overflow still use Paper's owning-region scheduler, so any unavoidable item entity creation remains off the asynchronous Slimefun ticker.
- Electric Magical and every other GenericMachine-based Supreme machine receive the same protection.
- Preserved existing item IDs, recipes, output quantities, processing speeds and energy costs.

## Supreme Legacy 1.0.5

### Networks Rollback Safety

- Moved GenericMachine overflow item spawning onto Paper's owning-region scheduler so an asynchronous Slimefun ticker cannot trigger `AsyncCatcher` while returning reserved inputs.
- Added a zero-capacity transport sentinel for late same-item deliveries after an ingredient is already fully reserved.
- Reduced Networks/Cargo refill races that could otherwise fill Electric Magical and other staged-machine input inventories during processing.
- Preserved recipes, output rates, processing speeds, energy costs and item IDs.

## Supreme Legacy 1.0.4

### Specialized Machine Safety

- Added a shared persistent state format for Supreme machines that run outside GenericMachine's normal staged-processing engine.
- Tech Robotic now persists its selected output, progress, and exact consumed upgrade stack; breaking the machine after a restart still returns reserved inputs.
- Tech Mutation now persists both consumed inputs, progress, output, effective chance, and the rolled success/failure result so an output-full restart cannot reroll the mutation.
- Mob Collector now persists its active recipe, selected input slot, and progress across restarts while continuing to commit bottle/tool cost only when the output is successfully delivered.
- Virtual Garden now persists active cultivation recipe and progress across restarts.
- Virtual Aquarium now persists the selected output, input slot, recipe, and progress; tool durability is deferred until final output commit so no durability is spent while waiting for power, output space, or restart recovery.
- Added specialized Machine Doctor providers so `/supreme doctor machine` reports authoritative state for Tech Robotic, Tech Mutation, Mob Collector, Virtual Garden, and Virtual Aquarium instead of falling back to GenericMachine's unrelated state maps.
- Supreme armor protections now require a complete matching armor set. Titanium, Aurum, Adamantium, and each Thornium progression tier use distinct armor-set IDs so a single piece or mixed-tier set cannot grant radiation immunity.
- Expanded CI safety invariants for staged 64-item routing, GenericMachine persistence, specialized-machine persistence, mutation result persistence, Virtual Aquarium deferred tool cost, armor-set protection, capacitor limits, and energy-rate conversions.
- Preserved existing item IDs, recipes, output quantities, processing speeds, energy costs, Mob Collector drop tables, Virtual Aquarium drop weights, and Supreme progression recipes.

## Supreme Legacy 1.0.3

### Automation & Persistence

- Added true staged recipe reservation for GenericMachine recipes: automation now exposes one legal physical stack per still-needed ingredient instead of enough slots for the entire recipe quantity.
- Large recipes can now follow the old Supreme flow of supplying up to 64 of an ingredient, reserving that batch internally, reopening the slot, and accepting the next batch until the complete recipe is secured.
- Staged reservations are recipe-aware for Networks/Cargo and stop advertising an ingredient once its required quantity has been fully reserved.
- Reserved inputs, active recipe data and processing progress are persisted in Slimefun block data and restored after server restarts.
- Exact reserved ItemStacks are stored with Paper NBT byte serialization, while large reserved quantities are stored separately so illegal overstacked ItemStacks are never required.
- If persisted recipe state becomes unreadable but reserved-item data can still be recovered, Supreme returns those reserved items instead of silently discarding them.
- Stalled partial reservations roll back safely after `machine-max-attempt-consumed` consecutive no-progress checks; any new staged input resets that idle-attempt counter.
- Added `/supreme doctor machine` for operators. Look at a supported Supreme GenericMachine to see live state, charge, energy use, output capacity, recipe progress and required/reserved/visible/remaining quantities for every ingredient.
- Added a lightweight 4-tick heavy-check backoff while GenericMachines are idle, output-blocked, or waiting for power. Active processing remains full-speed.
- Processing progress is checkpointed periodically without repeatedly reserializing the full recipe payload.
- Preserved existing item IDs, recipes, output quantities, machine processing speeds, energy costs, output-full safety and block-break item return behavior.

## Supreme Legacy 1.0.2

### Recipe & Machine Safety

- Added safe Tech Generator output repair for legacy/corrupted overstacked slots.
- Preserves every repaired item by splitting excess into legal stacks; overflow is only dropped at the machine when all Tech Generator output slots are full.
- Clamped `tech-generator-max-amount` to the legal range of 1-64 and logs an explicit warning when an unsafe configured value is corrected.
- Added a startup Recipe Doctor audit for Supreme's production-machine recipe sets.
- Added `/supreme doctor recipes` for operators to re-run recipe diagnostics on demand.
- Recipe Doctor checks missing inputs/outputs, invalid/AIR stacks, impossible physical input/output slot requirements, and ambiguous duplicate input signatures.
- Registered `supreme.admin` as an operator-only diagnostics permission.
- Kept existing recipe-aware cargo routing, delayed complete-input consumption, output-full pausing, Tech Generator generation-plan caching, recipes, processing rates, and energy behavior unchanged.

## Supreme Legacy 1.0.1

### Performance

- Optimized `SUPREME_TECH_GENERATOR` after profiler data showed it as a measurable server-tick hotspot.
- Cached sorted Tech Generator recipe views instead of rebuilding and sorting the full recipe list every tick.
- Added per-block recipe-match caching so unchanged generator cards do not repeatedly rescan all Tech Generator recipes.
- Added per-block generation-plan caching for stable MobTech upgrades, reusing calculated outputs, speed modifiers, and energy consumption until the card or upgrade slots actually change.
- Reduced stable MobTech upgrade inspection from several passes per tick to one recalculation only when the upgrade state changes.
- Reused MobTech persistent-data keys instead of constructing new `NamespacedKey` objects during repeated machine ticks.
- Prevented identical idle, output-full, and no-power status items from being recreated every tick.
- Reworked shared output-capacity simulation to track stack amounts without cloning every occupied output slot.
- Kept recipes, generated amounts, upgrade effects, processing speed, energy behavior, output safety, and synchronized ticker behavior unchanged.

## Supreme Legacy 1.0.0-SNAPSHOT

### Compatibility

- Updated compile target to Paper 1.21.11 and Java 21 bytecode.
- Added Maven profiles for Slimefun Legacy, Slimefun Gugu, and Slimefun United.
- Added GitHub Actions builds for the primary Legacy artifact and API compatibility matrix.
- Removed Spring and external GuizhanLib runtime requirements.
- Embedded only the small localization/menu helper implementation Supreme uses.
- Added version-safe enchantment and particle resolution.
- Disabled the original Supreme Dev-channel auto-updater; releases are now controlled by the maintained fork.

### Fixed

- Converted all player-facing consumption and generation rates from internal J/tick values to mathematically equivalent J/s values (×20).
- Machine block-break null errors and unsafe state cleanup.
- Input loss when a machine cannot place its full output.
- Partial output insertion and output voiding.
- Recipes stalling when separate ingredients arrive over multiple cargo cycles.
- Generic machines reserving partial inputs while waiting for the rest of a recipe or for power.
- Cargo failing to fill another empty slot for duplicate/same-item recipe inputs.
- Tech Mutation same-item recipes and output-full reroll behavior.
- Tech Robotic static recipe stack mutation and incomplete amount validation.
- MobTech Collector charging 4,000 J/s internally despite its intended 400 J/s item description.
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

### Cross-fork API cleanup

- Moved all machine tickers to the `SlimefunBlockData` overload.
- Centralized the deprecated `Config` ticker fallback in `SupremeBlockTicker`.
- Moved generator output to the block-data overload through `SupremeEnergyProvider`.
- Retained a single suppressed `Config` generator bridge for older Gugu/United dispatch paths.
- Removed the obsolete official RC-37 CI target; supported targets are Slimefun Legacy, Gugu and United.
