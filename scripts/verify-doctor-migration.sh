#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
JAVA="$ROOT/src/main/java/com/github/relativobr/supreme"
MAPPINGS="$JAVA/diagnostics/SupremeLegacyIdMappings.java"
SERVICE="$JAVA/diagnostics/SupremeLegacyMigrationService.java"
BRIDGE="$JAVA/diagnostics/SupremeLegacyMigrationProviderBridge.java"
PLUGIN="$JAVA/Supreme.java"

for file in "$MAPPINGS" "$SERVICE" "$BRIDGE"; do
  test -f "$file"
done

# The existing Supreme compatibility catalog remains the source of truth. Doctor may only publish
# old -> currently registered modern IDs, and must stay disabled when old IDs are intentionally canonical.
grep -q 'Supreme.getLegacyItem()' "$MAPPINGS"
grep -q 'isUseLegacySupremeexpansionItemId()' "$MAPPINGS"
grep -q 'SlimefunItem.getById(newId) == null' "$MAPPINGS"
grep -q 'registerLegacySlimefunItemId' "$MAPPINGS"

# Migration remains explicitly loaded-only. Never add force-loading to a repair provider.
grep -q 'world.getLoadedChunks()' "$SERVICE"
if grep -qE '\.loadChunk\(|\.getChunkAt\(|\.getChunkAtAsync\(' "$SERVICE"; then
  echo "Supreme Doctor migration must never force-load chunks." >&2
  exit 1
fi

# Item identity migration must use Slimefun item data and retain nested-container coverage.
grep -q 'getItemDataService' "$SERVICE"
grep -q 'setItemData' "$SERVICE"
grep -q 'meta instanceof BundleMeta' "$SERVICE"
grep -q 'meta instanceof BlockStateMeta' "$SERVICE"
grep -q 'MAX_NESTED_DEPTH = 4' "$SERVICE"

# Placed blocks must retain addon block data and menu contents, and rollback to the source record on failure.
grep -q 'snapshotData(oldData)' "$SERVICE"
grep -q 'snapshotMenu(oldData)' "$SERVICE"
grep -q 'restoreBlockData(newData, oldValues, sourceId)' "$SERVICE"
grep -q 'restoreMenuLosslessly(newData, menuContents)' "$SERVICE"
grep -q 'Object restored = create.invoke(controller, location, sourceId)' "$SERVICE"
grep -q 'supreme_legacy_migrated' "$SERVICE"

# The provider API must remain optional/reflection-only for Gugu/United compatibility.
grep -q 'LegacyItemMigrationProvider' "$BRIDGE"
grep -q 'Class.forName(PROVIDER_CLASS' "$BRIDGE"
grep -q 'Proxy.newProxyInstance' "$BRIDGE"
grep -q 'SupremeLegacyIdMappings.activeMappings()' "$BRIDGE"
grep -q 'new SupremeLegacyMigrationService(plugin).scanLoaded(repair)' "$BRIDGE"
grep -q 'unregisterAll(plugin)' "$BRIDGE"

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

echo "Supreme Slimefun Doctor legacy-ID migration invariants verified."
