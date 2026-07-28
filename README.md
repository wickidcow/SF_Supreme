# Supreme Legacy

A maintained, English-first Supreme fork for modern Paper servers and the Slimefun 4-compatible API family.

This branch keeps the original Supreme gameplay and item IDs while modernizing machine safety, dependency handling, and cross-fork build support. Slimefun 5 was reviewed only for implementation ideas because its API is a rewrite and is not a runtime target for this branch.

## Runtime targets

| Slimefun implementation | Goal | Build profile |
|---|---:|---|
| Slimefun Legacy | Primary | `legacy` (default) |
| Slimefun Gugu | Supported API target | `gugu` |
| Slimefun United | Supported API target | `united` |
| Original Slimefun 4 RC-37 API | Compatibility baseline | `official` |

- Target server API: Paper 1.21.11 / Paper 26.x
- Source compatibility: Java 21
- Recommended runtime for the primary Legacy build: Java 25
- Hard runtime dependency: `Slimefun`
- No Spring runtime and no separate GuizhanLib runtime are required.

## What changed in this maintenance update

### Machine and inventory safety

- Prevents recipes from starting when the complete output cannot fit.
- Prevents partial output insertion and item voiding when output slots fill during processing.
- Restores reserved recipe inputs when a machine is broken before completion.
- Fixes null-sensitive machine break handling.
- Fixes recipes whose required ingredients arrive over multiple cargo ticks.
- Keeps incomplete recipe inputs visible and does not reserve them while the machine lacks power.
- Lets cargo fill empty input slots when a recipe requires the same item in multiple slots.
- Fixes Tech Mutation same-item automation and preserves one success roll while output is blocked.
- Fixes Tech Robotic recipe mutation and validates the complete upgrade input amount.
- Fixes MobTech Collector double consumption of Empty MobTech shells.
- Commits Mob Collector bottle/tool cost only when its output is ready to be delivered.
- Corrects Mob Collector Tool III to its intended 50,000 J charge capacity.
- Isolates processing state per placed block for collectors, gardens, aquariums, quarries, and generators.

### Modern server compatibility

- Uses Paper 1.21.11 as the compile API.
- Replaces removed direct enchantment constants with namespaced lookups.
- Handles the old/new happy-villager particle name safely.
- Makes block/entity/inventory access synchronous where Bukkit state is touched.
- Supports any Bukkit `InventoryHolder` below a quarry instead of only a narrow container list.
- Persists quarry enabled state per placed quarry instead of sharing one global toggle.
- Prevents generator output caches and delay counters from leaking between placed generators.

### Dependency cleanup

- Removes inert Spring `@Async` annotations and the Spring dependency.
- Vendors the small localization/menu helper subset Supreme actually uses.
- Removes the hard compile/runtime dependency on external GuizhanLib variants.
- Disables the original upstream Dev-channel auto-updater so it cannot overwrite the maintained fork.
- Keeps the plugin package isolated from Slimefun's own shaded libraries.

## Building

### Slimefun Legacy (default)

The GitHub workflow checks out `wickidcow/Slimefun-Legacy`, publishes its API to the runner's local Maven repository, and then builds Supreme:

```bash
mvn -Plegacy clean verify
```

Output:

```text
target/Supreme-Legacy.jar
```

### Other API checks

```bash
mvn -Pofficial -DskipTests clean package
mvn -Pgugu -DskipTests clean package
mvn -Punited -DskipTests clean package
```

These profiles are compile-compatibility checks. Distribute the normal Legacy-built jar unless a target fork proves it needs a separately compiled artifact.

## Installation

1. Build the project or download the artifact from GitHub Actions.
2. Stop the server.
3. Back up the world and `plugins/Supreme` folder.
4. Replace the existing Supreme jar in `plugins`.
5. Keep the existing Supreme data/config folder for item-ID continuity.
6. Start the server and review the log for registration errors.
7. Test the checklist in [`COMPATIBILITY.md`](COMPATIBILITY.md) before replacing a production build.

Do not run two Supreme jars at the same time.

## Important compatibility notes

- The plugin name remains `Supreme` and existing item IDs remain unchanged.
- `api-version: 1.17` is intentionally retained in `plugin.yml` to avoid unnecessarily narrowing the Bukkit compatibility declaration; the actual compile target is modern Paper.
- Slimefun 5 is not supported by this branch because it uses a rewritten API.
- The United and Gugu profiles verify the legacy package surface used by Supreme; server testing is still required for behavior differences inside each fork.

## Main content

Supreme includes high-tier resources, magical components, tools, weapons, armor, electric fabricators, MobTech systems, collectors, virtual production machines, generators, capacitors, and configurable quarries.

See [`CHANGELOG.md`](CHANGELOG.md) for the complete maintenance summary and [`COMPATIBILITY.md`](COMPATIBILITY.md) for the server test plan.

## Credits

Original Supreme developers and contributors include RelativoBR, Especttra, WilianSantosBR, and Mynothauro. This maintenance branch preserves the original project's license and gameplay lineage.
