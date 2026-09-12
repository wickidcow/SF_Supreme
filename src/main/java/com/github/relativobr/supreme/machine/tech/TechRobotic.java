package com.github.relativobr.supreme.machine.tech;

import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemContainerMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import com.github.relativobr.supreme.generic.recipe.InventoryRecipe;
import com.github.relativobr.supreme.resource.SupremeComponents;
import com.github.relativobr.supreme.resource.magical.SupremeAttribute;
import com.github.relativobr.supreme.resource.magical.SupremeCetrus;
import com.github.relativobr.supreme.resource.magical.SupremeCore;
import com.github.relativobr.supreme.util.ItemGroups;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeItemStack;
import com.github.relativobr.supreme.util.SupremeSpecialMachineStateCodec;
import com.github.relativobr.supreme.util.UtilEnergy;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactivity;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class TechRobotic extends SimpleItemContainerMachine
    implements Radioactive, SupremeMachineDiagnostics {

  private static final String STATE_TYPE = "TECH_ROBOTIC";
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;
  private static final long IDLE_RETRY_TICKS = 4L;

  public static final SlimefunItemStack TECH_ROBOTIC = new SupremeItemStack("SUPREME_TECH_ROBOTIC",
      Material.POLISHED_BLACKSTONE, "&bTech Robotic", "", "&fUse beginner level robots ",
      "&fto progress to higher levels", "&fneed 64x to upgrade", "", LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_ROBOTIC = {SupremeComponents.INDUCTIVE_MACHINE,
      SupremeComponents.SYNTHETIC_RUBY, SupremeComponents.INDUCTIVE_MACHINE, SlimefunItems.REINFORCED_PLATE,
      SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.REINFORCED_PLATE, SupremeComponents.RUSTLESS_MACHINE,
      SupremeCore.CORE_OF_BLOCK, SupremeComponents.RUSTLESS_MACHINE};

  public static final SlimefunItemStack TECH_ROBOTIC_II = new SupremeItemStack("SUPREME_TECH_ROBOTIC_II",
      Material.POLISHED_BLACKSTONE, "&bTech Robotic II", "", "&fUse beginner level robots ",
      "&fto progress to higher levels", "&fneed 32x to upgrade", "", LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_ROBOTIC_II = new ItemStack[]{SupremeComponents.CONVEYANCE_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CONVEYANCE_MACHINE, SupremeComponents.INDUCTOR_MACHINE,
      TechRobotic.TECH_ROBOTIC, SupremeComponents.INDUCTOR_MACHINE, SupremeComponents.THORNERITE,
      SupremeCetrus.CETRUS_IGNIS, SupremeComponents.THORNERITE};

  public static final SlimefunItemStack TECH_ROBOTIC_III = new SupremeItemStack("SUPREME_TECH_ROBOTIC_III",
      Material.POLISHED_BLACKSTONE, "&bTech Robotic III", "", "&fUse beginner level robots ",
      "&fto progress to higher levels", "&fneed 16x to upgrade", "", LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_ROBOTIC_III = new ItemStack[]{SupremeComponents.THORNERITE,
      SupremeAttribute.getImpetus(), SupremeComponents.THORNERITE, SupremeComponents.SUPREME,
      TechRobotic.TECH_ROBOTIC_II, SupremeComponents.SUPREME, SupremeComponents.CRYSTALLIZER_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CRYSTALLIZER_MACHINE};

  public static final List<AbstractItemRecipe> recipes = new ArrayList<>();
  private final Map<Block, ItemStack> processing = new HashMap<>();
  private final Map<Block, Integer> progressTime = new HashMap<>();
  private final Map<Block, ItemStack> consumedInputs = new HashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new HashMap<>();
  private final Map<Block, Long> nextIdleCheck = new HashMap<>();
  private int speed = 1;
  private int amountUpgrade = 64;

  public TechRobotic(SlimefunItemStack item, ItemStack[] recipe) {
    super(ItemGroups.MACHINES_CATEGORY, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
  }

  public static void addRecipe(ItemStack input, ItemStack output) {
    recipes.add(new AbstractItemRecipe(input, output));
  }

  private static void invalidProgressBar(BlockMenu menu, String txt) {
    for (int i : InventoryRecipe.TECH_ROBOTIC_PROGRESS_BAR_SLOT) {
      menu.replaceExistingItem(i, new CustomItemStack(Material.RED_STAINED_GLASS_PANE, txt));
    }
  }

  private static void invalidProgressBar(BlockMenu menu, Material material, String txt) {
    for (int i : InventoryRecipe.TECH_ROBOTIC_PROGRESS_BAR_SLOT) {
      menu.replaceExistingItem(i, new CustomItemStack(material, txt));
    }
  }

  @Override
  public int[] getInputSlots() {
    return InventoryRecipe.TECH_ROBOTIC_INPUT_SLOTS;
  }

  @Override
  public int[] getOutputSlots() {
    return InventoryRecipe.TECH_ROBOTIC_OUTPUT_SLOTS;
  }

  @Override
  protected void constructMenu(BlockMenuPreset preset) {
    for (int i : InventoryRecipe.TECH_ROBOTIC_BORDER) {
      preset.addItem(i, new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_ROBOTIC_BORDER_IN) {
      preset.addItem(i, new CustomItemStack(Material.BLUE_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_ROBOTIC_BORDER_OUT) {
      preset.addItem(i, new CustomItemStack(Material.ORANGE_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_ROBOTIC_PROGRESS_BAR_SLOT) {
      preset.addItem(i, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_ROBOTIC_OUTPUT_SLOTS) {
      preset.addMenuClickHandler(i, new ChestMenu.AdvancedMenuClickHandler() {
        @Override
        public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
          return false;
        }

        @Override
        public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor, ClickAction action) {
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

  public void tick(Block b) {
    BlockMenu inv = BlockStorage.getInventory(b);
    if (inv == null) {
      return;
    }

    restoreStateIfNeeded(b);
    ItemStack itemProcess = processing.get(b);
    if (itemProcess == null) {
      long gameTime = b.getWorld().getGameTime();
      if (gameTime < nextIdleCheck.getOrDefault(b, 0L)) {
        return;
      }

      AbstractItemRecipe recipe = findRecipe(inv);
      if (recipe == null) {
        nextIdleCheck.put(b, gameTime + IDLE_RETRY_TICKS);
        invalidProgressBar(inv, "&cTechRobotic unidentified recipe");
        return;
      }

      nextIdleCheck.remove(b);
      ItemStack output = recipe.getFirstItemOutput().clone();
      if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), output)) {
        invalidProgressBar(inv, "&cOutput is full");
        return;
      }

      ItemStack consumed = recipe.getFirstItemInput().clone();
      consumed.setAmount(getAmountUpgrade());
      inv.consumeItem(getInputSlots()[0], getAmountUpgrade());
      consumedInputs.put(b, consumed);
      processing.put(b, output);
      int ticks = getTimeProcess() * 2;
      progressTime.put(b, ticks);
      lastProgressCheckpoint.put(b, ticks);
      persistState(b, output, consumed, ticks);
      invalidProgressBar(inv, output.getType(), " ");
      return;
    }

    if (getProgressTime(b) <= 0) {
      if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), itemProcess)) {
        invalidProgressBar(inv, "&cOutput is full");
        return;
      }
      SupremeInventoryUtils.pushAll(inv, getOutputSlots(), itemProcess);
      clearState(b);
      invalidProgressBar(inv, Material.BLACK_STAINED_GLASS_PANE, " ");
      return;
    }

    processTicks(b, inv, itemProcess);
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
    for (int i : InventoryRecipe.TECH_ROBOTIC_PROGRESS_BAR_SLOT) {
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

  private AbstractItemRecipe findRecipe(BlockMenu inv) {
    if (inv == null) {
      return null;
    }
    ItemStack input = inv.getItemInSlot(getInputSlots()[0]);
    if (input == null || input.getAmount() < getAmountUpgrade()) {
      return null;
    }

    for (AbstractItemRecipe recipe : recipes) {
      ItemStack required = recipe.getFirstItemInput();
      if (required != null && SlimefunUtils.isItemSimilar(input, required, false, true)) {
        return recipe;
      }
    }
    return null;
  }

  private void persistState(Block block, ItemStack output, ItemStack consumed, int ticks) {
    SupremeSpecialMachineStateCodec.save(block, STATE_TYPE, ticks, ticks, new ItemStack[0],
        new ItemStack[]{output.clone()}, new ItemStack[]{consumed.clone()}, -1, "");
  }

  private boolean restoreStateIfNeeded(Block block) {
    if (processing.containsKey(block)) {
      return true;
    }
    if (!SupremeSpecialMachineStateCodec.hasState(block, STATE_TYPE)) {
      return false;
    }

    Optional<SupremeSpecialMachineStateCodec.State> restored =
        SupremeSpecialMachineStateCodec.load(block, STATE_TYPE);
    if (restored.isPresent()) {
      SupremeSpecialMachineStateCodec.State state = restored.get();
      if (state.outputs().length > 0 && state.outputs()[0] != null
          && state.reservedItems().length > 0 && state.reservedItems()[0] != null) {
        processing.put(block, state.outputs()[0].clone());
        consumedInputs.put(block, state.reservedItems()[0].clone());
        progressTime.put(block, Math.max(0, state.progress()));
        lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
        nextIdleCheck.remove(block);
        return true;
      }
    }

    dropRecoveredInputs(block,
        SupremeSpecialMachineStateCodec.loadReservedOnly(block, STATE_TYPE));
    SupremeSpecialMachineStateCodec.clear(block);
    return false;
  }

  private void dropRecoveredInputs(Block block, ItemStack[] recovered) {
    if (block.getWorld() == null || recovered == null) {
      return;
    }
    for (ItemStack item : recovered) {
      if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
        block.getWorld().dropItemNaturally(block.getLocation(), item.clone());
      }
    }
  }

  private void clearState(Block block) {
    processing.remove(block);
    progressTime.remove(block);
    consumedInputs.remove(block);
    lastProgressCheckpoint.remove(block);
    nextIdleCheck.remove(block);
    SupremeSpecialMachineStateCodec.clear(block);
  }

  @Override
  protected void onMachineBreak(Block block) {
    restoreStateIfNeeded(block);
    ItemStack consumed = consumedInputs.get(block);
    if (consumed != null && block.getWorld() != null) {
      block.getWorld().dropItemNaturally(block.getLocation(), consumed.clone());
    }
    clearState(block);
  }

  @Override
  public List<String> getMachineDiagnosticLines(Block block) {
    List<String> lines = new ArrayList<>();
    BlockMenu inv = BlockStorage.getInventory(block);
    lines.add("Machine: " + getId() + " (TECH_ROBOTIC)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    ItemStack output = processing.get(block);
    if (output == null) {
      lines.add("State: IDLE / waiting for upgrade input");
      lines.add("Required input amount: " + getAmountUpgrade());
      lines.add("Idle recipe retry: every " + IDLE_RETRY_TICKS + " ticks while no recipe matches");
      return lines;
    }

    int progress = getProgressTime(block);
    String state;
    if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), output)) {
      state = "OUTPUT FULL";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption()) {
      state = "WAITING FOR POWER";
    } else if (progress <= 0) {
      state = "READY TO OUTPUT";
    } else {
      state = "PROCESSING";
    }
    lines.add("State: " + state + " | Progress: " + progress + "/" + (getTimeProcess() * 2));
    ItemStack consumed = consumedInputs.get(block);
    if (consumed != null) {
      lines.add("Reserved input: " + describeItem(consumed) + " x" + consumed.getAmount());
    }
    lines.add("Output: " + describeItem(output) + " x" + output.getAmount());
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    List<ItemStack> displayRecipes = new ArrayList<>();
    recipes.stream().filter(Objects::nonNull).forEach(recipe -> {
      ItemStack itemStack = recipe.getFirstItemInput().clone();
      itemStack.setAmount(getAmountUpgrade());
      displayRecipes.add(itemStack);
      displayRecipes.add(recipe.getFirstItemOutput());
    });
    return displayRecipes;
  }

  public int getSpeed() {
    return speed;
  }

  public TechRobotic setSpeed(int speed) {
    this.speed = speed;
    return this;
  }

  public int getAmountUpgrade() {
    return amountUpgrade;
  }

  public TechRobotic setAmountUpgrade(int amountUpgrade) {
    this.amountUpgrade = amountUpgrade;
    return this;
  }

  @Nonnull
  @Override
  public Radioactivity getRadioactivity() {
    return Radioactivity.VERY_HIGH;
  }
}
