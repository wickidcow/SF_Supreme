# Compatibility and Server Test Checklist

Run this checklist on a backup/test server for every Slimefun implementation you plan to support.

## Supported build targets

| Target | Expected profile | Notes |
|---|---|---|
| Slimefun Legacy | `legacy` | Primary artifact and primary testing target |
| Slimefun Gugu | `gugu` | Legacy API packages; test localized/internal behavior |
| Slimefun United | `united` | Legacy API packages; test fork-specific machine/storage behavior |
| Slimefun 5 | None | Ideas-only reference; rewritten API is intentionally unsupported |

## Startup

- [ ] Supreme enables without `NoClassDefFoundError` for Spring or GuizhanLib.
- [ ] Existing Supreme items register under their original IDs.
- [ ] Existing placed Supreme blocks open and retain their inventories.
- [ ] No duplicate item-ID warnings appear.
- [ ] Config and language files load in English.

## Electric fabricators and generic machines

- [ ] Test recipes with one ingredient.
- [ ] Test recipes with multiple different ingredients.
- [ ] Test a recipe requiring the same item in multiple input positions using cargo.
- [ ] Deliver ingredients over separate cargo ticks and confirm the machine starts.
- [ ] Remove machine power and confirm incomplete/full inputs remain visible and unconsumed.
- [ ] Fill every output slot and confirm no input is consumed.
- [ ] Fill output slots during processing and confirm the machine pauses without losing output.
- [ ] Break an active machine and confirm reserved inputs are returned/dropped once.

## Tech machines

- [ ] Tech Mutation accepts both distinct-input and same-item recipes.
- [ ] Tech Mutation does not reroll success repeatedly while output is full.
- [ ] Tech Robotic consumes the configured amount and does not alter guide recipes.
- [ ] Tech Generator preserves its card and creates the full calculated output set.
- [ ] Malformed/stale MobTech metadata does not stop the ticker.
- [ ] MobTech Collector consumes exactly one Empty MobTech shell.
- [ ] MobTech Collector consumes 20 J/tick for all configured tiers unless intentionally changed.

## Collectors and virtual machines

- [ ] Mob Collector pauses safely with full output.
- [ ] Mob Collector does not consume bottles or damage tools until output completes.
- [ ] Virtual Garden produces every configured output for multi-output recipes.
- [ ] Virtual Aquarium chooses one result and keeps that result while output is blocked.
- [ ] Tool durability and unbreakable tools behave correctly.
- [ ] Breaking one tier does not reset or alter another placed tier.

## Quarries

- [ ] Each placed quarry has an independent enabled/disabled state.
- [ ] Quarry output works with chests and another `InventoryHolder` used on your server.
- [ ] A full destination inventory does not consume energy or delete an item.
- [ ] Quarry production delay is independent for multiple placed quarries.
- [ ] Owner/protection checks prevent unauthorized control.

## Generators and capacitors

- [ ] Multiple identical generators calculate output independently.
- [ ] Moving/removing the environmental condition updates only the affected generator.
- [ ] Stored energy is not reset by generation recalculation.
- [ ] Breaking a generator clears its cached state without affecting another generator.

## Production rollout

- [ ] Stop the server before replacing the jar.
- [ ] Keep a world and plugin-folder backup.
- [ ] Run at least one full restart, not only `/reload`.
- [ ] Watch the log during chunk loading of areas containing old Supreme machines.
- [ ] Test cargo input/output with the addons used on the production server.

## SlimefunBlockData API bridge

Supreme uses `SlimefunBlockData` as the shared primary ticker and generator-output API across
Slimefun Legacy, Gugu and United. Deprecated `Config` signatures exist only inside the two
classes in `com.github.relativobr.supreme.compat`, where they delegate to the same implementation.
The much older official RC-37 API is not a supported target because it predates this storage API.
