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

grep -q 'flow == ItemTransportFlow.WITHDRAW ? getOutputSlots() : getInputSlots()' \
  "$JAVA/generic/machine/GenericMachine.java"
grep -q 'MAX_STAGED_BATCH = 64' "$JAVA/generic/machine/GenericMachine.java"
grep -q 'SupremeMachineStateCodec.save' "$JAVA/generic/machine/GenericMachine.java"

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

grep -q 'loadReservedOnly' "$JAVA/machine/tech/TechRobotic.java"
grep -q 'loadReservedOnly' "$JAVA/machine/tech/TechMutation.java"
grep -q 'saveAuxText' "$JAVA/machine/tech/TechMutation.java"

grep -q 'commitAquariumTool' "$JAVA/machine/VirtualAquarium.java"
if awk '/protected MachineRecipe findNextRecipe/,/^  }/' "$JAVA/machine/VirtualAquarium.java" | grep -q 'setDamage'; then
  echo "Virtual Aquarium must not spend tool durability while merely selecting a recipe." >&2
  exit 1
fi

grep -q 'public boolean isFullSetRequired()' "$JAVA/gear/AbstractArmor.java"
awk '/public boolean isFullSetRequired\(\)/,/^  }/' "$JAVA/gear/AbstractArmor.java" | grep -q 'return true;'
grep -q 'supreme_armor_thornium_' "$JAVA/gear/AbstractArmor.java"

grep -q 'SupremeMachineDiagnostics diagnostics' "$JAVA/command/SupremeCommand.java"

echo "Supreme machine, persistence, armor, and energy invariants verified."
