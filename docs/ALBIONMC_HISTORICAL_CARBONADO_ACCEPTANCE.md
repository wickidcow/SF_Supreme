# AlbionMC historical CARBONADO native acceptance test

Status: **NOT RUN**. This procedure is for the **draft Supreme PR #52 candidate**, not for a released build. Automated MockBukkit tests and Paper API compilation do not substitute for a native Paper saved-world test.

## Grounded reproduction (2026-11-10)

The owner removed historical CARBONADO from a FluffyMachines barrel originally populated from `/sf cheat`. A newly generated `/sf cheat` CARBONADO stacks with newly generated Supreme Tech Generator CARBONADO, but not with the historical barrel item. Both Paper `/paper dumpitem` screenshots show `slimefun:slimefun_item=CARBONADO`, custom model `2200094`, and visible `Carbonado` / `Black Diamond` text. Old name/lore has nested Adventure components and explicit flags; new presentation is flatter. The screenshots are truncated: they **do not** establish complete skull profile, texture, all typed PDC, or full item-component equality.

With fresh in MAIN / old in OFF, `/supreme doctor items preview` rejected the offhand reference. With old in MAIN / fresh in OFF, the **live read-only** preview returned `READY: exact registered presentation on a detached clone.` No actual repair or persistence test was performed.

## Preconditions

1. Use a **disposable, backed-up** test server with a copy of the **actual saved old item**, player data, Slimefun/Fluffy storage, matching addon set and registry, and the candidate JAR built from the **exact PR head**. Preserve the untouched backup separately. Never test with `/reload`.
2. Record exact Paper version, Legacy version/commit, Supreme candidate commit, FluffyMachines version, other addons, and whether the world/storage was migrated or upgraded. Verify the candidate JAR hash and the exact CI run; do not confuse a draft CI artifact with an official release.
3. Ensure **at least one original old CARBONADO remains untouched** in the backup. Record historical amount H and fresh amount F and obtain full serializations (including profile, components and typed PDC) if possible. Avoid exposing unrelated player data.
4. `supreme.admin` is required for preview; `supreme.doctor.items.repair` must be granted **temporarily** and only to the testing operator for the confirmation. Permission is default-deny. Keep other players off this disposable instance.
5. The current candidate is allowlisted for only CARBONADO, SYNTHETIC_SAPPHIRE, SYNTHETIC_EMERALD and SMALL_URANIUM. No bulk player, cargo, barrel or world conversion exists.

## Acceptance sequence

- [ ] Start the test server normally and confirm no plugin startup, registration, recipe or saved-storage errors.
- [ ] Withdraw **old** CARBONADO from a copied Fluffy barrel, keep it in **MAIN**. Put fresh `/sf cheat` CARBONADO in **OFF**. Record both amounts and full metadata. Check that old/new initially do **not** merge.
- [ ] Run **read-only** `/supreme doctor items` with old MAIN / fresh OFF. Check the reported Slimefun IDs match, both reference comparisons identify the fresh OFF item as the current guide/cheat definition, and the registered guide/cheat and crafting outputs are stackable. The old MAIN item may be different. If the two registered outputs differ, **STOP** and investigate the registration discrepancy before repair; do not assume the generator caused it.
- [ ] Run `/supreme doctor items preview`. Require exactly `READY: exact registered presentation on a detached clone.` Verify both items and the barrel counts remain unchanged. If not READY, **STOP**; do not force or bypass validation.
- [ ] Test the reversed-hand arrangement separately on **unmodified copied stacks**: fresh MAIN / old OFF. It must reject and leave both unchanged.
- [ ] On the correctly arranged copied pair, grant the temporary permission and run `/supreme doctor items repair confirm` **once**. Require an explicit success message. If rejected, stop and capture the reason. Do not repeatedly confirm blindly.
- [ ] Verify MAIN amount remains H, OFF remains F, registered Slimefun ID, typed PDC, model and complete skull profile/texture are unchanged from the old item (except the explicitly updated name/lore component representation). No other inventory slots or storage should change.
- [ ] Test mergeability of repaired historical and fresh items in vanilla inventory, **without losing any quantity**. If total exceeds max stack size, verify the expected split and conserved total rather than requiring one stack.
- [ ] Test dropped item entity pickup and merge behavior; verify count conservation.
- [ ] Insert repaired items into a copied Fluffy barrel, withdraw, and reinsert. Check displayed count and physical withdrawable quantity, not just the GUI. **Untouched old barrel stock may remain a separate incompatible identity**; the repair does not rewrite stored items. Never claim automatic barrel migration.
- [ ] Shut down normally, restart the **same test world**, and repeat inventory, dropped item, and barrel identity/quantity checks. Verify original and repaired skull textures and item model still render.
- [ ] Remove the temporary repair permission. Keep PR draft until all relevant saved-world tests pass.

## Failure and release gates

If any typed metadata, skull profile, amount, other inventory slot, barrel stock or persisted data changes unexpectedly: **STOP**, preserve logs and before/after serializations, restore the disposable world from backup, and file a scoped bug. Do not patch stored inventories automatically or change item identity globally.

Run `scripts/verify-supreme-invariants.sh`, `scripts/verify-doctor-migration.sh`, and Maven `clean verify` against the exact Legacy API, then check the exact-head Paper 26.2/26.3/rolling compatibility jobs. Recheck native behavior after each relevant code change.

**Release decision:** a green CI workflow plus a READY preview are **not** native acceptance. Require saved-world old-item confirmation, strict metadata and amount conservation, Fluffy barrel round trip, and restart persistence. No merge, stable tag, release or addon bundle update from this draft until gates are met.
