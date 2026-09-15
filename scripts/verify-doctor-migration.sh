#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
JAVA="$ROOT/src/main/java/com/github/relativobr/supreme"
MAPPINGS="$JAVA/diagnostics/SupremeLegacyIdMappings.java"
BLOCK_SERVICE="$JAVA/diagnostics/SupremeLegacyMigrationService.java"
ITEM_SERVICE="$JAVA/diagnostics/SupremeLegacyItemMigrationService.java"
ITEM_BRIDGE="$JAVA/diagnostics/SupremeLegacyMigrationProviderBridge.java"
BLOCK_BRIDGE="$JAVA/diagnostics/SupremeLegacyBlockMigrationProviderBridge.java"
PLUGIN="$JAVA/Supreme.java"

for file in "$MAPPINGS" "$BLOCK_SERVICE" "$ITEM_SERVICE" "$ITEM_BRIDGE" "$BLOCK_BRIDGE"; do
  test -f "$file"
done

# The existing Supreme compatibility catalog remains the source of truth. Doctor may only publish
# old -> currently registered modern IDs, and must stay disabled when old IDs are intentionally canonical.
grep -q 'Supreme.getLegacyItem()' "$MAPPINGS"
grep -q 'isUseLegacySupremeexpansionItemId()' "$MAPPINGS"
grep -q 'SlimefunItem.getById(newId) == null' "$MAPPINGS"
grep -q 'registerLegacySlimefunItemId' "$MAPPINGS"

# Both lanes remain loaded-only. Never add force-loading to a migration provider.
grep -q 'world.getLoadedChunks()' "$ITEM_SERVICE"
grep -q 'isChunkLoaded' "$BLOCK_BRIDGE"
if grep -qE '\.loadChunk\(|\.getChunkAt\(|\.getChunkAtAsync\(' "$ITEM_SERVICE" "$BLOCK_SERVICE" "$BLOCK_BRIDGE"; then
  echo "Supreme Doctor migration must never force-load chunks." >&2
  exit 1
fi

# Item identity migration must use Slimefun item data and retain nested-container coverage.
grep -q 'getItemDataService' "$ITEM_SERVICE"
grep -q 'setItemData' "$ITEM_SERVICE"
grep -q 'meta instanceof BundleMeta' "$ITEM_SERVICE"
grep -q 'meta instanceof BlockStateMeta' "$ITEM_SERVICE"
grep -q 'MAX_NESTED_DEPTH = 4' "$ITEM_SERVICE"

# Placed blocks must retain addon block data and menu contents, and rollback to the source record on failure.
grep -q 'snapshotData(oldData)' "$BLOCK_SERVICE"
grep -q 'snapshotMenu(oldData)' "$BLOCK_SERVICE"
grep -q 'restoreBlockData(newData, oldValues, sourceId)' "$BLOCK_SERVICE"
grep -q 'restoreMenuLosslessly(newData, menuContents)' "$BLOCK_SERVICE"
grep -q 'Object restored = create.invoke(controller, location, sourceId)' "$BLOCK_SERVICE"
grep -q 'supreme_legacy_migrated' "$BLOCK_SERVICE"

# Item migration remains an optional/reflection-only compatibility lane.
grep -q 'LegacyItemMigrationProvider' "$ITEM_BRIDGE"
grep -q 'Class.forName(PROVIDER_CLASS' "$ITEM_BRIDGE"
grep -q 'Proxy.newProxyInstance' "$ITEM_BRIDGE"
grep -q 'new SupremeLegacyItemMigrationService(plugin).scanLoaded(repair)' "$ITEM_BRIDGE"
grep -q 'SupremeLegacyBlockMigrationProviderBridge.register(plugin)' "$ITEM_BRIDGE"
grep -q 'SupremeLegacyBlockMigrationProviderBridge.unregister(plugin)' "$ITEM_BRIDGE"

# Placed blocks use the exact location-bound, claim-backed migration API with immediate revalidation.
grep -q 'LegacyBlockMigrationProvider' "$BLOCK_BRIDGE"
grep -q 'LegacyBlockMigrationCandidate' "$BLOCK_BRIDGE"
grep -q 'scanLoadedCandidates' "$BLOCK_BRIDGE"
grep -q 'isCandidateStillValid' "$BLOCK_BRIDGE"
grep -q 'stateClaim' "$BLOCK_BRIDGE"
grep -q 'MessageDigest.getInstance("SHA-256")' "$BLOCK_BRIDGE"
grep -q 'view.claim.equals(claim' "$BLOCK_BRIDGE"
grep -q 'SupremeLegacyIdMappings.activeMappings().get(view.from)' "$BLOCK_BRIDGE"
grep -q 'migrateBlock.invoke' "$BLOCK_BRIDGE"

# Lifecycle order matters: items are registered before active targets are filtered/published.
python3 - "$PLUGIN" <<'PY'
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8")
setup = text.index("MainSetup.setup(this);")
publish = text.index("SupremeLegacyIdMappings.publish(this);")
register = text.index("SupremeLegacyMigrationProviderBridge.register(this);")
if not setup < publish < register:
    raise SystemExit("Supreme Doctor migration must publish/register only after MainSetup registers current targets.")
if "SupremeLegacyMigrationProviderBridge.unregister(this);" not in text:
    raise SystemExit("Supreme Doctor migration provider must unregister during plugin disable.")
PY

echo "Supreme Slimefun Doctor split item/block migration invariants verified."
