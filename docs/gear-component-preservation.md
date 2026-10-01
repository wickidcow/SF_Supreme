# Gear lore: retain existing components while appending generated descriptions

The two basic/Thornium gear preparation paths now generate only their new description lines. A package-private native-component helper appends these lines without converting the existing item lore through Bukkit legacy strings. Translation, font, hover, insertion and other component data in the old prefix remain intact. The same Soulbound/effect text, first spacer for absent lore, and append ordering are retained, including the old repeated-call append behavior.

The existing configuration lookup, tier mapping, enchantment levels, potion effects, unbreakability changes and item metadata application are unchanged. No item IDs, amounts, recipes, generation probabilities, robotic-bee formulas, energy costs, quarry selection, buffering, transport, stored data or migration policy changes. This keeps the prior quarry optimization intact. MockBukkit is test-only and is not shipped.

## Actual pre-promotion evidence

Run `36799413708` built the exact coordinated Legacy source `9e8de71adf70e9a361a74c21afe5e7006c85faf6`, passed both existing Supreme/Doctor invariant scripts and the full Maven verify build. All **21 tests** passed with no failures/errors/skips, including **8 new gear-lore tests** and the earlier 13 quarry tests. Base JAR class bytecode is within Java21 and the new test libraries are absent from the distributable.

The new cases exercise the actual helper delegated to by both production paths: rich component preservation, absent and empty lore, original spacer behavior, generated ordering, protected PDC/model/damage/item metadata, immutable inputs, repeated-call behavior and 250 deterministic comparisons with the former legacy-string algorithm. They are not live equipment/gameplay or historical-world certification.

Downloaded artifact `11134623677` matched SHA256 `3d6be0ac2472a705ad4c1ff36a3ff739f6602426d42987716a208e257247639c`. XML totals and all four validated source/build/test blob hashes were independently checked. Candidate artifact `11134273908` SHA256 is `9f9926b7f705a19df157a8d11eb2fa1fa0296013a714a05f407b937a741a41ad`.

Supreme still has other deprecations: this verbose compiler log displays 100 deprecation diagnostic lines and may be capped by the compiler, so that is not an exhaustive total or a zero-warning claim. Those remaining APIs and data-sensitive bridges require further coherent batches. No warning suppression or shipped dependency was added. Normal PR compatibility checks and the exact coordinated bundle must validate the promoted source separately. Temporary transport/workflow files are excluded, the version remains 1.0.17, and no merge or stable release is implied.
