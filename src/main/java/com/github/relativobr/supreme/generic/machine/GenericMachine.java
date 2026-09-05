package com.github.relativobr.supreme.generic.machine;

import static java.util.Objects.nonNull;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import com.github.relativobr.supreme.generic.recipe.InventoryRecipe;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shared implementation for Supreme's container machines.
 *
 * <p>The state maps are deliberately keyed by placed block so machines never share progress. Cargo
 * keeps at most one natural stack buffered for each recipe ingredient. Large recipes may consume that
 * stack in stages while waiting for cargo to refill it. Reserved inputs are restored if the block is
 * broken before the completed output is delivered.</p>
 */
public class GenericMachine extends AContainer implements NotHopperable, RecipeDisplayItem {

  private final Map<Block, MachineRecipe> processing = new HashMap<>();
  private final Map<Block, Integer> progressTime = new HashMap<>();
  private final Map<Block, Map<ItemStack, Integer>> consumedItemsMap = new HashMap<>();
  private final Map<Block, Integer> attemptCount = new HashMap<>();
  public final List<AbstractItemRecipe> machineRecipes = new ArrayList<>();
  private Integer timeProcess;
  private String machineIdentifier = "MediumContainerMachine";

  @ParametersAreNonnullByDefault
  public GenericMachine(ItemGroup category, SlimefunItemStack item, RecipeType recipeType,
      ItemStack[] recipe) {
    super(category, item, recipeType, recipe);

    addItemHandler(onBlockBreak());

    new BlockMenuPreset(getId(), getItemName()) {

      @Override
      public void init() {
        constructMenu(this);
      }

      @Override
      public boolean canOpen(Block b, Player p) {
        return p.hasPermission("slimefun.inventory.bypass") || Slimefun.getProtectionManager()
            .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK);
      }

      @Override
      public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
        // Automated insertion must use the item-aware overload below. Returning every input slot here
        // lets some cargo implementations bypass the one-stack buffer and spread items again.
        return flow == ItemTransportFlow.WITHDRAW ? getOutputSlots() : new int[0];
      }

      @Override
      public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow,
          ItemStack item) {
        if (flow == ItemTransportFlow.WITHDRAW) {
          return getOutputSlots();
        }
        return getRecipeAwareInsertSlots(menu, item);
      }
    };
  }

  /**
   * Exposes at most one physical input stack for each recipe ingredient. A full matching stack
   * exposes no insertion slots, so cargo systems stop transferring that ingredient until Supreme
   * consumes it. This restores Supreme's low-churn legacy buffering while retaining recipe-aware
   * routing for multi-input machines.
   */
  private int[] getRecipeAwareInsertSlots(DirtyChestMenu menu, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return new int[0];
    }

    if (machineRecipes.isEmpty()) {
      return getSingleStackInsertSlot(menu, item);
    }

    ItemStack[] selectedRecipe = findTransportRecipe(menu, item);
    if (selectedRecipe == null) {
      return new int[0];
    }

    ItemStack requiredTemplate = null;
    for (ItemStack required : groupSimilarItems(selectedRecipe).keySet()) {
      if (SlimefunUtils.isItemSimilar(required, item, false, false)) {
        requiredTemplate = required;
        break;
      }
    }

    return requiredTemplate == null ? new int[0] : getSingleStackInsertSlot(menu, requiredTemplate);
  }

  /**
   * Returns one partial matching slot, or one empty slot when the ingredient is not present yet.
   * If a matching stack is already full, insertion stops even if other input slots are empty.
   */
  private int[] getSingleStackInsertSlot(DirtyChestMenu menu, ItemStack item) {
    int bestPartialSlot = -1;
    int bestPartialAmount = -1;
    int firstEmptySlot = -1;
    boolean hasMatchingStack = false;

    for (int slot : getInputSlots()) {
      ItemStack stack = menu.getItemInSlot(slot);
      if (stack == null || stack.getType().isAir()) {
        if (firstEmptySlot < 0) {
          firstEmptySlot = slot;
        }
        continue;
      }

      if (!SlimefunUtils.isItemSimilar(stack, item, false, false)) {
        continue;
      }

      hasMatchingStack = true;
      if (stack.getAmount() >= stack.getMaxStackSize()) {
        return new int[0];
      }

      if (stack.getAmount() > bestPartialAmount) {
        bestPartialAmount = stack.getAmount();
        bestPartialSlot = slot;
      }
    }

    if (bestPartialSlot >= 0) {
      return new int[] {bestPartialSlot};
    }
    if (!hasMatchingStack && firstEmptySlot >= 0) {
      return new int[] {firstEmptySlot};
    }
    return new int[0];
  }

  /**
   * Selects the first recipe containing the incoming item that is compatible with everything already
   * present in the machine. When several recipes are possible, prefer the one with the most distinct
   * ingredients already represented in the input inventory, matching normal recipe selection behavior.
   */
  private ItemStack[] findTransportRecipe(DirtyChestMenu menu, ItemStack incoming) {
    ItemStack[] bestRecipe = null;
    int bestMatchedIngredients = -1;

    for (AbstractItemRecipe recipe : machineRecipes) {
      ItemStack[] input = recipe.getInputNotNull();
      Map<ItemStack, Integer> requiredItems = groupSimilarItems(input);
      if (!containsSimilar(requiredItems, incoming)) {
        continue;
      }

      boolean compatible = true;
      for (int slot : getInputSlots()) {
        ItemStack existing = menu.getItemInSlot(slot);
        if (existing == null || existing.getType().isAir()) {
          continue;
        }
        if (!containsSimilar(requiredItems, existing)) {
          compatible = false;
          break;
        }
      }
      if (!compatible) {
        continue;
      }

      int matchedIngredients = 0;
      for (ItemStack required : requiredItems.keySet()) {
        if (containsInputItem(menu, required)) {
          matchedIngredients++;
        }
      }

      if (matchedIngredients > bestMatchedIngredients) {
        bestRecipe = input;
        bestMatchedIngredients = matchedIngredients;
      }
    }

    return bestRecipe;
  }

  private boolean containsSimilar(Map<ItemStack, Integer> items, ItemStack target) {
    for (ItemStack candidate : items.keySet()) {
      if (SlimefunUtils.isItemSimilar(candidate, target, false, false)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsInputItem(DirtyChestMenu menu, ItemStack required) {
    for (int slot : getInputSlots()) {
      ItemStack existing = menu.getItemInSlot(slot);
      if (existing != null && !existing.getType().isAir()
          && SlimefunUtils.isItemSimilar(existing, required, false, false)) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  @Override
  protected BlockBreakHandler onBlockBreak() {
    return new SimpleBlockBreakHandler() {
      @Override
      public void onBlockBreak(Block b) {
        BlockMenu inv = BlockStorage.getInventory(b);
        if (inv != null) {
          // Restore reserved inputs before the normal menu drop path, returning each item once.
          revertConsumedItem(b, inv);
          inv.dropItems(b.getLocation(), getInputSlots());
          inv.dropItems(b.getLocation(), getOutputSlots());
        } else {
          dropConsumedItems(b);
        }
        onMachineBreak(b);
        removeMapBlock(b);
      }
    };
  }

  /** Allows special machines to clear their own per-block state. */
  protected void onMachineBreak(Block block) {
    // Default machines have no additional state.
  }

  /**
   * Gives specialized machines a chance to validate external state immediately before inputs are
   * consumed. Returning {@code false} safely cancels the pending cycle.
   */
  protected boolean canStartProcess(Block block, BlockMenu menu, MachineRecipe recipe) {
    return true;
  }

  /** Called once after the complete recipe input and energy charge have been secured. */
  protected void onProcessStarted(Block block, BlockMenu menu, MachineRecipe recipe) {
    // Default machines do not have external state to commit.
  }

  protected void updateStatusReset(BlockMenu menu) {
    menu.replaceExistingItem(getStatusSlot(), getDisplayOrInfo(null, " "));
  }

  protected void updateStatusInvalidInput(BlockMenu menu) {
    menu.replaceExistingItem(getStatusSlot(),
        getDisplayOrWarn(null, "&cInput a valid material to start"));
  }

  protected void updateStatusOutputFull(BlockMenu menu) {
    menu.replaceExistingItem(getStatusSlot(), getDisplayOrWarn(null, "&cOutput is full"));
  }

  protected void updateStatusConnectEnergy(BlockMenu menu, ItemStack itemStack) {
    menu.replaceExistingItem(getStatusSlot(),
        getDisplayOrWarn(itemStack, "&cConnect energy to continue"));
  }

  protected void updateStatusLoadMaterial(BlockMenu menu, ItemStack itemStack, int attempts,
      int progressCount, int totalProgress) {
    CustomItemStack infoDetail = new CustomItemStack(itemStack,
        "&cLoad more material to start", "",
        "&7Attempts: &e" + attempts + " &7/ &e"
            + Supreme.getSupremeOptions().getMachineMaxAttemptConsumed(),
        "&7Progress: &e" + progressCount + " &7/ &e" + totalProgress, "");
    menu.replaceExistingItem(getStatusSlot(), infoDetail);
  }

  @Nonnull
  public GenericMachine setMachineRecipes(@Nonnull List<AbstractItemRecipe> recipes) {
    machineRecipes.clear();
    machineRecipes.addAll(recipes);
    return this;
  }

  public GenericMachine setTimeProcess(int timeProcess) {
    this.timeProcess = timeProcess;
    return this;
  }

  public int getTimeProcess() {
    if (timeProcess == null) {
      timeProcess = 15;
    }
    return timeProcess;
  }

  @Override
  protected void constructMenu(BlockMenuPreset preset) {
    for (int i : getBorderSlots()) {
      preset.addItem(i, new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : getInputBorderSlots()) {
      preset.addItem(i, new CustomItemStack(Material.CYAN_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : getOutputBorderSlots()) {
      preset.addItem(i, new CustomItemStack(Material.ORANGE_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }

    preset.addItem(getStatusSlot(), new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "),
        ChestMenuUtils.getEmptyClickHandler());

    for (int i : getOutputSlots()) {
      preset.addMenuClickHandler(i, new ChestMenu.AdvancedMenuClickHandler() {
        @Override
        public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
          return false;
        }

        @Override
        public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor,
            ClickAction action) {
          return cursor == null || cursor.getType() == Material.AIR;
        }
      });
    }
  }

  @Override
  public ItemStack getProgressBar() {
    return new ItemStack(Material.PISTON);
  }

  @Override
  public int[] getInputSlots() {
    return InventoryRecipe.MEDIUM_INPUT;
  }

  @Override
  public int[] getOutputSlots() {
    return InventoryRecipe.MEDIUM_OUTPUT;
  }

  public int getStatusSlot() {
    return InventoryRecipe.MEDIUM_STATUS_SLOT;
  }

  public int[] getBorderSlots() {
    return InventoryRecipe.MEDIUM_BORDER;
  }

  public int[] getInputBorderSlots() {
    return InventoryRecipe.MEDIUM_INPUT_BORDER;
  }

  public int[] getOutputBorderSlots() {
    return InventoryRecipe.MEDIUM_OUTPUT_BORDER;
  }

  @Nonnull
  @Override
  public String getMachineIdentifier() {
    return nonNull(machineIdentifier) ? machineIdentifier : "MachineIdentifier";
  }

  @Nonnull
  public GenericMachine setMachineIdentifier(@Nonnull String identifier) {
    machineIdentifier = identifier;
    return this;
  }

  @Override
  protected void tick(Block b) {
    BlockMenu inv = BlockStorage.getInventory(b);
    if (inv == null) {
      return;
    }

    if (isProcessing(b)) {
      doProcessing(b, inv);
    } else {
      nextProcessing(b, inv);
    }
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    List<ItemStack> displayRecipes = new ArrayList<>();
    for (AbstractItemRecipe recipe : machineRecipes) {
      displayRecipes.add(new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, " "));
      displayRecipes.add(recipe.getFirstItemOutput());
    }
    return displayRecipes;
  }

  @Override
  protected MachineRecipe findNextRecipe(BlockMenu inv) {
    for (AbstractItemRecipe recipe : machineRecipes) {
      ItemStack[] input = recipe.getInputNotNull();
      if (matchingRecipe(input, inv)) {
        return new MachineRecipe(getTimeProcess(), input, recipe.getOutputNotNull());
      }
    }
    return null;
  }

  protected int getProgressTime(Block b) {
    return progressTime.getOrDefault(b, getTimeProcess());
  }

  protected MachineRecipe getProcessing(Block b) {
    return processing.get(b);
  }

  protected Map<ItemStack, Integer> getConsumedItems(Block b) {
    return consumedItemsMap.computeIfAbsent(b, ignored -> new LinkedHashMap<>());
  }

  protected boolean isProcessing(Block b) {
    return getProcessing(b) != null;
  }

  protected boolean notHasSpaceOutput(BlockMenu inv, ItemStack[] result) {
    return !SupremeInventoryUtils.canFit(inv, getOutputSlots(), result);
  }

  private void nextProcessing(Block b, BlockMenu inv) {
    MachineRecipe next = findNextRecipe(inv);
    if (next != null) {
      processing.put(b, next);
      progressTime.put(b, next.getTicks());
      consumedItemsMap.put(b, new LinkedHashMap<>());
      attemptCount.put(b, 0);
    } else if (getInputSlots().length <= 5) {
      updateStatusReset(inv);
    } else {
      updateStatusInvalidInput(inv);
    }
  }

  protected final void removeMapBlock(Block b) {
    progressTime.remove(b);
    processing.remove(b);
    attemptCount.remove(b);
    consumedItemsMap.remove(b);
  }

  private void doProcessing(Block b, BlockMenu inv) {
    MachineRecipe recipe = getProcessing(b);
    if (recipe == null) {
      removeMapBlock(b);
      return;
    }

    ItemStack[] result = recipe.getOutput();
    if (result == null || result.length == 0) {
      removeMapBlock(b);
      updateStatusReset(inv);
      return;
    }

    int ticks = recipe.getTicks();
    int ticksRemaining = getProgressTime(b);

    if (ticksRemaining == ticks) {
      startProcessTicks(b, inv, ticksRemaining);
    } else if (ticksRemaining <= 0) {
      endProcessTicks(b, inv, result);
    } else if (takeMachineCharge(b, inv)) {
      doProcessTicks(b, inv, ticks, ticksRemaining, result[0]);
    }
  }

  private boolean takeMachineCharge(Block b, BlockMenu inv) {
    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
      updateStatusConnectEnergy(inv, null);
      return false;
    }
    removeCharge(b.getLocation(), getEnergyConsumption());
    return true;
  }

  private void startProcessTicks(Block b, BlockMenu inv, int ticksRemaining) {
    MachineRecipe recipe = getProcessing(b);
    if (recipe == null) {
      removeMapBlock(b);
      return;
    }

    // Do not reserve ingredients while a complete result has nowhere to go.
    if (notHasSpaceOutput(inv, recipe.getOutput())) {
      updateStatusOutputFull(inv);
      return;
    }

    if (!canStartProcess(b, inv, recipe)) {
      revertConsumedItem(b, inv);
      removeMapBlock(b);
      updateStatusInvalidInput(inv);
      return;
    }

    // Do not hide/reserve inputs while the machine has no power. Energy is charged only after the
    // complete recipe has been staged, preserving the newer no-wasted-energy behavior.
    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
      updateStatusConnectEnergy(inv, recipe.getOutput()[0]);
      return;
    }

    int previousProgress = getConsumedItems(b).values().stream().mapToInt(Integer::intValue).sum();
    if (!consumptionRecipe(b, inv)) {
      int progressCount = getConsumedItems(b).values().stream().mapToInt(Integer::intValue).sum();
      int attempts = progressCount > previousProgress
          ? 0
          : attemptCount.getOrDefault(b, 0) + 1;

      if (attempts >= Supreme.getSupremeOptions().getMachineMaxAttemptConsumed()) {
        revertConsumedItem(b, inv);
        removeMapBlock(b);
        updateStatusInvalidInput(inv);
        return;
      }

      attemptCount.put(b, attempts);
      int totalProgress = Arrays.stream(recipe.getInput())
          .filter(java.util.Objects::nonNull)
          .mapToInt(ItemStack::getAmount)
          .sum();
      updateStatusLoadMaterial(inv, recipe.getOutput()[0], attempts, progressCount,
          totalProgress);
      return;
    }

    removeCharge(b.getLocation(), getEnergyConsumption());
    onProcessStarted(b, inv, recipe);
    progressTime.put(b, Math.max(ticksRemaining - getSpeed(), 0));
    attemptCount.put(b, 0);
  }

  private void revertConsumedItem(Block b, BlockMenu inv) {
    Map<ItemStack, Integer> consumedItems = consumedItemsMap.get(b);
    if (consumedItems == null || consumedItems.isEmpty()) {
      return;
    }

    for (Map.Entry<ItemStack, Integer> consumedEntry : consumedItems.entrySet()) {
      ItemStack consumedItem = consumedEntry.getKey();
      int amount = consumedEntry.getValue();
      if (consumedItem == null || consumedItem.getType().isAir()) {
        continue;
      }

      int maxStackSize = consumedItem.getMaxStackSize();
      while (amount > 0) {
        int stackSize = Math.min(maxStackSize, amount);
        ItemStack returnItem = consumedItem.clone();
        returnItem.setAmount(stackSize);
        ItemStack leftover = inv.pushItem(returnItem, getInputSlots());
        if (leftover != null && b.getWorld() != null) {
          b.getWorld().dropItemNaturally(b.getLocation(), leftover);
        }
        amount -= stackSize;
      }
    }
    consumedItems.clear();
  }

  private void dropConsumedItems(Block block) {
    Map<ItemStack, Integer> consumedItems = consumedItemsMap.get(block);
    if (consumedItems == null || consumedItems.isEmpty() || block.getWorld() == null) {
      return;
    }

    for (Map.Entry<ItemStack, Integer> entry : consumedItems.entrySet()) {
      ItemStack item = entry.getKey();
      int amount = entry.getValue();
      if (item == null || item.getType().isAir()) {
        continue;
      }
      while (amount > 0) {
        int stackSize = Math.min(item.getMaxStackSize(), amount);
        ItemStack dropped = item.clone();
        dropped.setAmount(stackSize);
        block.getWorld().dropItemNaturally(block.getLocation(), dropped);
        amount -= stackSize;
      }
    }
    consumedItems.clear();
  }

  private void endProcessTicks(Block b, BlockMenu inv, ItemStack[] result) {
    if (notHasSpaceOutput(inv, result)) {
      updateStatusOutputFull(inv);
      return;
    }
    SupremeInventoryUtils.pushAll(inv, getOutputSlots(), result);
    removeMapBlock(b);
    updateStatusReset(inv);
  }

  private void doProcessTicks(Block b, BlockMenu inv, int ticks, int ticksRemaining,
      ItemStack result) {
    progressTime.put(b, Math.max(ticksRemaining - getSpeed(), 0));
    ChestMenuUtils.updateProgressbar(inv, getStatusSlot(), ticksRemaining, ticks, result);
  }

  /**
   * Reserves whatever recipe material is currently available and credits it toward later attempts.
   * This lets recipes larger than one stack progress as: consume stack -> cargo refills one stack ->
   * consume again, without asking Networks to keep several stacks of each ingredient in the machine.
   */
  private boolean consumptionRecipe(Block b, BlockMenu inv) {
    MachineRecipe processingRecipe = getProcessing(b);
    if (processingRecipe == null) {
      return false;
    }

    Map<ItemStack, Integer> requiredItems = groupSimilarItems(processingRecipe.getInput());
    Map<ItemStack, Integer> consumedItems = getConsumedItems(b);
    boolean complete = true;

    for (Map.Entry<ItemStack, Integer> entry : requiredItems.entrySet()) {
      ItemStack requiredItem = entry.getKey();
      int requiredAmount = entry.getValue();
      int alreadyConsumed = countConsumed(consumedItems, requiredItem);
      int remaining = Math.max(requiredAmount - alreadyConsumed, 0);

      if (remaining == 0) {
        continue;
      }

      for (int slot : getInputSlots()) {
        ItemStack slotItem = inv.getItemInSlot(slot);
        if (slotItem == null || slotItem.getType().isAir()
            || !SlimefunUtils.isItemSimilar(slotItem, requiredItem, false, false)) {
          continue;
        }

        int amountToConsume = Math.min(slotItem.getAmount(), remaining);
        if (amountToConsume > 0) {
          ItemStack consumed = slotItem.clone();
          inv.consumeItem(slot, amountToConsume);
          mergeSimilar(consumedItems, consumed, amountToConsume);
          remaining -= amountToConsume;
        }
        if (remaining == 0) {
          break;
        }
      }

      if (remaining > 0) {
        complete = false;
      }
    }
    return complete;
  }

  private int countConsumed(Map<ItemStack, Integer> consumedItems, ItemStack requiredItem) {
    int amount = 0;
    for (Map.Entry<ItemStack, Integer> entry : consumedItems.entrySet()) {
      if (SlimefunUtils.isItemSimilar(entry.getKey(), requiredItem, false, false)) {
        amount += entry.getValue();
      }
    }
    return amount;
  }

  private boolean matchingRecipe(ItemStack[] recipe, BlockMenu inv) {
    // Starting requires one visible item of each distinct ingredient. Larger quantities can then be
    // staged one natural stack at a time and remain credited until the recipe is complete.
    for (ItemStack required : groupSimilarItems(recipe).keySet()) {
      boolean present = false;
      for (int slot : getInputSlots()) {
        ItemStack itemInSlot = inv.getItemInSlot(slot);
        if (itemInSlot != null
            && SlimefunUtils.isItemSimilar(itemInSlot, required, false, false)) {
          present = true;
          break;
        }
      }
      if (!present) {
        return false;
      }
    }
    return true;
  }

  private Map<ItemStack, Integer> groupSimilarItems(ItemStack[] items) {
    Map<ItemStack, Integer> grouped = new LinkedHashMap<>();
    if (items == null) {
      return grouped;
    }
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
        mergeSimilar(grouped, item, item.getAmount());
      }
    }
    return grouped;
  }

  private void mergeSimilar(Map<ItemStack, Integer> items, ItemStack item, int amount) {
    for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
      if (SlimefunUtils.isItemSimilar(entry.getKey(), item, false, false)) {
        entry.setValue(entry.getValue() + amount);
        return;
      }
    }
    ItemStack key = item.clone();
    key.setAmount(1);
    items.put(key, amount);
  }

  private ItemStack getDisplayOrInfo(ItemStack itemStack, String name) {
    return new CustomItemStack(
        itemStack != null ? itemStack : new ItemStack(Material.BLACK_STAINED_GLASS_PANE), name);
  }

  private ItemStack getDisplayOrWarn(ItemStack itemStack, String name) {
    return new CustomItemStack(
        itemStack != null ? itemStack : new ItemStack(Material.RED_STAINED_GLASS_PANE), name);
  }
}
