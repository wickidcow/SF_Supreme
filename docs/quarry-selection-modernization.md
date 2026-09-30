# Quarry selection: fewer allocations, unchanged output decisions

This batch is based on `99b9184062639673533f7c83454988c712511b4a` and changes only the internal selection implementation called by `ItemUtil.getItemQuarry`. It removes two method-local AtomicInteger counters and a stream/filter/collector chain. A per-call list snapshot is deliberately retained before invoking chance accessors. Nothing is cached across calls, and returned item identities remain the same references as before.

The new package-private helper preserves first-match order, inclusive lower and upper bounds, null-entry filtering, zero/negative weights and Java int overflow. Those edge cases are historical behavior, not an invitation to rebalance output probabilities during API modernization. The helper does not generate a random number, clone an ItemStack, change its amount, consume energy or mutate an output definition.

Recipes, IDs, production limits, generator/MobTech formulas, staged materials, machine codecs, transport, Doctor providers and persisted data remain unchanged. Maven additions are JUnit/Surefire test-only configuration, not shipped plugin dependencies.

## Verified before promotion

[Run 36781728556](https://github.com/wickidcow/SF_Supreme/actions/runs/36781728556) used the exact Legacy test merge `d600e077552baab2086a056d04b1cb0a6a9051a5` (Doctor/storage head `788b89e1`). Both existing source/Doctor guards, the complete Maven build, 13 JUnit cases and Java 21 bytecode checks passed. The actual downloaded XML and all four changed source/build blobs were independently verified.

The differential case checks 10,000 seeded layouts with eight rolls each against the literal original AtomicInteger/stream algorithm: 80,000 decision comparisons. Other cases cover shared inclusive boundaries, zero and extreme weights, null entries, identity equality, fresh reads, callback mutation after snapshot, early-return invocation order, propagated failures and unmodifiable source lists. This is executable decision-equivalence evidence, not a live-server throughput or TPS benchmark.

Evidence artifact `11128675901`, SHA-256 `ae7558e0124e8659923f63a1c9b5c2d8653b19a8782619aab6074e7cd93a6d13`. Exact-core candidate artifact `11128575922`, SHA-256 `9a3bd410ea35075b3056e3c2a2277041514f72cd95e16e9fe9204f4c1f58a333`.

The normal Supreme Paper/core compatibility matrix must independently pass for the promoted PR. Existing deprecated presentation/storage references outside this selection path remain separate audit work; a successful Maven build is not a zero-deprecation certification. No live old-world migration or cross-fork round trip is claimed. Temporary validation workflow and compressed patch objects are excluded. Version 1.0.17 remains a development candidate until the coordinated release step; no merge or stable release occurs in this batch.
