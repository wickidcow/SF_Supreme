package com.github.relativobr.supreme.machine.tech;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemContainerMachine;
import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import com.github.relativobr.supreme.generic.recipe.InventoryRecipe;
import com.github.relativobr.supreme.machine.tech.MobTechGeneric.MobTechType;
import com.github.relativobr.supreme.resource.SupremeComponents;
import com.github.relativobr.supreme.resource.mobtech.MobTech;
import com.github.relativobr.supreme.util.ItemGroups;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeItemStack;
import com.github.relativobr.supreme.util.UtilEnergy;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactivity;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.UnplaceableBlock;
import io.github.thebusybiscuit.slimefun4.libraries.commons.lang.Validate;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TechGenerator extends SimpleItemContainerMachine implements Radioactive {

  public static final SlimefunItemStack TECH_GENERATOR = new SupremeItemStack(
      "SUPREME_TECH_GENERATOR", Material.LOOM,
      "&bTech Generator", "", "&fUsing power and bees/golem/zombie, ", "&fslowly generates "
      + "materials.", "",
      LoreBuilder.radioactive(Radioactivity.LOW), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
      UtilEnergy.energyPowerPerSecond(2000), "", "&3Supreme Machine");

  public static final ItemStack[] RECIPE_TECH_GENERATOR = {SupremeComponents.INDUCTIVE_MACHINE,
      SupremeComponents.SYNTHETIC_RUBY, SupremeComponents.INDUCTIVE_MACHINE,
      SlimefunItems.REINFORCED_ALLOY_INGOT,
      new ItemStack(Material.LOOM), SlimefunItems.REINFORCED_ALLOY_INGOT,
      SupremeComponents.CARRIAGE_MACHINE,
      SlimefunItems.HEATING_COIL, SupremeComponents.CARRIAGE_MACHINE};

  public static final List<AbstractItemRecipe> receitasParaProduzir = new ArrayList<>();
  private static volatile List<AbstractItemRecipe> recipeShowCache;
  private static volatile List<AbstractItemRecipe> recipeProcessCache;
  private static volatile int recipeCacheSize = -1;

  private final Map<Block, ItemStack> processing = new HashMap<>();
  private final Map<Block, Integer> progressTime = new HashMap<>();
  private final Map<Block, RecipeMatch> recipeMatches = new HashMap<>();
  private final Map<Block, GenerationPlanCache> generationPlans = new HashMap<>();
  private final Map<Block, StaticStatus> staticStatuses = new HashMap<>();
  private NamespacedKey mobTechTierKey;
  private NamespacedKey mobTechTypeKey;
  private int speed = 1;

  private record RecipeMatch(@Nullable ItemStack observedInput, @Nullable ItemStack output) {}

  private record StaticStatus(Material material, String text) {}

  private record GenerationPlan(ItemStack[] outputs, int timeReduction, int consumption) {}

  private record GenerationPlanCache(
      ItemStack baseOutput,
      ItemStack[] upgradeSnapshots,
      int maxAmount,
      int baseConsumption,
      GenerationPlan plan) {}

  public TechGenerator(SlimefunItemStack item, ItemStack[] recipe) {
    super(ItemGroups.MACHINES_CATEGORY, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
  }

  public static synchronized void addRecipesToProcess(ItemStack input, ItemStack output) {
    receitasParaProduzir.add(new AbstractItemRecipe(input, output));
    invalidateRecipeCaches();
  }

  private static void invalidateRecipeCaches() {
    recipeShowCache = null;
    recipeProcessCache = null;
    recipeCacheSize = -1;
  }

  private static void ensureRecipeCaches() {
    if (recipeShowCache != null && recipeProcessCache != null
        && recipeCacheSize == receitasParaProduzir.size()) {
      return;
    }

    synchronized (TechGenerator.class) {
      if (recipeShowCache != null && recipeProcessCache != null
          && recipeCacheSize == receitasParaProduzir.size()) {
        return;
      }

      recipeShowCache = receitasParaProduzir.stream()
          .filter(recipe -> recipe.getInput() != null)
          .sorted((first, second) -> Integer.compare(first.getInput().length, second.getInput().length))
          .toList();
      recipeProcessCache = receitasParaProduzir.stream()
          .filter(recipe -> recipe.getInput() != null)
          .sorted((first, second) -> Integer.compare(second.getInput().length, first.getInput().length))
          .toList();
      recipeCacheSize = receitasParaProduzir.size();
    }
  }

  private static List<AbstractItemRecipe> cachedRecipesForDisplay() {
    ensureRecipeCaches();
    return recipeShowCache;
  }

  private static List<AbstractItemRecipe> cachedRecipesForProcessing() {
    ensureRecipeCaches();
    return recipeProcessCache;
  }

  public static void preSetup(Supreme plugin, SlimefunItemStack item, Material input,
      Material output) {
    preSetup(plugin, 1, item, new ItemStack(input), new ItemStack(output));
  }

  public static void preSetup(Supreme plugin, SlimefunItemStack item, ItemStack input,
      ItemStack output) {
    preSetup(plugin, 1, item, input, output);
  }

  public static void preSetup(Supreme plugin, int tierCard, SlimefunItemStack item, ItemStack input,
      ItemStack output) {
    preSetup(plugin, tierCard, item, input, input, output);
  }

  public static void preSetup(Supreme plugin, int tierCard, SlimefunItemStack item,
      ItemStack input1, ItemStack input2,
      ItemStack output) {
    new UnplaceableBlock(ItemGroups.CARDS_CATEGORY, item, RecipeType.ENHANCED_CRAFTING_TABLE,
        new ItemStack[]{input1.clone(), input2.clone(), input1.clone(), input2.clone(),
            getCardTier(tierCard), input2.clone(), input1.clone(), input2.clone(), input1.clone()})
        .register(plugin);
    TechGenerator.addRecipesToProcess(item, output);
  }

  @Nonnull
  private static ItemStack getCardTier(int tierCard) {
    if (tierCard >= 3) {
      return SupremeComponents.CENTER_CARD_ULTIMATE;
    } else if (tierCard == 2) {
      return SupremeComponents.CENTER_CARD_ADVANCED;
    } else {
      return SupremeComponents.CENTER_CARD_SIMPLE;
    }
  }

  private void setStaticStatus(Block block, BlockMenu menu, String text) {
    setStaticStatus(block, menu, Material.RED_STAINED_GLASS_PANE, text);
  }

  private void setStaticStatus(Block block, BlockMenu menu, Material material, String text) {
    StaticStatus previous = staticStatuses.get(block);
    if (previous != null && previous.material() == material && previous.text().equals(text)) {
      return;
    }

    for (int slot : InventoryRecipe.TECH_GENERATOR_PROGRESS_BAR_SLOT) {
      menu.replaceExistingItem(slot, new CustomItemStack(material, text));
    }
    staticStatuses.put(block, new StaticStatus(material, text));
  }

  private void clearStaticStatus(Block block) {
    staticStatuses.remove(block);
  }

  public List<AbstractItemRecipe> getRecipeShow() {
    return new ArrayList<>(cachedRecipesForDisplay());
  }

  public List<AbstractItemRecipe> getRecipeProcess() {
    return new ArrayList<>(cachedRecipesForProcessing());
  }

  @Override
  public int[] getInputSlots() {
    return InventoryRecipe.TECH_GENERATOR_INPUT_SLOTS;
  }

  @Override
  public int[] getOutputSlots() {
    return InventoryRecipe.TECH_GENERATOR_OUTPUT_SLOTS;
  }

  @Override
  protected void constructMenu(BlockMenuPreset preset) {
    for (int i : InventoryRecipe.TECH_GENERATOR_BORDER) {
      preset.addItem(i, new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_GENERATOR_BORDER_IN) {
      preset.addItem(i, new CustomItemStack(Material.BLUE_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_GENERATOR_BORDER_OUT) {
      preset.addItem(i, new CustomItemStack(Material.ORANGE_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_GENERATOR_PROGRESS_BAR_SLOT) {
      preset.addItem(i, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_GENERATOR_OUTPUT_SLOTS) {
      preset.addMenuClickHandler(i, new ChestMenu.AdvancedMenuClickHandler() {
        @Override
        public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
          return false;
        }

        @Override
        public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor,
            ClickAction action) {
          if (cursor == null) {
            return true;
          }
          return cursor.getType() == Material.AIR;
        }
      });
    }
  }

  @Override
  public void preRegister() {
    addItemHandler(new SupremeBlockTicker(true, this::tick));
  }

  public void tick(Block block) {
    BlockMenu inventory = BlockStorage.getInventory(block);
    if (inventory == null) {
      return;
    }

    ItemStack itemProduction = processing.get(block);
    if (itemProduction == null) {
      ItemStack validRecipeItem = validRecipeItem(block, inventory);
      if (validRecipeItem == null) {
        setStaticStatus(block, inventory, "&cCards unidentified");
        return;
      }

      GenerationPlan plan = getGenerationPlan(block, inventory, validRecipeItem);
      if (!SupremeInventoryUtils.canFit(inventory, getOutputSlots(), plan.outputs())) {
        setStaticStatus(block, inventory, "&cOutput is full");
        return;
      }

      processing.put(block, validRecipeItem.clone());
      progressTime.put(block, getTimeProcess() * 2);
      setStaticStatus(block, inventory, validRecipeItem.getType(), " ");
      return;
    }

    ItemStack currentRecipe = validRecipeItem(block, inventory);
    if (currentRecipe == null
        || !SlimefunUtils.isItemSimilar(currentRecipe, itemProduction, false, false)) {
      clearState(block);
      setStaticStatus(block, inventory, Material.BLACK_STAINED_GLASS_PANE, " ");
      return;
    }

    GenerationPlan plan = getGenerationPlan(block, inventory, itemProduction);
    if (!SupremeInventoryUtils.canFit(inventory, getOutputSlots(), plan.outputs())) {
      setStaticStatus(block, inventory, "&cOutput is full");
      return;
    }

    if (getProgressTime(block) <= 0) {
      SupremeInventoryUtils.pushAll(inventory, getOutputSlots(), plan.outputs());
      clearState(block);
      setStaticStatus(block, inventory, Material.BLACK_STAINED_GLASS_PANE, " ");
      return;
    }

    processTicks(block, inventory, itemProduction, plan);
  }

  private GenerationPlan getGenerationPlan(Block block, BlockMenu inventory, ItemStack baseOutput) {
    int[] inputSlots = getInputSlots();
    ItemStack[] upgrades = new ItemStack[4];
    for (int i = 0; i < upgrades.length; i++) {
      upgrades[i] = inventory.getItemInSlot(inputSlots[i + 1]);
    }

    int maxAmount = Supreme.getSupremeOptions().getMaxAmountTechGenerator();
    int baseConsumption = getEnergyConsumption();
    GenerationPlanCache cached = generationPlans.get(block);
    if (cached != null
        && cached.maxAmount() == maxAmount
        && cached.baseConsumption() == baseConsumption
        && Objects.equals(cached.baseOutput(), baseOutput)
        && sameStacks(cached.upgradeSnapshots(), upgrades)) {
      return cached.plan();
    }

    ItemStack[] snapshots = cloneStacks(upgrades);
    GenerationPlan plan = buildGenerationPlan(upgrades, baseOutput, maxAmount, baseConsumption);
    generationPlans.put(block,
        new GenerationPlanCache(baseOutput.clone(), snapshots, maxAmount, baseConsumption, plan));
    return plan;
  }

  private GenerationPlan buildGenerationPlan(ItemStack[] upgrades, ItemStack baseOutput,
      int maxAmount, int baseConsumption) {
    List<ItemStack> outputs = new ArrayList<>();
    ItemStack base = baseOutput.clone();
    base.setAmount(maxAmount);
    outputs.add(base);

    int timeReduction = 0;
    int consumption = baseConsumption;
    NamespacedKey tierKey = getMobTechTierKey();
    NamespacedKey typeKey = getMobTechTypeKey();

    for (ItemStack input : upgrades) {
      if (input == null || input.getType().isAir()) {
        continue;
      }

      ItemMeta itemMeta = input.getItemMeta();
      MobTechType persistedType = null;
      int persistedTier = 0;
      boolean hasPersistedData = itemMeta != null
          && PersistentDataAPI.hasInt(itemMeta, tierKey)
          && PersistentDataAPI.hasString(itemMeta, typeKey);
      if (hasPersistedData) {
        try {
          persistedType = MobTechType.valueOf(PersistentDataAPI.getString(itemMeta, typeKey));
          persistedTier = PersistentDataAPI.getInt(itemMeta, tierKey);
        } catch (IllegalArgumentException ignored) {
          persistedType = null;
        }
      }

      SlimefunItem slimefunItem = SlimefunItem.getByItem(input);
      if (slimefunItem instanceof MobTech mobTech) {
        int roundTimeAmount = Math.round(input.getAmount() * 0.015625F);
        if (mobTech.getMobTechType() == MobTechType.ROBOTIC_ACCELERATION
            || mobTech.getMobTechType() == MobTechType.MUTATION_BERSERK) {
          timeReduction += (mobTech.getMobTechTier() + 1) * roundTimeAmount;
        } else {
          timeReduction += roundTimeAmount;
        }

        if (mobTech.getMobTechType() == MobTechType.ROBOTIC_CLONING
            || mobTech.getMobTechType() == MobTechType.MUTATION_LUCK) {
          addUpgradeOutputs(input, baseOutput, mobTech, maxAmount, outputs);
        }

        if (!hasPersistedData && itemMeta != null) {
          PersistentDataAPI.setInt(itemMeta, tierKey, mobTech.getMobTechTier());
          PersistentDataAPI.setString(itemMeta, typeKey, mobTech.getMobTechType().name());
          input.setItemMeta(itemMeta);
        }
      }

      if (persistedType != null) {
        float perceptual = (persistedTier + 1) * input.getAmount() * 0.15625F;
        if (persistedType == MobTechType.ROBOTIC_EFFICIENCY
            || persistedType == MobTechType.MUTATION_INTELLIGENCE) {
          consumption -= Math.round(consumption / 100F * perceptual);
        }
        if (persistedType == MobTechType.ROBOTIC_ACCELERATION
            || persistedType == MobTechType.MUTATION_BERSERK) {
          consumption += Math.round(consumption / 100F * perceptual);
        }
      }
    }

    return new GenerationPlan(outputs.toArray(ItemStack[]::new), timeReduction,
        Math.max(consumption, 1));
  }

  private void addUpgradeOutputs(ItemStack input, ItemStack baseOutput, MobTech mobTech,
      int maxAmount, List<ItemStack> outputs) {
    int amount = Math.min(input.getAmount() * mobTech.getMobTechTier(), maxAmount);
    int copies = 1;
    if (mobTech.getMobTechTier() >= 4) {
      copies++;
    }
    if (mobTech.getMobTechTier() >= 6) {
      copies++;
    }
    if (mobTech.getMobTechTier() >= 9) {
      copies++;
    }

    for (int i = 0; i < copies; i++) {
      ItemStack extra = baseOutput.clone();
      extra.setAmount(amount);
      outputs.add(extra);
    }
  }

  private static ItemStack[] cloneStacks(ItemStack[] stacks) {
    ItemStack[] clones = new ItemStack[stacks.length];
    for (int i = 0; i < stacks.length; i++) {
      clones[i] = stacks[i] == null ? null : stacks[i].clone();
    }
    return clones;
  }

  private static boolean sameStacks(ItemStack[] first, ItemStack[] second) {
    if (first.length != second.length) {
      return false;
    }
    for (int i = 0; i < first.length; i++) {
      if (!Objects.equals(first[i], second[i])) {
        return false;
      }
    }
    return true;
  }

  private void clearState(Block block) {
    processing.remove(block);
    progressTime.remove(block);
  }

  @Override
  protected void onMachineBreak(Block block) {
    clearState(block);
    recipeMatches.remove(block);
    generationPlans.remove(block);
    staticStatuses.remove(block);
  }

  public int getProgressTime(Block block) {
    return progressTime.getOrDefault(block, getTimeProcess() * 2);
  }

  private void processTicks(Block block, BlockMenu inventory, ItemStack result,
      GenerationPlan plan) {
    int ticksLeft = getProgressTime(block);
    if (ticksLeft > 0) {
      if (takeCharge(block.getLocation(), inventory)) {
        int time = Math.max(0, ticksLeft - getSpeed() - plan.timeReduction());
        progressTime.put(block, time);

        int ticksTotal = getTimeProcess() * 2;
        clearStaticStatus(block);
        for (int slot : InventoryRecipe.TECH_GENERATOR_PROGRESS_BAR_SLOT) {
          ChestMenuUtils.updateProgressbar(inventory, slot, Math.round(ticksLeft / getSpeed()),
              Math.round(ticksTotal / getSpeed()), result);
        }
      } else {
        setStaticStatus(block, inventory, "&cNo power on the machine ("
            + UtilEnergy.format(UtilEnergy.toPerSecond(plan.consumption())) + " J/s)");
      }
    } else {
      setStaticStatus(block, inventory, "&cMachine time failure");
    }
  }

  protected boolean takeCharge(@Nonnull Location location, BlockMenu inventory) {
    Validate.notNull(location, "Can't attempt to take charge from a null location!");
    if (!isChargeable()) {
      return true;
    }

    int consumption = getCachedConsumption(location.getBlock(), inventory);
    int charge = getCharge(location);
    if (charge < consumption) {
      return false;
    }

    setCharge(location, charge - consumption);
    return true;
  }

  private int getCachedConsumption(Block block, BlockMenu inventory) {
    GenerationPlanCache cached = generationPlans.get(block);
    if (cached != null) {
      return cached.plan().consumption();
    }
    return checkDownConsumption(getEnergyConsumption(), inventory);
  }

  private int checkDownConsumption(int consumption, BlockMenu inventory) {
    int[] inputSlots = getInputSlots();
    for (int slot = 1; slot <= 4; slot++) {
      consumption = checkConsumptionSlot(inventory.getItemInSlot(inputSlots[slot]), consumption);
    }
    return Math.max(consumption, 1);
  }

  private int checkConsumptionSlot(ItemStack input, int consumption) {
    if (input == null || input.getType().isAir() || input.getItemMeta() == null) {
      return consumption;
    }

    NamespacedKey tierKey = getMobTechTierKey();
    NamespacedKey typeKey = getMobTechTypeKey();
    ItemMeta itemMeta = input.getItemMeta();
    if (PersistentDataAPI.hasInt(itemMeta, tierKey)
        && PersistentDataAPI.hasString(itemMeta, typeKey)) {
      try {
        MobTechType mobTechType = MobTechType.valueOf(PersistentDataAPI.getString(itemMeta, typeKey));
        int mobTechTier = PersistentDataAPI.getInt(itemMeta, tierKey);
        float perceptual = (mobTechTier + 1) * input.getAmount() * 0.15625F;
        if (mobTechType == MobTechType.ROBOTIC_EFFICIENCY
            || mobTechType == MobTechType.MUTATION_INTELLIGENCE) {
          consumption -= Math.round(consumption / 100F * perceptual);
        }
        if (mobTechType == MobTechType.ROBOTIC_ACCELERATION
            || mobTechType == MobTechType.MUTATION_BERSERK) {
          consumption += Math.round(consumption / 100F * perceptual);
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore stale or malformed metadata instead of stopping the machine ticker.
      }
    } else {
      SlimefunItem slimefunItem = SlimefunItem.getByItem(input);
      if (slimefunItem instanceof MobTech mobTech) {
        PersistentDataAPI.setInt(itemMeta, tierKey, mobTech.getMobTechTier());
        PersistentDataAPI.setString(itemMeta, typeKey, mobTech.getMobTechType().name());
        input.setItemMeta(itemMeta);
      }
    }
    return consumption;
  }

  private NamespacedKey getMobTechTierKey() {
    if (mobTechTierKey == null) {
      mobTechTierKey = new NamespacedKey(Supreme.inst(), "mob_tech_tier");
    }
    return mobTechTierKey;
  }

  private NamespacedKey getMobTechTypeKey() {
    if (mobTechTypeKey == null) {
      mobTechTypeKey = new NamespacedKey(Supreme.inst(), "mob_tech_type");
    }
    return mobTechTypeKey;
  }

  @Nullable
  private ItemStack validRecipeItem(Block block, BlockMenu inventory) {
    if (inventory == null) {
      return null;
    }

    ItemStack currentInput = inventory.getItemInSlot(getInputSlots()[0]);
    RecipeMatch cached = recipeMatches.get(block);
    if (cached != null && sameRecipeInput(currentInput, cached.observedInput())) {
      return cached.output();
    }

    for (AbstractItemRecipe produce : cachedRecipesForProcessing()) {
      if (SlimefunUtils.isItemSimilar(currentInput, produce.getFirstItemInput(), false, true)) {
        ItemStack output = produce.getFirstItemOutput();
        recipeMatches.put(block, new RecipeMatch(produce.getFirstItemInput(), output));
        return output;
      }
    }

    recipeMatches.put(block,
        new RecipeMatch(currentInput == null ? null : currentInput.clone(), null));
    return null;
  }

  private static boolean sameRecipeInput(@Nullable ItemStack current, @Nullable ItemStack cached) {
    if (current == null || current.getType().isAir()) {
      return cached == null || cached.getType().isAir();
    }
    if (cached == null || cached.getType().isAir()) {
      return false;
    }
    return SlimefunUtils.isItemSimilar(current, cached, false, true);
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    List<ItemStack> displayRecipes = new ArrayList<>();
    for (AbstractItemRecipe recipe : cachedRecipesForDisplay()) {
      if (recipe != null) {
        ItemStack itemStack = recipe.getFirstItemOutput().clone();
        itemStack.setAmount(Supreme.getSupremeOptions().getMaxAmountTechGenerator());
        displayRecipes.add(recipe.getFirstItemInput());
        displayRecipes.add(itemStack);
      }
    }
    return displayRecipes;
  }

  public int getSpeed() {
    return speed;
  }

  public TechGenerator setSpeed(int speed) {
    this.speed = speed;
    return this;
  }

  @Nonnull
  @Override
  public Radioactivity getRadioactivity() {
    return Radioactivity.LOW;
  }
}
