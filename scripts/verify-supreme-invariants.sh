#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"

if grep -R "J/tick\|energyPowerPerTick" -n "$ROOT/src/main/java"; then
  echo "Player-facing energy rates must be converted to J/s." >&2
  exit 1
fi

grep -q 'return energyPerTick \* TICKS_PER_SECOND;' \
  "$ROOT/src/main/java/com/github/relativobr/supreme/util/UtilEnergy.java"

if grep -R "getValueGeneratorsWithLimit(Supreme.getSupremePowerSection().getCapacitor" -n "$ROOT/src/main/java"; then
  echo "Capacitors must not use the generator production limiter." >&2
  exit 1
fi

grep -q 'capacitorThorniumCapacity(100000000)' "$ROOT/src/main/java/com/github/relativobr/supreme/util/SupremePowerSection.java"
grep -q 'capacitorSupremeCapacity(1600000000)' "$ROOT/src/main/java/com/github/relativobr/supreme/util/SupremePowerSection.java"
grep -q 'flow == ItemTransportFlow.WITHDRAW ? getOutputSlots() : getInputSlots()' "$ROOT/src/main/java/com/github/relativobr/supreme/generic/machine/GenericMachine.java"

echo "Supreme invariants verified."
