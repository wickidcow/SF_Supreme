package com.github.relativobr.supreme.machine;

import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemWithLargeContainerMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.machine.recipe.VirtualGardenMachineRecipe;
import com.github.relativobr.supreme.resource.SupremeComponents;
import com.github.relativobr.supreme.resource.magical.SupremeAttribute;
import com.github.relativobr.supreme.resource.magical.SupremeCetrus;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeItemStack;
import com.github.relativobr.supreme.util.SupremeSpecialMachineStateCodec;
import com.github.relativobr.supreme.util.UtilEnergy;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.commons.lang.Validate;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

public class VirtualGarden extends SimpleItemWithLargeContainerMachine
    implements SupremeMachineDiagnostics {

  private static final String STATE_TYPE = "VIRTUAL_GARDEN";
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;
  private static final long IDLE_RETRY_TICKS = 4L;

  public static final SlimefunItemStack VIRTUAL_GARDEN_MACHINE = new SupremeItemStack("SUPREME_VIRTUAL_GARDEN_I",
      Material.STRIPPED_WARPED_HYPHAE, "&bVirtual Garden", "", "&fThis machine allows you to",
      "&fcultivate some resources.", "", LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
      LoreBuilder.speed(1), LoreBuilder.powerBuffer(1000), UtilEnergy.energyPowerPerSecond(20), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_GARDEN_MACHINE = new ItemStack[]{SupremeComponents.SYNTHETIC_RUBY,
      new ItemStack(Material.STRIPPED_WARPED_HYPHAE), SupremeComponents.SYNTHETIC_RUBY,
      SupremeComponents.RUSTLESS_MACHINE, SupremeComponents.PETRIFIER_MACHINE, SupremeComponents.RUSTLESS_MACHINE,
      SupremeComponents.ADAMANTIUM_PLATE, SlimefunItems.PROGRAMMABLE_ANDROID_2_FARMER,
      SupremeComponents.ADAMANTIUM_PLATE};

  public static final SlimefunItemStack VIRTUAL_GARDEN_MACHINE_II = new SupremeItemStack("SUPREME_VIRTUAL_GARDEN_II",
      Material.STRIPPED_WARPED_HYPHAE, "&bVirtual Garden II", "", "&fThis machine allows you to",
      "&fcultivate some resources.", "", LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
      LoreBuilder.speed(5), LoreBuilder.powerBuffer(5000), UtilEnergy.energyPowerPerSecond(100), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_GARDEN_MACHINE_II = new ItemStack[]{
      SupremeComponents.CONVEYANCE_MACHINE, SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CONVEYANCE_MACHINE,
      SupremeComponents.INDUCTOR_MACHINE, VirtualGarden.VIRTUAL_GARDEN_MACHINE, SupremeComponents.INDUCTOR_MACHINE,
      SupremeComponents.THORNERITE, SupremeCetrus.CETRUS_AQUA, SupremeComponents.THORNERITE};

  public static final SlimefunItemStack VIRTUAL_GARDEN_MACHINE_III = new SupremeItemStack("SUPREME_VIRTUAL_GARDEN_III",
      Material.STRIPPED_WARPED_HYPHAE, "&bVirtual Garden III", "", "&fThis machine allows you to",
      "&fcultivate some resources.", "", LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
      LoreBuilder.speed(15), LoreBuilder.powerBuffer(15000), UtilEnergy.energyPowerPerSecond(300), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_VIRTUAL_GARDEN_MACHINE_III = new ItemStack[]{SupremeComponents.THORNERITE,
      SupremeAttribute.getFortune(), SupremeComponents.THORNERITE, SupremeComponents.SUPREME,
      VirtualGarden.VIRTUAL_GARDEN_MACHINE_II, SupremeComponents.SUPREME, SupremeComponents.CRYSTALLIZER_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CRYSTALLIZER_MACHINE};

  private final Map<Block, MachineRecipe> processing = new HashMap<>();
  private final Map<Block, Integer> progress = new HashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new HashMap<>();
  private final Map<Block, Long> nextIdleCheck = new HashMap<>();
  private final Map<Block, Integer> lastIdleFingerprint = new HashMap<>();
  private final Set<VirtualGardenMachineRecipe> virtualGardenMachineRecipes = new HashSet<>();

  @ParametersAreNonnullByDefault
  public VirtualGarden(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
    super(category, item, recipeType, recipe);
  }

  @Override
  protected void registerDefaultRecipes() {
    recipes.clear();
    VirtualGardenMachineRecipe.getAllRecipe().stream().filter(Objects::nonNull)
        .forEach(recipe -> addProduce(new VirtualGardenMachineRecipe(recipe)));
  }

  public void addProduce(@Nonnull VirtualGardenMachineRecipe produce) {
    Validate.notNull(produce, "A produce cannot be null");
    virtualGardenMachineRecipes.add(produce);
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    List<ItemStack> displayRecipes = new ArrayList<>();
    VirtualGardenMachineRecipe.getAllRecipe().stream().filter(Objects::nonNull).forEach(recipe -> {
      displayRecipes.add(new CustomItemStack(recipe.getFirstMaterialInput(), null, "&fRequires &bto cultivate"));
      displayRecipes.add(new ItemStack(recipe.getFirstMaterialOutput()));
    });
    return displayRecipes;
  }

  @Override
  public void preRegister() {
    addItemHandler(new SupremeBlockTicker(true, this::tick));
  }

  @Nonnull
  @Override
  public String getRecipeSectionLabel(@Nonnull Player p) {
    return "&7Cultivate:";
  }

  @Override
  protected MachineRecipe findNextRecipe(@Nonnull BlockMenu inv) {
    for (int slot : getInputSlots()) {
      for (VirtualGardenMachineRecipe produce : virtualGardenMachineRecipes) {
        ItemStack itemInSlot = inv.getItemInSlot(slot);
        ItemStack itemInInput = produce.getInput()[0];
        if (itemInSlot != null && itemInInput != null
            && itemInSlot.getType() == itemInInput.getType()
            && SupremeInventoryUtils.canFit(inv, getOutputSlots(), produce.getOutput())) {
          return produce;
        }
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
      long gameTime = b.getWorld().getGameTime();
      int fingerprint = SupremeInventoryUtils.fingerprint(inv, getInputSlots(), getOutputSlots());
      if (gameTime < nextIdleCheck.getOrDefault(b, 0L)
          && fingerprint == lastIdleFingerprint.getOrDefault(b, Integer.MIN_VALUE)) {
        return;
      }

      MachineRecipe next = findNextRecipe(inv);
      if (next != null) {
        clearIdleBackoff(b);
        processing.put(b, next);
        progress.put(b, next.getTicks());
        lastProgressCheckpoint.put(b, next.getTicks());
        persistState(b, next, next.getTicks());
      } else {
        lastIdleFingerprint.put(b, fingerprint);
        nextIdleCheck.put(b, gameTime + IDLE_RETRY_TICKS);
        updateStatusReset(inv);
      }
      return;
    }

    ItemStack[] recipeOutput = active.getOutput();
    if (notHasSpaceOutput(inv, recipeOutput)) {
      updateStatusOutputFull(inv);
      return;
    }

    int timeLeft = progress.getOrDefault(b, active.getTicks());
    if (timeLeft <= 0) {
      SupremeInventoryUtils.pushAll(inv, getOutputSlots(), recipeOutput);
      clearState(b);
      updateStatusReset(inv);
      return;
    }

    if (getCharge(b.getLocation()) < getEnergyConsumption()) {
      updateStatusConnectEnergy(inv, recipeOutput.length > 0 ? recipeOutput[0] : null);
      return;
    }

    if (takeCharge(b.getLocation())) {
      ChestMenuUtils.updateProgressbar(inv, getStatusSlot(), timeLeft, active.getTicks(), getProgressBar());
      int nextProgress = Math.max(timeLeft - getSpeed(), 0);
      progress.put(b, nextProgress);
      checkpointProgress(b, nextProgress, active.getTicks());
    }
  }

  private void persistState(Block block, MachineRecipe recipe, int currentProgress) {
    SupremeSpecialMachineStateCodec.save(block, STATE_TYPE, currentProgress, recipe.getTicks(),
        recipe.getInput(), recipe.getOutput(), new ItemStack[0], -1, "");
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
      if (state.outputs().length > 0) {
        MachineRecipe recipe = new MachineRecipe(state.ticks(), state.inputs(), state.outputs());
        processing.put(block, recipe);
        progress.put(block, Math.max(0, state.progress()));
        lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
        clearIdleBackoff(block);
        return true;
      }
    }

    SupremeSpecialMachineStateCodec.clear(block);
    return false;
  }

  private void clearIdleBackoff(Block block) {
    nextIdleCheck.remove(block);
    lastIdleFingerprint.remove(block);
  }

  private void clearState(Block block) {
    processing.remove(block);
    progress.remove(block);
    lastProgressCheckpoint.remove(block);
    clearIdleBackoff(block);
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
    lines.add("Machine: " + getId() + " (VIRTUAL_GARDEN)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    MachineRecipe active = processing.get(block);
    if (active == null) {
      lines.add("State: IDLE / waiting for cultivation input");
      lines.add("Idle recipe retry: " + IDLE_RETRY_TICKS + " ticks while inventory is unchanged");
      return lines;
    }

    int timeLeft = progress.getOrDefault(block, active.getTicks());
    String state;
    if (notHasSpaceOutput(inv, active.getOutput())) {
      state = "OUTPUT FULL";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption() && timeLeft > 0) {
      state = "WAITING FOR POWER";
    } else if (timeLeft <= 0) {
      state = "READY TO OUTPUT";
    } else {
      state = "PROCESSING";
    }
    lines.add("State: " + state + " | Progress: " + timeLeft + "/" + active.getTicks());
    if (active.getInput().length > 0 && active.getInput()[0] != null) {
      lines.add("Cultivation input: " + describeItem(active.getInput()[0]));
    }
    if (active.getOutput().length > 0 && active.getOutput()[0] != null) {
      lines.add("Output: " + describeItem(active.getOutput()[0]) + " x" + active.getOutput()[0].getAmount());
    }
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
  }

  @Nonnull
  @Override
  public String getMachineIdentifier() {
    return "VIRTUAL_GARDEN";
  }

  @Override
  public ItemStack getProgressBar() {
    return new ItemStack(Material.IRON_HOE);
  }

  public MachineRecipe getProcessing(Block b) {
    return processing.get(b);
  }

  public boolean isProcessing(Block b) {
    return getProcessing(b) != null;
  }
}
