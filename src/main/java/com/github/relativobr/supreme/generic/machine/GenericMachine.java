package com.github.relativobr.supreme.generic.machine;

import static java.util.Objects.nonNull;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import com.github.relativobr.supreme.generic.recipe.InventoryRecipe;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeMachineStateCodec;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Shared implementation for Supreme's container machines.
 *
 * <p>Supreme Legacy stages large recipes one legal stack per ingredient at a time. Cargo and
 * Networks can therefore feed 64, let the machine reserve that batch internally, and refill the
 * released slot until the complete recipe has been secured. Reserved inputs and active processing
 * state are persisted in Slimefun block data and are restored after restarts.</p>
 */
public class GenericMachine extends AContainer implements NotHopperable, RecipeDisplayItem {

  private static final int MAX_STAGED_BATCH = 64;
  private static final long IDLE_BACKOFF_TICKS = 4L;
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;

  // AContainer ticks asynchronously on Paper/Purpur, while transport and block-break paths may
  // inspect or clear the same placed-machine state from another server-owned thread.
  private final Map<Block, MachineRecipe> processing = new ConcurrentHashMap<>();
  private final Map<Block, Integer> progressTime = new ConcurrentHashMap<>();
  private final Map<Block, Map<ItemStack, Integer>> consumedItemsMap = new ConcurrentHashMap<>();
  private final Map<Block, Integer> attemptCount = new ConcurrentHashMap<>();
  private final Map<Block, Long> heavyCheckAfter = new ConcurrentHashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new ConcurrentHashMap<>();
  private final Map<Block, Map<ItemStack, Integer>> activeRequiredItems = new ConcurrentHashMap<>();
  private final List<RecipeCache> recipeCaches = new ArrayList<>();
  private final Map<Material, List<RecipeCache>> transportRecipeIndex = new HashMap<>();
  public final List<AbstractItemRecipe> machineRecipes = new ArrayList<>();
  private Integer timeProcess;
  private String machineIdentifier = "MediumContainerMachine";

  private static final class RecipeCache {

    private final AbstractItemRecipe recipe;
    private final ItemStack[] input;
    private final Map<ItemStack, Integer> requiredItems;

    private RecipeCache(AbstractItemRecipe recipe, ItemStack[] input,
        Map<ItemStack, Integer> requiredItems) {
      this.recipe = recipe;
      this.input = input;
      this.requiredItems = requiredItems;
    }
  }

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
        return flow == ItemTransportFlow.WITHDRAW ? getOutputSlots() : getInputSlots();
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
   * Exposes at most one physical stack slot for an ingredient while a recipe is being staged.
   *
   * <p>This is the important compatibility behavior for Networks/Cargo: a recipe requiring hundreds
   * of an ingredient does not advertise enough empty slots for the entire quantity. It advertises one
   * legal stack, the ticker reserves that stack, and then the same physical slot becomes available for
   * the next delivery.</p>
   */
  private int[] getRecipeAwareInsertSlots(DirtyChestMenu menu, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return new int[0];
    }

    if (machineRecipes.isEmpty()) {
      return getInputSlots();
    }

    Map<ItemStack, Integer> requiredItems = null;
    Map<ItemStack, Integer> reservedItems = Map.of();

    if (menu instanceof BlockMenu blockMenu) {
      Block block = blockMenu.getBlock();
      restorePersistentStateIfNeeded(block, blockMenu);
      MachineRecipe activeRecipe = getProcessing(block);
      if (activeRecipe != null) {
        if (isRollbackPending(block)) {
          // During rollback, transport must not steal newly freed input space before the staged
          // ingredients have been restored. The status slot is permanently occupied and therefore
          // acts as a zero-capacity sentinel even for transport implementations that broaden an
          // empty slot response back to the machine's normal inputs.
          return new int[]{getStatusSlot()};
        }
        requiredItems = getActiveRequiredItems(block, activeRecipe.getInput());
        reservedItems = getConsumedItems(block);
      }
    }

    if (requiredItems == null) {
      RecipeCache selectedRecipe = findTransportRecipe(menu, item);
      if (selectedRecipe == null) {
        return new int[0];
      }
      requiredItems = selectedRecipe.requiredItems;
    }

    ItemStack requiredTemplate = null;
    int requiredAmount = 0;
    for (Map.Entry<ItemStack, Integer> entry : requiredItems.entrySet()) {
      if (SlimefunUtils.isItemSimilar(entry.getKey(), item, false, false)) {
        requiredTemplate = entry.getKey();
        requiredAmount = entry.getValue();
        break;
      }
    }

    if (requiredTemplate == null || requiredAmount <= 0) {
      return new int[0];
    }

    int reservedAmount = countMapAmount(reservedItems, requiredTemplate);
    if (reservedAmount >= requiredAmount) {
      // Networks Expansion has a compatibility fallback for older Supreme builds that may broaden an
      // empty result back to every physical input slot. Prefer a full matching slot as the intentional
      // zero-capacity sentinel; when no such stack is visible, use the permanently occupied status
      // slot so late same-item deliveries still cannot refill the machine while processing.
      int fullMatchingSlot = findFullMatchingInputSlot(menu, requiredTemplate);
      return new int[]{fullMatchingSlot >= 0 ? fullMatchingSlot : getStatusSlot()};
    }

    int bestPartialSlot = -1;
    int bestPartialAmount = -1;
    int fullMatchingSlot = -1;
    int firstEmptySlot = -1;

    for (int slot : getInputSlots()) {
      ItemStack stack = menu.getItemInSlot(slot);
      if (stack == null || stack.getType().isAir()) {
        if (firstEmptySlot < 0) {
          firstEmptySlot = slot;
        }
        continue;
      }

      if (!SlimefunUtils.isItemSimilar(stack, requiredTemplate, false, false)) {
        continue;
      }

      if (stack.getAmount() < stack.getMaxStackSize()) {
        if (stack.getAmount() > bestPartialAmount) {
          bestPartialSlot = slot;
          bestPartialAmount = stack.getAmount();
        }
      } else if (fullMatchingSlot < 0) {
        fullMatchingSlot = slot;
      }
    }

    if (bestPartialSlot >= 0) {
      return new int[]{bestPartialSlot};
    }

    // A full matching slot is deliberately returned as a zero-capacity sentinel until the ticker
    // reserves it. This prevents a transport implementation from claiming a second empty slot.
    if (fullMatchingSlot >= 0) {
      return new int[]{fullMatchingSlot};
    }

    return firstEmptySlot >= 0 ? new int[]{firstEmptySlot} : new int[0];
  }

  private int findFullMatchingInputSlot(DirtyChestMenu menu, ItemStack requiredTemplate) {
    for (int slot : getInputSlots()) {
      ItemStack stack = menu.getItemInSlot(slot);
      if (stack != null
          && !stack.getType().isAir()
          && stack.getAmount() >= stack.getMaxStackSize()
          && SlimefunUtils.isItemSimilar(stack, requiredTemplate, false, false)) {
        return slot;
      }
    }
    return -1;
  }

  /**
   * Selects the first recipe containing the incoming item that is compatible with everything already
   * present in the machine. When several recipes are possible, prefer the one with the most distinct
   * ingredients already represented in the input inventory.
   */
  private RecipeCache findTransportRecipe(DirtyChestMenu menu, ItemStack incoming) {
    List<RecipeCache> candidates = transportRecipeIndex.get(incoming.getType());
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }

    RecipeCache bestRecipe = null;
    int bestMatchedIngredients = -1;

    for (RecipeCache recipe : candidates) {
      Map<ItemStack, Integer> requiredItems = recipe.requiredItems;
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
        bestRecipe = recipe;
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
          restorePersistentStateIfNeeded(b, inv);
          if (!revertConsumedItem(b, inv)) {
            // A deliberate block break is allowed to spill only the still-hidden remainder. Normal
            // machine rollback never uses this path and instead waits for inventory room.
            dropConsumedItems(b);
          }
          inv.dropItems(b.getLocation(), getInputSlots());
          inv.dropItems(b.getLocation(), getOutputSlots());
        } else {
          if (!consumedItemsMap.containsKey(b) && SupremeMachineStateCodec.hasState(b)) {
            consumedItemsMap.put(b, SupremeMachineStateCodec.loadConsumedOnly(b));
          }
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
   * Gives specialized machines a chance to validate external state immediately before a fully
   * reserved recipe begins processing. Returning {@code false} returns every reserved ingredient.
   */
  protected boolean canStartProcess(Block block, BlockMenu menu, MachineRecipe recipe) {
    return true;
  }

  /** Called once after the complete recipe input and initial energy charge have been secured. */
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

  protected void updateStatusRollbackBlocked(BlockMenu menu) {
    menu.replaceExistingItem(getStatusSlot(),
        getDisplayOrWarn(null, "&cInput full - clear a slot to recover staged material"));
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
        "&7Reserved/available: &e" + progressCount + " &7/ &e" + totalProgress, "");
    menu.replaceExistingItem(getStatusSlot(), infoDetail);
  }

  @Nonnull
  public GenericMachine setMachineRecipes(@Nonnull List<AbstractItemRecipe> recipes) {
    machineRecipes.clear();
    machineRecipes.addAll(recipes);
    rebuildRecipeCaches();
    return this;
  }

  private void rebuildRecipeCaches() {
    recipeCaches.clear();
    transportRecipeIndex.clear();

    for (AbstractItemRecipe recipe : machineRecipes) {
      ItemStack[] input = recipe.getInputNotNull();
      Map<ItemStack, Integer> requiredItems = groupSimilarItems(input);
      RecipeCache cache = new RecipeCache(recipe, input, requiredItems);
      recipeCaches.add(cache);

      Set<Material> indexedMaterials = new HashSet<>();
      for (ItemStack required : requiredItems.keySet()) {
        Material type = required.getType();
        if (indexedMaterials.add(type)) {
          transportRecipeIndex.computeIfAbsent(type, ignored -> new ArrayList<>()).add(cache);
        }
      }
    }
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

    restorePersistentStateIfNeeded(b, inv);

    long gameTime = b.getWorld().getGameTime();
    if (gameTime < heavyCheckAfter.getOrDefault(b, 0L)) {
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
    for (RecipeCache recipe : recipeCaches) {
      if (matchingRecipe(recipe.requiredItems, inv)) {
        return new MachineRecipe(getTimeProcess(), recipe.input, recipe.recipe.getOutputNotNull());
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
    return consumedItemsMap.computeIfAbsent(b, ignored -> new ConcurrentHashMap<>());
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
      activeRequiredItems.put(b, groupSimilarItems(next.getInput()));
      progressTime.put(b, next.getTicks());
      consumedItemsMap.put(b, new ConcurrentHashMap<>());
      attemptCount.put(b, 0);
      heavyCheckAfter.remove(b);
      lastProgressCheckpoint.put(b, next.getTicks());
      persistState(b);
    } else {
      if (getInputSlots().length <= 5) {
        updateStatusReset(inv);
      } else {
        updateStatusInvalidInput(inv);
      }
      backoff(b);
    }
  }

  protected final void removeMapBlock(Block b) {
    progressTime.remove(b);
    processing.remove(b);
    attemptCount.remove(b);
    consumedItemsMap.remove(b);
    heavyCheckAfter.remove(b);
    lastProgressCheckpoint.remove(b);
    activeRequiredItems.remove(b);
    SupremeMachineStateCodec.clear(b);
  }

  private void doProcessing(Block b, BlockMenu inv) {
    MachineRecipe recipe = getProcessing(b);
    if (recipe == null) {
      removeMapBlock(b);
      return;
    }

    ItemStack[] result = recipe.getOutput();
    if (result == null || result.length == 0) {
      markRollbackPending(b);
      if (!revertConsumedItem(b, inv)) {
        updateStatusRollbackBlocked(inv);
        backoff(b);
        return;
      }
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
      backoff(b);
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

    // Never hide more inputs while the finished recipe has nowhere to go.
    if (notHasSpaceOutput(inv, recipe.getOutput())) {
      updateStatusOutputFull(inv);
      backoff(b);
      return;
    }

    if (isRollbackPending(b)) {
      if (!revertConsumedItem(b, inv)) {
        updateStatusRollbackBlocked(inv);
        backoff(b);
        return;
      }
      removeMapBlock(b);
      updateStatusInvalidInput(inv);
      return;
    }

    int stagedThisTick = reserveAvailableInputBatch(b, inv, recipe.getInput());
    if (!hasAllReservedInputs(b, recipe.getInput())) {
      int attempts = stagedThisTick > 0 ? 0 : attemptCount.getOrDefault(b, 0) + 1;
      int maxAttempts = getMaxAttemptConsumed();

      if (attempts >= maxAttempts) {
        // Enter a persistent rollback state. Transport is blocked while rollback is pending, and
        // reserved inputs stay hidden until the machine can restore all of them without spilling.
        attemptCount.put(b, maxAttempts);
        SupremeMachineStateCodec.saveAttempts(b, maxAttempts);
        if (!revertConsumedItem(b, inv)) {
          updateStatusRollbackBlocked(inv);
          backoff(b);
          return;
        }
        removeMapBlock(b);
        updateStatusInvalidInput(inv);
        return;
      }

      attemptCount.put(b, attempts);
      SupremeMachineStateCodec.saveAttempts(b, attempts);
      int progressCount = countReservedAndVisibleRecipeInputs(b, inv, recipe.getInput());
      updateStatusLoadMaterial(inv, recipe.getOutput()[0], attempts, progressCount,
          totalRecipeAmount(b, recipe.getInput()));
      if (stagedThisTick == 0) {
        backoff(b);
      }
      return;
    }

    if (!canStartProcess(b, inv, recipe)) {
      markRollbackPending(b);
      if (!revertConsumedItem(b, inv)) {
        updateStatusRollbackBlocked(inv);
        backoff(b);
        return;
      }
      removeMapBlock(b);
      updateStatusInvalidInput(inv);
      return;
    }

    if (notHasSpaceOutput(inv, recipe.getOutput())) {
      updateStatusOutputFull(inv);
      backoff(b);
      return;
    }

    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
      updateStatusConnectEnergy(inv, recipe.getOutput()[0]);
      backoff(b);
      return;
    }

    removeCharge(b.getLocation(), getEnergyConsumption());
    onProcessStarted(b, inv, recipe);
    int nextProgress = Math.max(ticksRemaining - getSpeed(), 0);
    progressTime.put(b, nextProgress);
    attemptCount.put(b, 0);
    heavyCheckAfter.remove(b);
    persistState(b);
  }

  private int getMaxAttemptConsumed() {
    return Math.max(1, Supreme.getSupremeOptions().getMachineMaxAttemptConsumed());
  }

  private boolean isRollbackPending(Block b) {
    Map<ItemStack, Integer> consumedItems = consumedItemsMap.get(b);
    return attemptCount.getOrDefault(b, 0) >= getMaxAttemptConsumed()
        && consumedItems != null && !consumedItems.isEmpty();
  }

  private void markRollbackPending(Block b) {
    int maxAttempts = getMaxAttemptConsumed();
    attemptCount.put(b, maxAttempts);
    SupremeMachineStateCodec.saveAttempts(b, maxAttempts);
  }

  /**
   * Reserves at most one legal stack of every still-missing ingredient on this ticker pass.
   */
  private int reserveAvailableInputBatch(Block b, BlockMenu inv, ItemStack[] recipe) {
    Map<ItemStack, Integer> requiredItems = getActiveRequiredItems(b, recipe);
    Map<ItemStack, Integer> consumedItems = getConsumedItems(b);
    int totalConsumedNow = 0;

    for (Map.Entry<ItemStack, Integer> entry : requiredItems.entrySet()) {
      ItemStack requiredItem = entry.getKey();
      int requiredAmount = entry.getValue();
      int alreadyReserved = countMapAmount(consumedItems, requiredItem);
      int remainingRequired = requiredAmount - alreadyReserved;
      if (remainingRequired <= 0) {
        continue;
      }

      int legalBatch = Math.max(1,
          Math.min(MAX_STAGED_BATCH, requiredItem.getMaxStackSize()));
      int batchRemaining = Math.min(remainingRequired, legalBatch);

      for (int slot : getInputSlots()) {
        ItemStack slotItem = inv.getItemInSlot(slot);
        if (slotItem == null || slotItem.getType().isAir()
            || !SlimefunUtils.isItemSimilar(slotItem, requiredItem, false, false)) {
          continue;
        }

        int amountToConsume = Math.min(slotItem.getAmount(), batchRemaining);
        if (amountToConsume > 0) {
          ItemStack consumed = slotItem.clone();
          consumed.setAmount(1);
          inv.consumeItem(slot, amountToConsume);
          mergeSimilar(consumedItems, consumed, amountToConsume);
          batchRemaining -= amountToConsume;
          totalConsumedNow += amountToConsume;
        }
        if (batchRemaining <= 0) {
          break;
        }
      }
    }

    if (totalConsumedNow > 0) {
      heavyCheckAfter.remove(b);
      persistState(b);
    }
    return totalConsumedNow;
  }

  /**
   * Attempts to restore every hidden staged input to the visible input inventory.
   *
   * <p>Normal rollback is deliberately lossless and spill-free. If the visible inputs do not have
   * enough capacity, nothing is moved and the machine stays in persistent rollback mode. A user can
   * clear a slot and the ticker will retry. If a concurrent transport race changes the inventory
   * after the capacity preflight, only the still-hidden remainder stays reserved for the next retry.</p>
   */
  private boolean revertConsumedItem(Block b, BlockMenu inv) {
    Map<ItemStack, Integer> consumedItems = consumedItemsMap.get(b);
    if (consumedItems == null || consumedItems.isEmpty()) {
      return true;
    }

    if (!canReturnConsumedMap(inv, consumedItems)) {
      return false;
    }

    Map<ItemStack, Integer> leftovers = returnConsumedMap(inv, consumedItems);
    consumedItems.clear();
    consumedItems.putAll(leftovers);

    if (!consumedItems.isEmpty()) {
      // A transport write raced the preflight. Keep only the still-hidden remainder and retry later;
      // attemptCount remains at the rollback sentinel so no new recipe inputs are reserved meanwhile.
      persistState(b);
      return false;
    }
    return true;
  }

  private boolean canReturnConsumedMap(BlockMenu inv, Map<ItemStack, Integer> consumedItems) {
    List<ItemStack> simulatedSlots = new ArrayList<>(getInputSlots().length);
    for (int slot : getInputSlots()) {
      ItemStack existing = inv.getItemInSlot(slot);
      simulatedSlots.add(existing == null || existing.getType().isAir() ? null : existing.clone());
    }

    for (Map.Entry<ItemStack, Integer> consumedEntry : consumedItems.entrySet()) {
      ItemStack consumedItem = consumedEntry.getKey();
      int remaining = consumedEntry.getValue();
      if (consumedItem == null || consumedItem.getType().isAir() || remaining <= 0) {
        continue;
      }

      for (ItemStack simulated : simulatedSlots) {
        if (remaining <= 0) {
          break;
        }
        if (simulated == null || simulated.getType().isAir()
            || !SlimefunUtils.isItemSimilar(simulated, consumedItem, false, false)) {
          continue;
        }
        int capacity = Math.max(0, simulated.getMaxStackSize() - simulated.getAmount());
        int moved = Math.min(capacity, remaining);
        simulated.setAmount(simulated.getAmount() + moved);
        remaining -= moved;
      }

      for (int i = 0; i < simulatedSlots.size() && remaining > 0; i++) {
        if (simulatedSlots.get(i) != null) {
          continue;
        }
        int moved = Math.min(Math.max(1, consumedItem.getMaxStackSize()), remaining);
        ItemStack simulated = consumedItem.clone();
        simulated.setAmount(moved);
        simulatedSlots.set(i, simulated);
        remaining -= moved;
      }

      if (remaining > 0) {
        return false;
      }
    }
    return true;
  }

  private Map<ItemStack, Integer> returnConsumedMap(BlockMenu inv,
      Map<ItemStack, Integer> consumedItems) {
    Map<ItemStack, Integer> leftovers = new LinkedHashMap<>();

    for (Map.Entry<ItemStack, Integer> consumedEntry : consumedItems.entrySet()) {
      ItemStack consumedItem = consumedEntry.getKey();
      int amount = consumedEntry.getValue();
      if (consumedItem == null || consumedItem.getType().isAir()) {
        continue;
      }

      int maxStackSize = Math.max(1, consumedItem.getMaxStackSize());
      while (amount > 0) {
        int stackSize = Math.min(maxStackSize, amount);
        ItemStack returnItem = consumedItem.clone();
        returnItem.setAmount(stackSize);
        ItemStack leftover = inv.pushItem(returnItem, getInputSlots());
        if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
          mergeSimilar(leftovers, leftover, leftover.getAmount());
        }
        amount -= stackSize;
      }
    }
    return leftovers;
  }

  /**
   * Slimefun's normal AContainer ticker is asynchronous on Paper/Purpur. Entity creation is not, so
   * forced recovery/block-break drops must hop to the owning region instead of calling
   * World#dropItemNaturally from the ticker thread. Normal recipe rollback never drops items.
   */
  private void dropItemNaturallySafe(Block block, ItemStack item) {
    if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
      return;
    }

    Location location = block.getLocation();
    ItemStack dropped = item.clone();
    Bukkit.getRegionScheduler().execute(Supreme.inst(), location,
        () -> location.getWorld().dropItemNaturally(location, dropped));
  }

  private void dropItemMapSafely(Block block, Map<ItemStack, Integer> items) {
    if (items == null || items.isEmpty() || block.getWorld() == null) {
      return;
    }

    for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
      ItemStack item = entry.getKey();
      int amount = entry.getValue();
      if (item == null || item.getType().isAir()) {
        continue;
      }
      while (amount > 0) {
        int stackSize = Math.min(Math.max(1, item.getMaxStackSize()), amount);
        ItemStack dropped = item.clone();
        dropped.setAmount(stackSize);
        dropItemNaturallySafe(block, dropped);
        amount -= stackSize;
      }
    }
  }

  private void dropConsumedItems(Block block) {
    Map<ItemStack, Integer> consumedItems = consumedItemsMap.get(block);
    if (consumedItems == null || consumedItems.isEmpty() || block.getWorld() == null) {
      return;
    }

    dropItemMapSafely(block, consumedItems);
    consumedItems.clear();
  }

  private void endProcessTicks(Block b, BlockMenu inv, ItemStack[] result) {
    if (notHasSpaceOutput(inv, result)) {
      updateStatusOutputFull(inv);
      backoff(b);
      return;
    }
    SupremeInventoryUtils.pushAll(inv, getOutputSlots(), result);
    removeMapBlock(b);
    updateStatusReset(inv);
  }

  private void doProcessTicks(Block b, BlockMenu inv, int ticks, int ticksRemaining,
      ItemStack result) {
    int nextProgress = Math.max(ticksRemaining - getSpeed(), 0);
    progressTime.put(b, nextProgress);
    ChestMenuUtils.updateProgressbar(inv, getStatusSlot(), ticksRemaining, ticks, result);

    int previousCheckpoint = lastProgressCheckpoint.getOrDefault(b, ticks);
    if (nextProgress <= 0
        || Math.abs(previousCheckpoint - nextProgress) >= PROGRESS_CHECKPOINT_INTERVAL) {
      SupremeMachineStateCodec.saveProgress(b, nextProgress);
      lastProgressCheckpoint.put(b, nextProgress);
    }
  }

  private boolean hasAllReservedInputs(Block b, ItemStack[] recipe) {
    Map<ItemStack, Integer> consumedItems = getConsumedItems(b);
    for (Map.Entry<ItemStack, Integer> entry : getActiveRequiredItems(b, recipe).entrySet()) {
      if (countMapAmount(consumedItems, entry.getKey()) < entry.getValue()) {
        return false;
      }
    }
    return true;
  }

  private int countReservedAndVisibleRecipeInputs(Block b, BlockMenu inv, ItemStack[] recipe) {
    int available = 0;
    Map<ItemStack, Integer> consumedItems = getConsumedItems(b);
    for (Map.Entry<ItemStack, Integer> entry : getActiveRequiredItems(b, recipe).entrySet()) {
      int reserved = countMapAmount(consumedItems, entry.getKey());
      int visible = countAvailable(inv, entry.getKey());
      available += Math.min(entry.getValue(), reserved + visible);
    }
    return available;
  }

  private int totalRecipeAmount(Block b, ItemStack[] recipe) {
    int total = 0;
    for (int amount : getActiveRequiredItems(b, recipe).values()) {
      total += amount;
    }
    return total;
  }

  private int countAvailable(BlockMenu inv, ItemStack requiredItem) {
    int amount = 0;
    for (int slot : getInputSlots()) {
      ItemStack slotItem = inv.getItemInSlot(slot);
      if (slotItem != null && !slotItem.getType().isAir()
          && SlimefunUtils.isItemSimilar(slotItem, requiredItem, false, false)) {
        amount += slotItem.getAmount();
      }
    }
    return amount;
  }

  private boolean matchingRecipe(Map<ItemStack, Integer> requiredItems, BlockMenu inv) {
    // One visible item of each distinct ingredient is enough to select a recipe. The staged
    // reservation engine then consumes one legal stack at a time until the full quantities arrive.
    for (ItemStack required : requiredItems.keySet()) {
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

  private Map<ItemStack, Integer> getActiveRequiredItems(Block block, ItemStack[] recipe) {
    return activeRequiredItems.computeIfAbsent(block, ignored -> groupSimilarItems(recipe));
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

  private int countMapAmount(Map<ItemStack, Integer> items, ItemStack target) {
    if (items == null || target == null) {
      return 0;
    }
    int amount = 0;
    for (Map.Entry<ItemStack, Integer> entry : items.entrySet()) {
      if (SlimefunUtils.isItemSimilar(entry.getKey(), target, false, false)) {
        amount += entry.getValue();
      }
    }
    return amount;
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

  private void persistState(Block b) {
    MachineRecipe recipe = getProcessing(b);
    if (recipe == null) {
      SupremeMachineStateCodec.clear(b);
      return;
    }
    SupremeMachineStateCodec.save(b, recipe, getProgressTime(b),
        attemptCount.getOrDefault(b, 0), getConsumedItems(b));
    lastProgressCheckpoint.put(b, getProgressTime(b));
  }

  private boolean restorePersistentStateIfNeeded(Block b, BlockMenu inv) {
    if (processing.containsKey(b)) {
      return true;
    }
    if (!SupremeMachineStateCodec.hasState(b)) {
      return false;
    }

    Optional<SupremeMachineStateCodec.State> restored = SupremeMachineStateCodec.load(b);
    if (restored.isPresent()) {
      SupremeMachineStateCodec.State state = restored.get();
      processing.put(b, state.recipe());
      activeRequiredItems.put(b, groupSimilarItems(state.recipe().getInput()));
      progressTime.put(b, Math.max(0, state.progress()));
      attemptCount.put(b, Math.max(0, state.attempts()));
      consumedItemsMap.put(b, new ConcurrentHashMap<>(state.consumedItems()));
      lastProgressCheckpoint.put(b, Math.max(0, state.progress()));
      heavyCheckAfter.remove(b);
      return true;
    }

    // If the recipe payload was damaged but the reserved-item payload is still readable, return
    // those items immediately rather than silently discarding them. A corrupt recipe cannot remain
    // pending, so only this recovery path is allowed to spill an unreturnable remainder safely.
    Map<ItemStack, Integer> recoverable = SupremeMachineStateCodec.loadConsumedOnly(b);
    if (!recoverable.isEmpty()) {
      Map<ItemStack, Integer> leftovers = returnConsumedMap(inv, recoverable);
      dropItemMapSafely(b, leftovers);
    }
    SupremeMachineStateCodec.clear(b);
    return false;
  }

  private void backoff(Block b) {
    heavyCheckAfter.put(b, b.getWorld().getGameTime() + IDLE_BACKOFF_TICKS);
  }

  /**
   * Returns human-readable live state for {@code /supreme doctor machine}.
   */
  public List<String> getMachineDiagnosticLines(Block block) {
    List<String> lines = new ArrayList<>();
    BlockMenu inv = BlockStorage.getInventory(block);
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restorePersistentStateIfNeeded(block, inv);
    MachineRecipe recipe = getProcessing(block);
    lines.add("Machine: " + getId() + " (" + getMachineIdentifier() + ")");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + getEnergyConsumption() + " J/tick");

    if (recipe == null) {
      lines.add("State: IDLE / waiting for a recipe");
      return lines;
    }

    int progress = getProgressTime(block);
    String state;
    if (isRollbackPending(block)) {
      state = "ROLLBACK WAITING FOR INPUT SPACE";
    } else if (notHasSpaceOutput(inv, recipe.getOutput())) {
      state = "OUTPUT FULL";
    } else if (progress == recipe.getTicks() && !hasAllReservedInputs(block, recipe.getInput())) {
      state = "STAGING INPUTS";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption()) {
      state = "WAITING FOR POWER";
    } else if (progress <= 0) {
      state = "READY TO OUTPUT";
    } else {
      state = "PROCESSING";
    }
    lines.add("State: " + state + " | Progress: " + progress + "/" + recipe.getTicks());
    lines.add("Output space: " + (!notHasSpaceOutput(inv, recipe.getOutput()) ? "available" : "full"));
    if (state.equals("STAGING INPUTS")) {
      lines.add("No-progress attempts: " + attemptCount.getOrDefault(block, 0) + "/"
          + getMaxAttemptConsumed() + " (checks back off by " + IDLE_BACKOFF_TICKS + " ticks)");
    }

    Map<ItemStack, Integer> reserved = getConsumedItems(block);
    for (Map.Entry<ItemStack, Integer> entry : getActiveRequiredItems(block, recipe.getInput()).entrySet()) {
      ItemStack ingredient = entry.getKey();
      int required = entry.getValue();
      int reservedAmount = countMapAmount(reserved, ingredient);
      int visible = countAvailable(inv, ingredient);
      lines.add(describeItem(ingredient) + ": required=" + required + " reserved="
          + reservedAmount + " visible=" + visible + " remaining="
          + Math.max(0, required - reservedAmount));
    }
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
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
