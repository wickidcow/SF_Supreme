<div align="center">

# 👑⚙️ Supreme — Slimefun Legacy

**High-tier resources, machines, MobTech, collectors, generators, quarries, tools, weapons, and armor.**

![Slimefun Legacy](https://img.shields.io/badge/Slimefun-Legacy-6bd425?style=for-the-badge)
![Paper 26.x](https://img.shields.io/badge/Paper-26.x-blue?style=for-the-badge)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge)
![Maintained for AlbionMC.com](https://img.shields.io/badge/Maintained%20for-albionmc.com-7b68ee?style=for-the-badge)

</div>

> [!IMPORTANT]
> Supreme Legacy is an **unofficial community maintenance fork** with Slimefun Legacy as its primary target. It is developed and maintained for use on **albionmc.com** while preserving Supreme's original gameplay, IDs, and project history.

## 👑 What does Supreme do?

Supreme is a large late-game Slimefun addon built around advanced materials and automation. Its content includes:

- high-tier resources and magical components;
- tools, weapons, and armor;
- electric fabricators and processing machines;
- MobTech systems and collectors;
- virtual production machines;
- generators and capacitors;
- configurable quarries;
- advanced crafting/progression systems.

## 🛡️ Slimefun Legacy maintenance

This branch keeps Supreme's established item IDs and gameplay while modernizing machine safety, dependency handling, and server compatibility.

Important maintenance work includes:

- recipes start only when their complete output can fit;
- blocked outputs do not silently void items;
- reserved inputs can be restored when a machine is broken before completion;
- safer null-sensitive break handling;
- fixes for recipes whose inputs arrive over multiple Cargo ticks;
- no input reservation while a machine lacks power;
- safer same-item Cargo filling and Tech Mutation handling;
- MobTech consumption/accounting fixes;
- per-placed-block processing state for collectors, gardens, aquariums, quarries, and generators;
- modern Paper material, enchantment, particle, inventory, and entity compatibility;
- quarry state persistence and safer inventory-holder support;
- removal of inert Spring annotations/dependency;
- removal of unnecessary external GuizhanLib runtime reliance;
- disabling the original development-channel self-updater so it cannot overwrite the maintained build.

See `CHANGELOG.md` and `COMPATIBILITY.md` for deeper maintenance and testing details.

## Soulbound Supreme equipment

Supreme armor, weapons and tools implement Slimefun's `Soulbound` attribute. Their new item templates also carry the native `slimefun:soulbound` byte flag and canonical gray Soulbound lore, rather than relying on an aqua display-only label. **Slimefun Legacy's existing death/respawn listener remains responsible for saving and returning equipment.** No second inventory snapshot or on-respawn item payout is introduced.

For existing Supreme equipment with **verified historical Supreme item IDs**, a compatibility listener runs before Slimefun's soulbound death handler. If the gear is enabled in the world, not already protected, and an identical stack appears both in the player's inventory and the actual death drops, the listener marks both with Slimefun's native soulbound flag and canonical lore. Slimefun then performs the normal drop removal and respawn return. The item's stored Supreme ID, custom model, enchantments, damage and unrelated persistent metadata are preserved. It does not rescue gear by matching only its displayed name or aqua Soulbound text; unidentified gear must be investigated rather than guessed about.

Operators can hold a suspect piece of gear in the main hand and run `/supreme doctor soulbound` to inspect the saved Slimefun ID, whether it resolves to Supreme gear, whether it uses a historical ID and whether the current Slimefun runtime recognizes it as Soulbound in that world. This is a read-only diagnostic. A positive result cannot certify compatibility with other death/inventory plugins; check using a controlled server or backup before real-world death tests.

## Machine checkpoint recovery

A machine with an unreadable, incomplete or unsupported checkpoint pauses with `RECOVERY BLOCKED` in `/supreme doctor machine`. Its original Slimefun block-data fields remain in place. It does not consume inputs, spend energy or produce new outputs while blocked. Normal Slimefun player breaking, Android mining and explosions are prevented, and item-aware Cargo/Networks insertion uses the occupied status slot. Existing visible inventory contents remain accessible.

After correcting the underlying data or restoring a known-good checkpoint from backup, look at the machine and run `/supreme doctor retry` with `supreme.admin`. Retry validates the record again without deleting or rewriting it; a valid record can resume through normal ticking. Restarting also starts a fresh validation lifecycle. If the data is still unreadable, the machine stays blocked. There is no automatic reset, partial-item payout or replacement with fresh item templates.

Tech Robotic and Tech Mutation also prepare a complete serialized checkpoint before consuming inputs. Their reservations retain copies of the items you supplied, including custom names, rich lore and persistent metadata. If preparation fails or a slot changes before consumption, the cycle does not start. Existing saved reservations are restored as stored; metadata discarded by an older version cannot be reconstructed.

The guard uses the existing version-1 storage format. It protects Supreme's normal machine and Slimefun break paths; it is not a backup system or a transaction around external tools that directly delete or replace block records. Tests distinguish recording-storage/MockBukkit evidence from real-server startup and restart smoke checks.

## ❤️ Credits & project lineage

Supreme has been built by multiple developers over its history, and this fork preserves their credit:

- **RelativoBR** — original Supreme development and project history.
- **Especttra** — original Supreme development/contributions.
- **WilianSantosBR** — original Supreme development/contributions.
- **Mynothauro** — original Supreme development/contributions.
- **Slimefun-Addon-Community/Supreme** — community upstream repository and the immediate source of this fork.
- **Supreme and Slimefun community contributors** — fixes, APIs, testing, and maintenance over the project's lifetime.
- **wickidcow / Slimefun Legacy** — current machine-safety, compatibility, and preservation work for modern servers and albionmc.com.

This fork is a maintenance continuation and does not claim authorship of the original Supreme project.

## 📜 GNU General Public License v3.0

Supreme is licensed under the **GNU General Public License v3.0 (GPLv3)**. See `LICENSE` for the complete terms.

If you distribute Supreme or a modified GPL-covered version, comply with GPLv3, including preserving applicable notices, identifying modified versions, licensing covered modified source under GPLv3, and making the required Corresponding Source available when distributing object code.

The software is provided **without warranty** as described by GPLv3.

## ⚖️ Independence & trademark notice

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Supreme, Slimefun Legacy, and this maintenance fork are independent community projects. They are not sponsored, endorsed, approved, or operated by Mojang Studios or Microsoft. Minecraft-related names, brands, and assets remain the property of their respective rights holders.

This repository is also not represented as an official release of RelativoBR, Especttra, WilianSantosBR, Mynothauro, the Slimefun-Addon-Community, or the original Slimefun developers unless explicitly stated by those parties.

---

<div align="center">

**👑 End-game Slimefun deserves Supreme machinery. ⚙️**

</div>
