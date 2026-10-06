#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
JAVA="$ROOT/src/main/java/com/github/relativobr/supreme"

# Keep Supreme on stable Bukkit/Paper + Slimefun APIs. Direct NMS/CraftBukkit coupling and
# runtime Minecraft-version branching are intentionally forbidden because they are common sources
# of breakage on Mojang/Paper version changes. Prefer capability checks and compile-matrix coverage.
if grep -R -E 'net\.minecraft|org\.bukkit\.craftbukkit' -n "$JAVA"; then
  echo "Supreme must not depend directly on NMS or CraftBukkit implementation packages." >&2
  exit 1
fi
if grep -R -E 'Bukkit\.(getVersion|getBukkitVersion|getMinecraftVersion)\(' -n "$JAVA"; then
  echo "Supreme must not branch on parsed Minecraft/Bukkit version strings; use stable API capability checks." >&2
  exit 1
fi

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

# GenericMachine inherits AContainer's asynchronous ticker on Paper/Purpur. Its live per-block
# state is also observed by transport and block-break/recovery paths, so the shared outer maps and
# mutable reserved-input maps must remain safe for concurrent structural access. Recipe caches are
# registration-time data and intentionally remain ordinary collections.
grep -q 'import java.util.concurrent.ConcurrentHashMap;' "$GENERIC"
grep -q 'Map<Block, MachineRecipe> processing = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'Map<Block, ProgressState> progressState = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'private static final class ProgressState' "$GENERIC"
grep -q 'volatile int remaining' "$GENERIC"
grep -q 'volatile int lastCheckpoint' "$GENERIC"
grep -q 'Map<Block, Map<ItemStack, Integer>> consumedItemsMap = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'Map<Block, Integer> attemptCount = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'Map<Block, Long> heavyCheckAfter = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'Map<Block, IdleRecipeState> idleRecipeState = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'private static final class IdleRecipeState' "$GENERIC"
grep -q 'volatile int fingerprint' "$GENERIC"
grep -q 'volatile long recheckAfter' "$GENERIC"
grep -q 'Map<Block, Map<ItemStack, Integer>> activeRequiredItems = new ConcurrentHashMap<>()' "$GENERIC"
grep -q 'consumedItemsMap.computeIfAbsent(b, ignored -> new ConcurrentHashMap<>())' "$GENERIC"
grep -q 'consumedItemsMap.put(b, new ConcurrentHashMap<>())' "$GENERIC"
grep -q 'consumedItemsMap.put(b, canonicalizeConsumedItems(requiredItems, state.consumedItems()))' "$GENERIC"

# Networks/Cargo routing must reuse precomputed recipe requirements instead of regrouping every query.
grep -q 'transportRecipeIndex' "$GENERIC"
grep -q 'rebuildRecipeCaches' "$GENERIC"
grep -q 'activeRequiredItems' "$GENERIC"
grep -q 'transportRecipeIndex.get(incoming.getType())' "$GENERIC"
grep -q 'private final ItemStack\[] requiredTemplates' "$GENERIC"
grep -q 'private final int\[] requiredAmounts' "$GENERIC"
grep -q 'selectedRecipe.requiredTemplates' "$GENERIC"
grep -q 'selectedRecipe.requiredAmounts' "$GENERIC"
grep -q 'containsSimilar(ItemStack\[] items, ItemStack target)' "$GENERIC"
grep -q 'containsInputItem(DirtyChestMenu menu, int\[] inputSlots, ItemStack required)' "$GENERIC"
grep -q 'matchingRecipe(recipe.requiredTemplates, inv)' "$GENERIC"
grep -q 'anchorMaterial' "$GENERIC"
grep -q 'requiredMaterials' "$GENERIC"
grep -q 'recipeMaterialFrequency' "$GENERIC"
grep -q 'visibleMaterials.contains(recipe.anchorMaterial)' "$GENERIC"
grep -q 'visibleMaterials.containsAll(recipe.requiredMaterials)' "$GENERIC"
grep -q 'IDLE_RECIPE_REVALIDATE_TICKS = 200L' "$GENERIC"
grep -q 'SupremeInventoryUtils.fingerprint(inv, getInputSlots())' "$GENERIC"
grep -q 'fingerprint == idleState.fingerprint' "$GENERIC"
grep -q 'idleState.recheckAfter = gameTime + IDLE_RECIPE_REVALIDATE_TICKS' "$GENERIC"
grep -q 'heavyCheckAfter.put(b, gameTime + IDLE_BACKOFF_TICKS)' "$GENERIC"
grep -q 'clearIdleRecipeBackoff' "$GENERIC"
grep -q 'idleRecipeState.remove(b)' "$GENERIC"
grep -q 'if (stagedThisTick == 0)' "$GENERIC"
grep -q 'consumedItems.getOrDefault(requiredItem, 0)' "$GENERIC"
grep -q 'consumedItems.merge(requiredItem, amountToConsume, Integer::sum)' "$GENERIC"
grep -q 'canonicalizeConsumedItems' "$GENERIC"
grep -q 'final Location location = inv.getLocation();' "$GENERIC"
grep -q 'progress.remaining = nextProgress' "$GENERIC"
grep -q 'progress.lastCheckpoint = nextProgress' "$GENERIC"
if grep -q 'idleInputFingerprint\|idleRecipeRecheckAfter' "$GENERIC"; then
  echo "GenericMachine idle recipe state must not use separate boxed fingerprint/deadline maps." >&2
  exit 1
fi
if grep -q 'containsSimilar(Map<ItemStack, Integer>' "$GENERIC"; then
  echo "GenericMachine transport recipe matching must use precomputed template arrays." >&2
  exit 1
fi

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

MOBTECH_COLLECTOR="$JAVA/machine/tech/MobTechCollector.java"
grep -q 'getNearbyLivingEntities' "$MOBTECH_COLLECTOR"
grep -q 'findMatchingEntity' "$MOBTECH_COLLECTOR"
if [[ "$(grep -c 'getNearbyEntities(' "$MOBTECH_COLLECTOR")" -ne 1 ]]; then
  echo "MobTech Collector must use exactly one nearby-entity query implementation per selection pass." >&2
  exit 1
fi

# Shared output insertion may run from asynchronous GenericMachine tickers. A concurrent transport
# write can invalidate the normal output-capacity preflight, so any unexpected leftover must hop to
# the owning Paper region before creating an item entity.
INVENTORY_UTIL="$JAVA/util/SupremeInventoryUtils.java"
grep -q 'static int fingerprint' "$INVENTORY_UTIL"
grep -q 'dropItemNaturallySafe(menu.getLocation(), …3920 tokens truncated…ll");
        return;
      }

      int ticks = getTimeProcess() * 2;
      int chance = Math.min(100, itemRecipe.getChance() * getUpgradeLuck());
      var reservation = SupremeTechInputReservation.tryReserve(b, inv, getInputSlots(),
          new int[]{1, 1}, new ItemStack[]{itemRecipe.getInput1(), itemRecipe.getInput2()}, false,
          STATE_TYPE, output, ticks, chance);
      if (reservation.isEmpty()) {
        invalidProgressBar(inv, "&cCannot reserve inputs safely");
        return;
      }
      ItemStack[] reserved = reservation.get().items();
      MutationCycle cycle = new MutationCycle(reserved[0], reserved[1], output, chance);
      processing.put(b, cycle);
      progressTime.put(b, ticks);
      lastProgressCheckpoint.put(b, ticks);
      successfulMutations.remove(b);
      SupremeSpecialMachineStateCodec.savePrepared(b, reservation.get().checkpoint());
      invalidProgressBar(inv, output.getType(), " ");
      return;
    }

    if (getProgressTime(b) <= 0) {
      Boolean success = successfulMutations.get(b);
      if (success == null) {
        success = UtilMachine.getRandomInt() <= itemProcessing.chance();
        successfulMutations.put(b, success);
        SupremeSpecialMachineStateCodec.saveAuxText(b, STATE_TYPE, Boolean.toString(success));
      }

      if (success) {
        ItemStack output = itemProcessing.output().clone();
        if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), output)) {
          invalidProgressBar(inv, "&cOutput is full");
          return;
        }
        SupremeInventoryUtils.pushAll(inv, getOutputSlots(), output);
        invalidProgressBar(inv, Material.BLACK_STAINED_GLASS_PANE, " Success! ");
      } else {
        invalidProgressBar(inv, Material.BLACK_STAINED_GLASS_PANE, " Fail! ");
      }

      clearState(b);
      return;
    }

    processTicks(b, inv, itemProcessing.output());
  }

  public int getProgressTime(Block b) {
    return progressTime.getOrDefault(b, getTimeProcess() * 2);
  }

  private void processTicks(Block b, BlockMenu inv, ItemStack result) {
    int ticksTotal = getTimeProcess() * 2;
    int ticksLeft = getProgressTime(b);
    if (ticksLeft <= 0) {
      invalidProgressBar(inv, "&cMachine time failure");
      return;
    }

    if (!takeCharge(b.getLocation())) {
      invalidProgressBar(inv, "&cNo power to machine");
      return;
    }

    int nextProgress = Math.max(ticksLeft - getSpeed(), 0);
    progressTime.put(b, nextProgress);
    for (int i : InventoryRecipe.TECH_MUTATION_PROGRESS_BAR_SLOT) {
      ChestMenuUtils.updateProgressbar(inv, i, Math.round(ticksLeft / (float) getSpeed()),
          Math.round(ticksTotal / (float) getSpeed()), result);
    }

    int previousCheckpoint = lastProgressCheckpoint.getOrDefault(b, ticksTotal);
    if (nextProgress <= 0
        || Math.abs(previousCheckpoint - nextProgress) >= PROGRESS_CHECKPOINT_INTERVAL) {
      SupremeSpecialMachineStateCodec.saveProgress(b, STATE_TYPE, nextProgress);
      lastProgressCheckpoint.put(b, nextProgress);
    }
  }

  private MobTechMutationGeneric validRecipeItem(BlockMenu inv) {
    if (inv == null) {
      return null;
    }

    for (MobTechMutationGeneric produce : recipes) {
      ItemStack input1 = produce.getInput1();
      ItemStack input2 = produce.getInput2();
      if (SlimefunUtils.isItemSimilar(inv.getItemInSlot(getInputSlots()[0]), input1, false, false)
          && SlimefunUtils.isItemSimilar(inv.getItemInSlot(getInputSlots()[1]), input2, false, false)) {
        return produce;
      }
    }
    return null;
  }

  @Override
  protected String getPersistentStateType() {
    return STATE_TYPE;
  }

  @Override
  protected void restoreCheckpointForBreak(Block block) {
    restoreStateIfNeeded(block);
  }

  private boolean restoreStateIfNeeded(Block block) {
    if (isRecoveryBlocked(block)) return false;
    if (processing.containsKey(block)) {
      return true;
    }
    if (!SupremeSpecialMachineStateCodec.hasState(block, STATE_TYPE)) {
      return false;
    }

    try {
      Optional<SupremeSpecialMachineStateCodec.State> restored =
          SupremeSpecialMachineStateCodec.load(block, STATE_TYPE);
      if (restored.isPresent()) {
        SupremeSpecialMachineStateCodec.State state = restored.get();
        if (isSpecialStateUsable(state)) {
          MutationCycle cycle = new MutationCycle(state.reservedItems()[0].clone(),
              state.reservedItems()[1].clone(), state.outputs()[0].clone(),
              Math.max(0, state.auxInt()));
          processing.put(block, cycle);
          progressTime.put(block, Math.max(0, state.progress()));
          lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
          clearIdleBackoff(block);
          if ("true".equalsIgnoreCase(state.auxText())) {
            successfulMutations.put(block, true);
          } else if ("false".equalsIgnoreCase(state.auxText())) {
            successfulMutations.put(block, false);
          }
          return true;
        }
      }
    } catch (RuntimeException ex) {
      // Keep the original record and pause if preparing live state also fails.
    }

    blockRecovery(block);
    return false;
  }

  private void clearIdleBackoff(Block block) {
    nextIdleCheck.remove(block);
    lastIdleFingerprint.remove(block);
  }

  @Override
  protected void resetRecoveryRuntime(Block block) {
    super.resetRecoveryRuntime(block);
    processing.remove(block);
    progressTime.remove(block);
    successfulMutations.remove(block);
    lastProgressCheckpoint.remove(block);
    clearIdleBackoff(block);
  }

  private void clearState(Block block) {
    processing.remove(block);
    progressTime.remove(block);
    successfulMutations.remove(block);
    lastProgressCheckpoint.remove(block);
    clearIdleBackoff(block);
    SupremeSpecialMachineStateCodec.clear(block);
  }

  @Override
  protected void onMachineBreak(Block block) {
    restoreStateIfNeeded(block);
    MutationCycle cycle = processing.get(block);
    if (cycle != null && block.getWorld() != null) {
      block.getWorld().dropItemNaturally(block.getLocation(), cycle.input1().clone());
      block.getWorld().dropItemNaturally(block.getLocation(), cycle.input2().clone());
    }
    clearState(block);
  }

  @Override
  public List<String> getMachineDiagnosticLines(Block block) {
    List<String> lines = new ArrayList<>();
    if (addRecoveryDiagnosticLines(block, lines)) return lines;
    BlockMenu inv = BlockStorage.getInventory(block);
    lines.add("Machine: " + getId() + " (TECH_MUTATION)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    if (addRecoveryDiagnosticLines(block, lines)) return lines;
    MutationCycle cycle = processing.get(block);
    if (cycle == null) {
      lines.add("State: IDLE / waiting for mutation inputs");
      lines.add("Idle recipe retry: every " + IDLE_RETRY_TICKS
          + " ticks while mutation inputs are unchanged");
      return lines;
    }

    int progress = getProgressTime(block);
    Boolean success = successfulMutations.get(block);
    String state;
    if (success != null && success
        && !SupremeInventoryUtils.canFit(inv, getOutputSlots(), cycle.output())) {
      state = "OUTPUT FULL";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption() && progress > 0) {
      state = "WAITING FOR POWER";
    } else if (progress <= 0 && success == null) {
      state = "READY TO ROLL RESULT";
    } else if (progress <= 0) {
      state = success ? "RESULT READY" : "FAILED RESULT READY";
    } else {
      state = "PROCESSING";
    }

    lines.add("State: " + state + " | Progress: " + progress + "/" + (getTimeProcess() * 2));
    lines.add("Input 1: " + describeItem(cycle.input1()));
    lines.add("Input 2: " + describeItem(cycle.input2()));
    lines.add("Output: " + describeItem(cycle.output()) + " | Chance: " + cycle.chance() + "%");
    if (success != null) {
      lines.add("Persisted result: " + (success ? "success" : "failure"));
    }
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    final CustomItemStack separator = new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " ");
    List<ItemStack> displayRecipes = new ArrayList<>();
    recipes.stream().filter(Objects::nonNull).forEach(recipe -> {
      int chance = Math.min(100, recipe.getChance() * getUpgradeLuck());
      displayRecipes.add(recipe.getInput1());
      displayRecipes.add(new CustomItemStack(Material.NAME_TAG, " " + chance + "% chance"));
      displayRecipes.add(recipe.getInput2());
      displayRecipes.add(recipe.getOutput());
      displayRecipes.add(separator);
      displayRecipes.add(separator);
    });
    return displayRecipes;
  }

  public int getSpeed() {
    return speed;
  }

  public TechMutation setSpeed(int speed) {
    this.speed = speed;
    return this;
  }

  public int getUpgradeLuck() {
    return upgradeLuck;
  }

  public TechMutation setUpgradeLuck(int upgradeLuck) {
    if (upgradeLuck < 1) {
      upgradeLuck = 1;
    } else if (upgradeLuck > 4) {
      upgradeLuck = 4;
    }
    this.upgradeLuck = upgradeLuck;
    return this;
  }

  @Nonnull
  @Override
  public Radioactivity getRadioactivity() {
    return Radioactivity.VERY_HIGH;
  }
}
