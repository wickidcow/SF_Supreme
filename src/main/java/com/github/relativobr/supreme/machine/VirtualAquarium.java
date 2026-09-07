package com.github.relativobr.supreme.machine;

import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemWithLargeContainerMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.machine.recipe.VirtualAquariumMachineRecipe;
import com.github.relativobr.supreme.resource.SupremeComponents;
import com.github.relativobr.supreme.resource.magical.SupremeAttribute;
import com.github.relativobr.supreme.resource.magical.SupremeCetrus;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeItemStack;
import com.github.relativobr.supreme.util.SupremeSpecialMachineStateCodec;
import com.github.relativobr.supreme.util.UtilEnergy;
import com.github.relativobr.supreme.util.UtilMachine;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.commons.lang.Validate;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class VirtualAquarium extends SimpleItemWithLargeContainerMachine
    implements SupremeMachineDiagnostics {

  private static final String STATE_TYPE = "VIRTUAL_AQUARIUM";
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;

  public static final SlimefunItemStack VIRTUAL_AQUARIUM_MACHINE = new SupremeItemStack("SUPREME_VIRTUAL_AQUARIUM_I",
      Material.DARK_PRISMARINE, "&bVirtual Aquarium", "", "&fThis machine allows you to collect ",
      "&f items that are collected at sea.", "", LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
      LoreBuilder.speed(1), LoreBuilder.powerBuffer(1000), UtilEnergy.energyPowerPerSecond(20), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_AQUARIUM_MACHINE = new ItemStack[]{SupremeComponents.SYNTHETIC_RUBY,
      new ItemStack(Material.FISHING_ROD), SupremeComponents.SYNTHETIC_RUBY, SupremeComponents.INDUCTIVE_MACHINE,
      SupremeComponents.PETRIFIER_MACHINE, SupremeComponents.INDUCTIVE_MACHINE, SupremeComponents.ADAMANTIUM_PLATE,
      SlimefunItems.PROGRAMMABLE_ANDROID_2_FISHERMAN, SupremeComponents.ADAMANTIUM_PLATE};

  public static final SlimefunItemStack VIRTUAL_AQUARIUM_MACHINE_II = new SupremeItemStack(
      "SUPREME_VIRTUAL_AQUARIUM_II", Material.DARK_PRISMARINE, "&bVirtual Aquarium II", "",
      "&fThis machine allows you to collect", "&f items that are collected at sea.", "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), LoreBuilder.speed(5),
      LoreBuilder.powerBuffer(5000), UtilEnergy.energyPowerPerSecond(100), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_AQUARIUM_MACHINE_II = new ItemStack[]{
      SupremeComponents.CONVEYANCE_MACHINE, SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CONVEYANCE_MACHINE,
      SupremeComponents.INDUCTOR_MACHINE, VirtualAquarium.VIRTUAL_AQUARIUM_MACHINE, SupremeComponents.INDUCTOR_MACHINE,
      SupremeComponents.THORNERITE, SupremeCetrus.CETRUS_IGNIS, SupremeComponents.THORNERITE};

  public static final SlimefunItemStack VIRTUAL_AQUARIUM_MACHINE_III = new SupremeItemStack(
      "SUPREME_VIRTUAL_AQUARIUM_III", Material.DARK_PRISMARINE, "&bVirtual Aquarium III", "",
      "&fThis machine allows you to collect", "&f items that are collected at sea.", "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), LoreBuilder.speed(15),
      LoreBuilder.powerBuffer(15000), UtilEnergy.energyPowerPerSecond(300), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_AQUARIUM_MACHINE_III = new ItemStack[]{SupremeComponents.THORNERITE,
      SupremeAttribute.getMagic(), SupremeComponents.THORNERITE, SupremeComponents.SUPREME,
      VirtualAquarium.VIRTUAL_AQUARIUM_MACHINE_II, SupremeComponents.SUPREME, SupremeComponents.CRYSTALLIZER_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CRYSTALLIZER_MACHINE};

  private final Map<Block, MachineRecipe> processing = new HashMap<>();
  private final Map<Block, Integer> progress = new HashMap<>();
  private final Map<Block, ItemStack> selectedOutput = new HashMap<>();
  private final Map<Block, Integer> selectedInputSlots = new HashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new HashMap<>();
  private final Set<VirtualAquariumMachineRecipe> virtualAquariumMachineRecipe = new HashSet<>();

  @ParametersAreNonnullByDefault
  public VirtualAquarium(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
    super(category, item, recipeType, recipe);
  }

  @Override
  protected void registerDefaultRecipes() {
    recipes.clear();
    addProduce(new VirtualAquariumMachineRecipe(new ItemStack(Material.FISHING_ROD),
        new ItemStack[]{new ItemStack(Material.SPONGE, 2), new ItemStack(Material.SEA_LANTERN, 2),
            new ItemStack(Material.COD, 20), new ItemStack(Material.SALMON, 20),
            new ItemStack(Material.TROPICAL_FISH, 20), new ItemStack(Material.INK_SAC, 20),
            new ItemStack(Material.NAUTILUS_SHELL, 6), new ItemStack(Material.STICK, 5),
            new ItemStack(Material.STRING, 5)}));
    addProduce(new VirtualAquariumMachineRecipe(new ItemStack(Material.TRIDENT),
        new ItemStack[]{new ItemStack(Material.SPONGE, 10), new ItemStack(Material.SEA_LANTERN, 10),
            new ItemStack(Material.COD, 5), new ItemStack(Material.SALMON, 5), new ItemStack(Material.TROPICAL_FISH, 5),
            new ItemStack(Material.INK_SAC, 5), new ItemStack(Material.NAUTILUS_SHELL, 20),
            new ItemStack(Material.STICK, 20), new ItemStack(Material.STRING, 20)}));
    addProduce(new VirtualAquariumMachineRecipe(new ItemStack(Material.GOLDEN_HOE),
        new ItemStack[]{new ItemStack(Material.SPONGE, 1), new ItemStack(Material.SEA_LANTERN, 1),
            new ItemStack(Material.COD, 3), new ItemStack(Material.SALMON, 3), new ItemStack(Material.TROPICAL_FISH, 2),
            new ItemStack(Material.INK_SAC, 2), new ItemStack(Material.NAUTILUS_SHELL, 5),
            new ItemStack(Material.STICK, 50), new ItemStack(Material.STRING, 33)}));
  }

  public void addProduce(@Nonnull VirtualAquariumMachineRecipe produce) {
    Validate.notNull(produce, "A produce cannot be null");
    virtualAquariumMachineRecipe.add(produce);
  }

  @Override
  public void preRegister() {
    addItemHandler(new SupremeBlockTicker(true, this::tick));
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    return VirtualAquariumMachineRecipe.getAllRecipe();
  }

  @Nonnull
  @Override
  public String getRecipeSectionLabel(@Nonnull Player p) {
    return "&7Collects:";
  }

  @Override
  protected MachineRecipe findNextRecipe(@Nonnull BlockMenu inv) {
    selectedInputSlots.remove(inv.getBlock());
    selectedOutput.remove(inv.getBlock());

    for (int slot : getInputSlots()) {
      ItemStack itemInSlot = inv.getItemInSlot(slot);
      if (itemInSlot == null || itemInSlot.getType().isAir()) {
        continue;
      }

      for (VirtualAquariumMachineRecipe produce : virtualAquariumMachineRecipe) {
        ItemStack itemInInput = produce.getInput()[0];
        if (itemInInput == null || itemInSlot.getType() != itemInInput.getType()) {
          continue;
        }

        ItemStack material = UtilMachine.getMaterial(produce.getOutput(), UtilMachine.getRandomInt());
        if (material == null) {
          continue;
        }
        ItemStack chosen = material.clone();
        chosen.setAmount(1);
        if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), new ItemStack[]{chosen})) {
          continue;
        }

        // The tool cost is committed only after the selected output can actually be delivered.
        // This prevents durability loss while the machine is waiting for power/output space and
        // makes restart recovery deterministic.
        selectedInputSlots.put(inv.getBlock(), slot);
        selectedOutput.put(inv.getBlock(), chosen);
        return produce;
      }
    }
    return null;
  }

  @Override
  protected void tick(Block b) {
    BlockMenu inv = BlockStorage.getInventory(b);
    if (inv == null) {
      return;
    }

    restoreStateIfNeeded(b);
    MachineRecipe active = processing.get(b);
    if (active == null) {
      MachineRecipe next = findNextRecipe(inv);
      if (next != null) {
        ItemStack chosen = selectedOutput.get(b);
        Integer slot = selectedInputSlots.get(b);
        if (chosen == null || slot == null) {
          clearState(b);
          updateStatusInvalidInput(inv);
          return;
        }
        processing.put(b, next);
        progress.put(b, next.getTicks());
        lastProgressCheckpoint.put(b, next.getTicks());
        persistState(b, next, chosen, slot, next.getTicks());
      } else {
        clearTransientState(b);
        updateStatusReset(inv);
      }
      return;
    }

    ItemStack output = selectedOutput.get(b);
    if (output == null) {
      clearState(b);
      updateStatusInvalidInput(inv);
      return;
    }
    if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), new ItemStack[]{output})) {
      updateStatusOutputFull(inv);
      return;
    }

    int timeLeft = progress.getOrDefault(b, active.getTicks());
    if (timeLeft <= 0) {
      if (!commitAquariumTool(b, inv, active)) {
        clearState(b);
        updateStatusInvalidInput(inv);
        return;
      }
      SupremeInventoryUtils.pushAll(inv, getOutputSlots(), new ItemStack[]{output});
      clearState(b);
      updateStatusReset(inv);
      return;
    }

    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
      updateStatusConnectEnergy(inv, output);
      return;
    }

    if (takeCharge(b.getLocation())) {
      ChestMenuUtils.updateProgressbar(inv, getStatusSlot(), timeLeft, active.getTicks(), getProgressBar());
      int nextProgress = Math.max(timeLeft - getSpeed(), 0);
      progress.put(b, nextProgress);
      checkpointProgress(b, nextProgress, active.getTicks());
    }
  }

  private boolean commitAquariumTool(Block block, BlockMenu menu, MachineRecipe recipe) {
    Integer slot = selectedInputSlots.get(block);
    ItemStack[] inputs = recipe.getInput();
    if (slot == null || inputs == null || inputs.length == 0 || inputs[0] == null) {
      return false;
    }

    ItemStack required = inputs[0];
    ItemStack current = menu.getItemInSlot(slot);
    if (current == null || current.getType() != required.getType()) {
      return false;
    }

    ItemMeta itemMeta = current.getItemMeta();
    if (itemMeta instanceof Damageable durability && !itemMeta.isUnbreakable()) {
      int currentDamage = durability.getDamage();
      if (currentDamage + 2 >= current.getType().getMaxDurability()) {
        menu.consumeItem(slot);
      } else {
        durability.setDamage(currentDamage + 2);
        current.setItemMeta(itemMeta);
        menu.replaceExistingItem(slot, current);
      }
    }
    return true;
  }

  private void persistState(Block block, MachineRecipe recipe, ItemStack chosen, int slot,
      int currentProgress) {
    SupremeSpecialMachineStateCodec.save(block, STATE_TYPE, currentProgress, recipe.getTicks(),
        recipe.getInput(), new ItemStack[]{chosen.clone()}, new ItemStack[0], slot, "");
  }

  private void checkpointProgress(Block block, int currentProgress, int totalTicks) {
    int previous = lastProgressCheckpoint.getOrDefault(block, totalTicks);
    if (currentProgress <= 0 || Math.abs(previous - currentProgress) >= PROGRESS_CHECKPOINT_INTERVAL) {
      SupremeSpecialMachineStateCodec.saveProgress(block, STATE_TYPE, currentProgress);
      lastProgressCheckpoint.put(block, currentProgress);
    }
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
      if (state.inputs().length > 0 && state.outputs().length > 0
          && state.outputs()[0] != null && state.auxInt() >= 0) {
        MachineRecipe recipe = new MachineRecipe(state.ticks(), state.inputs(), state.outputs());
        processing.put(block, recipe);
        progress.put(block, Math.max(0, state.progress()));
        selectedOutput.put(block, state.outputs()[0].clone());
        selectedInputSlots.put(block, state.auxInt());
        lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
        return true;
      }
    }

    SupremeSpecialMachineStateCodec.clear(block);
    return false;
  }

  private void clearTransientState(Block block) {
    selectedOutput.remove(block);
    selectedInputSlots.remove(block);
  }

  private void clearState(Block block) {
    processing.remove(block);
    progress.remove(block);
    selectedOutput.remove(block);
    selectedInputSlots.remove(block);
    lastProgressCheckpoint.remove(block);
    SupremeSpecialMachineStateCodec.clear(block);
  }

  @Override
  protected void onMachineBreak(Block block) {
    clearState(block);
  }

  @Override
  public List<String> getMachineDiagnosticLines(Block block) {
    List<String> lines = new ArrayList<>();
    BlockMenu inv = BlockStorage.getInventory(block);
    lines.add("Machine: " + getId() + " (VIRTUAL_AQUARIUM)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    MachineRecipe active = processing.get(block);
    ItemStack output = selectedOutput.get(block);
    if (active == null || output == null) {
      lines.add("State: IDLE / waiting for fishing tool");
      return lines;
    }

    int timeLeft = progress.getOrDefault(block, active.getTicks());
    String state;
    if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), new ItemStack[]{output})) {
      state = "OUTPUT FULL";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption() && timeLeft > 0) {
      state = "WAITING FOR POWER";
    } else if (timeLeft <= 0) {
      state = "READY TO COMMIT TOOL/OUTPUT";
    } else {
      state = "PROCESSING";
    }
    lines.add("State: " + state + " | Progress: " + timeLeft + "/" + active.getTicks());
    if (active.getInput().length > 0 && active.getInput()[0] != null) {
      lines.add("Tool: " + describeItem(active.getInput()[0]) + " | Slot: "
          + selectedInputSlots.getOrDefault(block, -1));
    }
    lines.add("Selected output: " + describeItem(output) + " x" + output.getAmount());
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
  }

  @Nonnull
  @Override
  public String getMachineIdentifier() {
    return "VIRTUAL_AQUARIUM";
  }

  @Override
  public ItemStack getProgressBar() {
    return new ItemStack(Material.FISHING_ROD);
  }

  public MachineRecipe getProcessing(Block b) {
    return processing.get(b);
  }

  public boolean isProcessing(Block b) {
    return getProcessing(b) != null;
  }
}
