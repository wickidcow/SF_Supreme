package com.github.relativobr.supreme.machine.tech;

import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemContainerMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
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
import com.github.relativobr.supreme.util.UtilMachine;
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
import javax.annotation.ParametersAreNonnullByDefault;
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

public class TechMutation extends SimpleItemContainerMachine
    implements Radioactive, SupremeMachineDiagnostics {

  private static final String STATE_TYPE = "TECH_MUTATION";
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;

  private record MutationCycle(ItemStack input1, ItemStack input2, ItemStack output, int chance) {
  }

  public static final SlimefunItemStack TECH_MUTATION_I = new SupremeItemStack("SUPREME_TECH_MUTATION_I",
      Material.SLIME_BLOCK, "&bTech Mutation", "", "&fUse generator mutation ", "&fto progress to higher levels", "",
      LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_MUTATION_I = {SupremeComponents.INDUCTIVE_MACHINE,
      SupremeComponents.SYNTHETIC_RUBY, SupremeComponents.INDUCTIVE_MACHINE, SlimefunItems.REINFORCED_PLATE,
      SlimefunItems.NUCLEAR_REACTOR, SlimefunItems.REINFORCED_PLATE, SupremeComponents.RUSTLESS_MACHINE,
      SupremeCore.CORE_OF_DEATH, SupremeComponents.RUSTLESS_MACHINE};

  public static final SlimefunItemStack TECH_MUTATION_II = new SupremeItemStack("SUPREME_TECH_MUTATION_II",
      Material.SLIME_BLOCK, "&bTech Mutation II", "", "&fUse generator mutation ", "&fto progress to higher levels", "",
      "&fChance factor multiplied by 2x", "", LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_MUTATION_II = new ItemStack[]{SupremeComponents.CONVEYANCE_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CONVEYANCE_MACHINE, SupremeComponents.INDUCTOR_MACHINE,
      TechMutation.TECH_MUTATION_I, SupremeComponents.INDUCTOR_MACHINE, SupremeComponents.THORNERITE,
      SupremeCetrus.CETRUS_IGNIS, SupremeComponents.THORNERITE};

  public static final SlimefunItemStack TECH_MUTATION_III = new SupremeItemStack("SUPREME_TECH_MUTATION_III",
      Material.SLIME_BLOCK, "&bTech Mutation III", "", "&fUse generator mutation ", "&fto progress to higher levels",
      "", "&fChance factor multiplied by 4x", "", LoreBuilder.radioactive(Radioactivity.VERY_HIGH), "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), UtilEnergy.energyPowerPerSecond(500), "",
      "&3Supreme Machine");
  public static final ItemStack[] RECIPE_TECH_MUTATION_III = new ItemStack[]{SupremeComponents.THORNERITE,
      SupremeAttribute.getImpetus(), SupremeComponents.THORNERITE, SupremeComponents.SUPREME,
      TechMutation.TECH_MUTATION_II, SupremeComponents.SUPREME, SupremeComponents.CRYSTALLIZER_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CRYSTALLIZER_MACHINE};

  public static final List<MobTechMutationGeneric> recipes = new ArrayList<>();
  private final Map<Block, MutationCycle> processing = new HashMap<>();
  private final Map<Block, Integer> progressTime = new HashMap<>();
  private final Map<Block, Boolean> successfulMutations = new HashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new HashMap<>();
  private int speed = 1;
  private int upgradeLuck = 1;

  public TechMutation(SlimefunItemStack item, ItemStack[] recipe) {
    super(ItemGroups.MACHINES_CATEGORY, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
  }

  @ParametersAreNonnullByDefault
  public static void addRecipeTechMutation(SlimefunItemStack recipe1, SlimefunItemStack recipe2, int chance,
      SlimefunItemStack item) {
    recipes.add(new MobTechMutationGeneric(recipe1, recipe2, chance, item));
  }

  @ParametersAreNonnullByDefault
  public static void addRecipeTechMutation(SlimefunItemStack itemStack1, SlimefunItemStack itemStack2,
      SlimefunItemStack output) {
    addRecipeTechMutation(itemStack1, itemStack2, 100, output);
  }

  private static void invalidProgressBar(BlockMenu menu, String txt) {
    for (int i : InventoryRecipe.TECH_MUTATION_PROGRESS_BAR_SLOT) {
      menu.replaceExistingItem(i, new CustomItemStack(Material.RED_STAINED_GLASS_PANE, txt));
    }
  }

  private static void invalidProgressBar(BlockMenu menu, Material material, String txt) {
    for (int i : InventoryRecipe.TECH_MUTATION_PROGRESS_BAR_SLOT) {
      menu.replaceExistingItem(i, new CustomItemStack(material, txt));
    }
  }

  @Override
  public int[] getInputSlots() {
    return InventoryRecipe.TECH_MUTATION_INPUT_SLOTS;
  }

  @Override
  public int[] getOutputSlots() {
    return InventoryRecipe.TECH_MUTATION_OUTPUT_SLOTS;
  }

  @Override
  protected void constructMenu(BlockMenuPreset preset) {
    for (int i : InventoryRecipe.TECH_MUTATION_BORDER) {
      preset.addItem(i, new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_MUTATION_BORDER_IN) {
      preset.addItem(i, new CustomItemStack(Material.BLUE_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_MUTATION_BORDER_OUT) {
      preset.addItem(i, new CustomItemStack(Material.ORANGE_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }
    for (int i : InventoryRecipe.TECH_MUTATION_PROGRESS_BAR_SLOT) {
      preset.addItem(i, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "),
          ChestMenuUtils.getEmptyClickHandler());
    }

    for (int i : InventoryRecipe.TECH_MUTATION_OUTPUT_SLOTS) {
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

  @Override
  public void tick(Block b) {
    BlockMenu inv = BlockStorage.getInventory(b);
    if (inv == null) {
      return;
    }

    restoreStateIfNeeded(b);
    MutationCycle itemProcessing = processing.get(b);
    if (itemProcessing == null) {
      MobTechMutationGeneric itemRecipe = validRecipeItem(inv);
      if (itemRecipe == null) {
        invalidProgressBar(inv, "&cTechMutation unidentified recipe");
        return;
      }

      ItemStack output = itemRecipe.getOutput().clone();
      if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), output)) {
        invalidProgressBar(inv, "&cOutput is full");
        return;
      }

      ItemStack input1 = itemRecipe.getInput1().clone();
      ItemStack input2 = itemRecipe.getInput2().clone();
      input1.setAmount(1);
      input2.setAmount(1);
      inv.consumeItem(getInputSlots()[0], 1);
      inv.consumeItem(getInputSlots()[1], 1);

      MutationCycle cycle = new MutationCycle(input1, input2, output,
          Math.min(100, itemRecipe.getChance() * getUpgradeLuck()));
      processing.put(b, cycle);
      int ticks = getTimeProcess() * 2;
      progressTime.put(b, ticks);
      lastProgressCheckpoint.put(b, ticks);
      successfulMutations.remove(b);
      persistState(b, cycle, ticks, "");
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

  private void persistState(Block block, MutationCycle cycle, int ticks, String result) {
    SupremeSpecialMachineStateCodec.save(block, STATE_TYPE, ticks, ticks,
        new ItemStack[0], new ItemStack[]{cycle.output().clone()},
        new ItemStack[]{cycle.input1().clone(), cycle.input2().clone()}, cycle.chance(), result);
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
          && state.reservedItems().length >= 2
          && state.reservedItems()[0] != null && state.reservedItems()[1] != null) {
        MutationCycle cycle = new MutationCycle(state.reservedItems()[0].clone(),
            state.reservedItems()[1].clone(), state.outputs()[0].clone(),
            Math.max(0, state.auxInt()));
        processing.put(block, cycle);
        progressTime.put(block, Math.max(0, state.progress()));
        lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
        if ("true".equalsIgnoreCase(state.auxText())) {
          successfulMutations.put(block, true);
        } else if ("false".equalsIgnoreCase(state.auxText())) {
          successfulMutations.put(block, false);
        }
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
    successfulMutations.remove(block);
    lastProgressCheckpoint.remove(block);
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
    BlockMenu inv = BlockStorage.getInventory(block);
    lines.add("Machine: " + getId() + " (TECH_MUTATION)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    MutationCycle cycle = processing.get(block);
    if (cycle == null) {
      lines.add("State: IDLE / waiting for mutation inputs");
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
