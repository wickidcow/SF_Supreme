#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
JAVA="$ROOT/src/main/java/com/github/relativobr/supreme"

if grep -R "energyPowerPerTick" -n "$JAVA"; then
  echo "Player-facing energy rates must use the J/s conversion helpers." >&2
  exit 1
fi

grep -q 'return energyPerTick \* TICKS_PER_SECOND;' "$JAVA/util/UtilEnergy.java"

if grep -R "getValueGeneratorsWithLimit(Supreme.getSupremePowerSection().getCapacitor" -n "$JAVA"; then
  echo "Capacitors must not use the generator production limiter." >&2
  exit 1
fi

grep -q 'capacitorThorniumCapacity(100000000)' "$JAVA/util/SupremePowerSection.java"
grep -q 'capacitorSupremeCapacity(1600000000)' "$JAVA/util/SupremePowerSection.java"

GENERIC="$JAVA/generic/machine/GenericMachine.java"
grep -q 'flow == ItemTransportFlow.WITHDRAW ? getOutputSlots() : getInputSlots()' "$GENERIC"
grep -q 'MAX_STAGED_BATCH = 64' "$GENERIC"
grep -q 'SupremeMachineStateCodec.save' "$GENERIC"
grep -q 'isRollbackPending' "$GENERIC"
grep -q 'Input full - clear a slot to recover staged material' "$GENERIC"
grep -q 'canReturnConsumedMap' "$GENERIC"
grep -q 'Normal recipe rollback never drops items' "$GENERIC"
grep -q 'return new int\[]{getStatusSlot()};' "$GENERIC"

# Networks/Cargo routing must reuse precomputed recipe requirements instead of regrouping every query.
grep -q 'transportRecipeIndex' "$GENERIC"
grep -q 'rebuildRecipeCaches' "$GENERIC"
grep -q 'activeRequiredItems' "$GENERIC"
grep -q 'transportRecipeIndex.get(incoming.getType())' "$GENERIC"
grep -q 'if (stagedThisTick == 0)' "$GENERIC"
if grep -q 'java.util.Comparator\|java.util.LinkedList' "$GENERIC"; then
  echo "GenericMachine transport routing must not allocate/sort a temporary partial-slot list." >&2
  exit 1
fi

# Ordinary staged rollback must retain overflow for a later retry instead of spawning entities.
if awk '/private boolean revertConsumedItem/,/^  }/' "$GENERIC" | grep -q 'dropItemNaturallySafe'; then
  echo "Normal GenericMachine rollback must not drop item entities." >&2
  exit 1
fi

SPECIAL_CODEC="$JAVA/util/SupremeSpecialMachineStateCodec.java"
grep -q 'STATE_VERSION = "1"' "$SPECIAL_CODEC"
grep -q 'ItemStack.serializeItemsAsBytes' "$SPECIAL_CODEC"
grep -q 'ItemStack.deserializeItemsFromBytes' "$SPECIAL_CODEC"

for machine in \
  "$JAVA/machine/tech/TechRobotic.java" \
  "$JAVA/machine/tech/TechMutation.java" \
  "$JAVA/machine/MobCollector.java" \
  "$JAVA/machine/VirtualGarden.java" \
  "$JAVA/machine/VirtualAquarium.java"; do
  grep -q 'implements .*SupremeMachineDiagnostics\|SupremeMachineDiagnostics' "$machine"
  grep -q 'SupremeSpecialMachineStateCodec.save' "$machine"
  grep -q 'restoreStateIfNeeded' "$machine"
done

MOB_COLLECTOR="$JAVA/machine/MobCollector.java"
grep -q 'IDLE_SCAN_BACKOFF_TICKS = 4L' "$MOB_COLLECTOR"
grep -q 'getNearbyLivingEntities' "$MOB_COLLECTOR"
grep -q 'hasMatchingEntity' "$MOB_COLLECTOR"
grep -q 'nextIdleScan' "$MOB_COLLECTOR"
if [[ "$(grep -c 'getNearbyEntities(' "$MOB_COLLECTOR")" -ne 1 ]]; then
  echo "Mob Collector must use exactly one nearby-entity query implementation per selection pass." >&2
  exit 1
fi

grep -q 'loadReservedOnly' "$JAVA/machine/tech/TechRobotic.java"
grep -q 'loadReservedOnly' "$JAVA/machine/tech/TechMutation.java"
grep -q 'saveAuxText' "$JAVA/machine/tech/TechMutation.java"

grep -q 'commitAquariumTool' "$JAVA/machine/VirtualAquarium.java"
if awk '/protected MachineRecipe findNextRecipe/,/^  }/' "$JAVA/machine/VirtualAquarium.java" | grep -q 'setDamage'; then
  echo "Virtual Aquarium must not spend tool durability while merely selecting a recipe." >&2
  exit 1
fi

ARMOR="$JAVA/gear/AbstractArmor.java"
grep -q 'public boolean isFullSetRequired()' "$ARMOR"
awk '/public boolean isFullSetRequired\(\)/,/^  }/' "$ARMOR" | grep -q 'return true;'
grep -q 'id.endsWith("_THORNIUM")' "$ARMOR"
grep -q 'id.endsWith("_MAGIC")' "$ARMOR"
grep -q 'id.endsWith("_RARE")' "$ARMOR"
grep -q 'id.endsWith("_EPIC")' "$ARMOR"
grep -q 'id.endsWith("_LEGENDARY")' "$ARMOR"
grep -q 'id.endsWith("_SUPREME")' "$ARMOR"
grep -q 'supreme_armor_thornium_base' "$ARMOR"
grep -q 'supreme_armor_thornium_magic' "$ARMOR"
grep -q 'supreme_armor_thornium_rare' "$ARMOR"
grep -q 'supreme_armor_thornium_epic' "$ARMOR"
grep -q 'supreme_armor_thornium_legendary' "$ARMOR"
grep -q 'supreme_armor_thornium_supreme' "$ARMOR"

SETUP_TECH="$JAVA/setup/SetupTechMachines.java"
grep -q 'setMachineIdentifier(TechRobotic.TECH_ROBOTIC_II.getItemId())' "$SETUP_TECH"
grep -q 'setMachineIdentifier(TechRobotic.TECH_ROBOTIC_III.getItemId())' "$SETUP_TECH"

grep -q 'SupremeMachineDiagnostics diagnostics' "$JAVA/command/SupremeCommand.java"

echo "Supreme machine, transport, mob scan, rollback, persistence, armor, and energy invariants verified."
