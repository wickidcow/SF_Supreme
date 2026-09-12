package com.github.relativobr.supreme.machine;

import static com.github.relativobr.supreme.Supreme.getSupremeOptions;

import com.github.relativobr.supreme.compat.SupremeBlockTicker;
import com.github.relativobr.supreme.generic.machine.SimpleItemWithLargeContainerMachine;
import com.github.relativobr.supreme.generic.machine.SupremeMachineDiagnostics;
import com.github.relativobr.supreme.machine.recipe.MobCollectorMachineRecipe;
import com.github.relativobr.supreme.resource.SupremeComponents;
import com.github.relativobr.supreme.resource.magical.SupremeAttribute;
import com.github.relativobr.supreme.resource.magical.SupremeCetrus;
import com.github.relativobr.supreme.util.SupremeInventoryUtils;
import com.github.relativobr.supreme.util.SupremeItemStack;
import com.github.relativobr.supreme.util.SupremeOptions;
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
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class MobCollector extends SimpleItemWithLargeContainerMachine
    implements SupremeMachineDiagnostics {

  private static final String STATE_TYPE = "MOB_COLLECTOR";
  private static final int PROGRESS_CHECKPOINT_INTERVAL = 20;
  private static final long IDLE_SCAN_BACKOFF_TICKS = 4L;

  public static final SlimefunItemStack MOB_COLLECTOR_MACHINE = new SupremeItemStack("SUPREME_MOB_COLLECTOR_MACHINE_I",
      Material.RESPAWN_ANCHOR, "&bMob Collector", "", "&fThis machine allows you to collect ",
      "&fitems from nearby mobs. (4 block)", "", LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
      LoreBuilder.speed(1), LoreBuilder.powerBuffer(1000), UtilEnergy.energyPowerPerSecond(20), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_MOB_COLLECTOR_MACHINE = new ItemStack[]{SupremeComponents.RUSTLESS_MACHINE,
      new ItemStack(Material.RESPAWN_ANCHOR), SupremeComponents.RUSTLESS_MACHINE, SupremeComponents.INDUCTIVE_MACHINE,
      SupremeComponents.SYNTHETIC_RUBY, SupremeComponents.INDUCTIVE_MACHINE, SupremeComponents.ADAMANTIUM_PLATE,
      SlimefunItems.PROGRAMMABLE_ANDROID_3_BUTCHER, SupremeComponents.ADAMANTIUM_PLATE};

  public static final SlimefunItemStack MOB_COLLECTOR_MACHINE_II = new SupremeItemStack(
      "SUPREME_MOB_COLLECTOR_MACHINE_II", Material.RESPAWN_ANCHOR, "&bMob Collector II", "",
      "&fThis machine allows you to collect", "&f items from nearby mobs. (8 block)", "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), LoreBuilder.speed(5),
      LoreBuilder.powerBuffer(5000), UtilEnergy.energyPowerPerSecond(100), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_MOB_COLLECTOR_MACHINE_II = new ItemStack[]{
      SupremeComponents.CONVEYANCE_MACHINE, SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CONVEYANCE_MACHINE,
      SupremeComponents.INDUCTOR_MACHINE, MOB_COLLECTOR_MACHINE, SupremeComponents.INDUCTOR_MACHINE,
      SupremeComponents.THORNERITE, SupremeCetrus.CETRUS_IGNIS, SupremeComponents.THORNERITE};

  public static final SlimefunItemStack MOB_COLLECTOR_MACHINE_III = new SupremeItemStack(
      "SUPREME_MOB_COLLECTOR_MACHINE_III", Material.RESPAWN_ANCHOR, "&bMob Collector III", "",
      "&fThis machine allows you to collect", "&f items from nearby mobs. (16 block)", "",
      LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE), LoreBuilder.speed(15),
      LoreBuilder.powerBuffer(15000), UtilEnergy.energyPowerPerSecond(300), "", "&3Supreme Machine");
  public static final ItemStack[] RECIPE_MOB_COLLECTOR_MACHINE_III = new ItemStack[]{SupremeComponents.THORNERITE,
      SupremeAttribute.getBomb(), SupremeComponents.THORNERITE, SupremeComponents.SUPREME,
      MOB_COLLECTOR_MACHINE_II, SupremeComponents.SUPREME, SupremeComponents.CRYSTALLIZER_MACHINE,
      SupremeCetrus.CETRUS_LUMIUM, SupremeComponents.CRYSTALLIZER_MACHINE};

  private final Map<Block, MachineRecipe> processing = new HashMap<>();
  private final Map<Block, Integer> progress = new HashMap<>();
  private final Map<Block, Integer> selectedInputSlots = new HashMap<>();
  private final Map<Block, Integer> lastProgressCheckpoint = new HashMap<>();
  private final Map<Block, Long> nextIdleScan = new HashMap<>();
  private final Set<MobCollectorMachineRecipe> mobCollectorMachineRecipes = new HashSet<>();
  private int mobRange = 4;

  @ParametersAreNonnullByDefault
  public MobCollector(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
    super(category, item, recipeType, recipe);
  }

  @Override
  protected void registerDefaultRecipes() {
    SupremeOptions supremeOptions = getSupremeOptions();
    boolean customBc = supremeOptions.isCustomBc();
    recipes.clear();
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GLASS_BOTTLE, getSpeed()),
        new ItemStack(Material.HONEY_BOTTLE, getSpeed()), n -> n.getType() == EntityType.BEE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GLASS_BOTTLE, getSpeed()),
        new ItemStack(Material.INK_SAC, getSpeed()), n -> n.getType() == EntityType.SQUID));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GLASS_BOTTLE, getSpeed()),
        new ItemStack(Material.GLOW_INK_SAC, getSpeed()), n -> n.getType() == EntityType.GLOW_SQUID));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GLASS_BOTTLE, getSpeed()),
        new SlimefunItemStack(SlimefunItems.FILLED_FLASK_OF_KNOWLEDGE, getSpeed()),
        n -> n.getType() == EntityType.WITHER));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GLASS_BOTTLE, getSpeed()),
        new ItemStack(Material.DRAGON_BREATH, getSpeed()), n -> n.getType() == EntityType.ENDER_DRAGON));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.HONEYCOMB, getSpeed()), n -> n.getType() == EntityType.BEE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.WHITE_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.WHITE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.ORANGE_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.ORANGE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.MAGENTA_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.MAGENTA));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.LIGHT_BLUE_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.LIGHT_BLUE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.YELLOW_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.YELLOW));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.LIME_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.LIME));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.PINK_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.PINK));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.GRAY_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.GRAY));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.LIGHT_GRAY_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.LIGHT_GRAY));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.CYAN_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.CYAN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.PURPLE_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.PURPLE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.BLUE_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.BLUE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.BROWN_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.BROWN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.GREEN_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.GREEN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.RED_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.RED));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.BLACK_WOOL, getSpeed()),
        n -> n.getType() == EntityType.SHEEP && ((Sheep) n).getColor() == DyeColor.BLACK));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.LEATHER, getSpeed()), n -> n.getType() == EntityType.COW));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.FEATHER, getSpeed()), n -> n.getType() == EntityType.CHICKEN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.SPONGE, getSpeed()), n -> n.getType() == EntityType.GUARDIAN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.SPIDER_EYE, getSpeed()), n -> n.getType() == EntityType.SPIDER));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new ItemStack(Material.COAL, getSpeed()), n -> n.getType() == EntityType.WITHER_SKELETON));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
        new SlimefunItemStack(SlimefunItems.COMPRESSED_CARBON, getSpeed()), n -> n.getType() == EntityType.WITHER));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.SHEARS),
          new SlimefunItemStack(SlimefunItems.BASIC_CIRCUIT_BOARD, getSpeed()),
          n -> n.getType() == EntityType.IRON_GOLEM));
    }
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.PHANTOM_MEMBRANE, getSpeed()), n -> n.getType() == EntityType.PHANTOM));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.ROTTEN_FLESH, getSpeed()), n -> n.getType() == EntityType.ZOMBIE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.BONE, getSpeed()), n -> n.getType() == EntityType.SKELETON));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.GUNPOWDER, getSpeed()), n -> n.getType() == EntityType.CREEPER));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.SLIME_BALL, getSpeed()), n -> n.getType() == EntityType.SLIME));
    }
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.STRING, getSpeed()), n -> n.getType() == EntityType.SPIDER));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.WITHER_SKELETON_SKULL, getSpeed()),
          n -> n.getType() == EntityType.WITHER_SKELETON));
    }
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.ENDER_PEARL, getSpeed()), n -> n.getType() == EntityType.ENDERMAN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.BLAZE_ROD, getSpeed()), n -> n.getType() == EntityType.BLAZE));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.MAGMA_CREAM, getSpeed()), n -> n.getType() == EntityType.MAGMA_CUBE));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.NETHER_STAR, getSpeed()), n -> n.getType() == EntityType.WITHER));
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.GHAST_TEAR, getSpeed()), n -> n.getType() == EntityType.GHAST));
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.TOTEM_OF_UNDYING, getSpeed()), n -> n.getType() == EntityType.RAVAGER));
    }
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.BEEF, getSpeed()), n -> n.getType() == EntityType.COW));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.PORKCHOP, getSpeed()), n -> n.getType() == EntityType.PIG));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.CHICKEN, getSpeed()), n -> n.getType() == EntityType.CHICKEN));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.MUTTON, getSpeed()), n -> n.getType() == EntityType.SHEEP));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.SNOWBALL, getSpeed()), n -> {
          String entityType = n.getType().name();
          return "SNOW_GOLEM".equals(entityType) || "SNOWMAN".equals(entityType);
        }));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.IRON_INGOT, getSpeed()), n -> n.getType() == EntityType.IRON_GOLEM));
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.NAUTILUS_SHELL, getSpeed()), n -> n.getType() == EntityType.DROWNED));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.PRISMARINE_SHARD, getSpeed()), n -> n.getType() == EntityType.GUARDIAN));
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
          new ItemStack(Material.PRISMARINE_CRYSTALS, getSpeed()), n -> n.getType() == EntityType.ELDER_GUARDIAN));
    }
    addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.IRON_SWORD),
        new ItemStack(Material.GLASS_BOTTLE, getSpeed()), n -> n.getType() == EntityType.WITCH));
    if (!customBc) {
      addProduce(new MobCollectorMachineRecipe(new ItemStack(Material.GOLD_INGOT),
          new SlimefunItemStack(SlimefunItems.STRANGE_NETHER_GOO, getSpeed()),
          n -> n.getType() == EntityType.PIGLIN));
    }
  }

  public void addProduce(@Nonnull MobCollectorMachineRecipe produce) {
    Validate.notNull(produce, "A produce cannot be null");
    mobCollectorMachineRecipes.add(produce);
  }

  @Override
  public void preRegister() {
    addItemHandler(new SupremeBlockTicker(true, this::tick));
  }

  @Nonnull
  @Override
  public List<ItemStack> getDisplayRecipes() {
    return MobCollectorMachineRecipe.getAllRecipe();
  }

  @Nonnull
  @Override
  public String getRecipeSectionLabel(@Nonnull Player p) {
    return "&7Collects:";
  }

  @Override
  protected MachineRecipe findNextRecipe(@Nonnull BlockMenu inv) {
    Block block = inv.getBlock();
    selectedInputSlots.remove(block);
    List<LivingEntity> nearbyEntities = null;

    for (int slot : getInputSlots()) {
      ItemStack itemInSlot = inv.getItemInSlot(slot);
      if (itemInSlot == null || itemInSlot.getType().isAir()) {
        continue;
      }

      for (MobCollectorMachineRecipe produce : mobCollectorMachineRecipes) {
        ItemStack itemInInput = produce.getInput()[0];
        if (itemInInput == null || itemInSlot.getType() != itemInInput.getType()) {
          continue;
        }
        if (itemInSlot.getType() == Material.GLASS_BOTTLE
            && itemInSlot.getAmount() < Math.max(1, itemInInput.getAmount())) {
          continue;
        }
        if (!SupremeInventoryUtils.canFit(inv, getOutputSlots(), produce.getOutput())) {
          continue;
        }

        if (nearbyEntities == null) {
          nearbyEntities = getNearbyLivingEntities(block);
          if (nearbyEntities.isEmpty()) {
            return null;
          }
        }

        if (hasMatchingEntity(nearbyEntities, produce::test)) {
          selectedInputSlots.put(block, slot);
          return produce;
        }
      }
    }
    return null;
  }

  private List<LivingEntity> getNearbyLivingEntities(Block block) {
    List<LivingEntity> livingEntities = new ArrayList<>();
    for (Entity entity : block.getWorld().getNearbyEntities(
        block.getLocation(), mobRange, mobRange, mobRange)) {
      if (entity instanceof LivingEntity living) {
        livingEntities.add(living);
      }
    }
    return livingEntities;
  }

  private boolean hasMatchingEntity(List<LivingEntity> nearbyEntities,
      Predicate<LivingEntity> predicate) {
    for (LivingEntity entity : nearbyEntities) {
      if (predicate.test(entity)) {
        return true;
      }
    }
    return false;
  }

  public final MobCollector setMobRange(int value) {
    mobRange = value;
    return this;
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
      if (gameTime < nextIdleScan.getOrDefault(b, 0L)) {
        return;
      }

      MachineRecipe next = findNextRecipe(inv);
      if (next != null) {
        nextIdleScan.remove(b);
        Integer slot = selectedInputSlots.get(b);
        if (slot == null) {
          clearCollectorState(b);
          updateStatusInvalidInput(inv);
          return;
        }
        processing.put(b, next);
        progress.put(b, next.getTicks());
        lastProgressCheckpoint.put(b, next.getTicks());
        persistState(b, next, slot, next.getTicks());
      } else {
        nextIdleScan.put(b, gameTime + IDLE_SCAN_BACKOFF_TICKS);
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
      if (!commitCollectorInput(b, inv, active)) {
        clearCollectorState(b);
        updateStatusInvalidInput(inv);
        return;
      }
      SupremeInventoryUtils.pushAll(inv, getOutputSlots(), recipeOutput);
      clearCollectorState(b);
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

  private boolean commitCollectorInput(Block block, BlockMenu menu, MachineRecipe recipe) {
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

    if (current.getType() == Material.GLASS_BOTTLE) {
      int amount = Math.max(1, required.getAmount());
      if (current.getAmount() < amount) {
        return false;
      }
      menu.consumeItem(slot, amount);
      return true;
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

  private void persistState(Block block, MachineRecipe recipe, int slot, int currentProgress) {
    SupremeSpecialMachineStateCodec.save(block, STATE_TYPE, currentProgress, recipe.getTicks(),
        recipe.getInput(), recipe.getOutput(), new ItemStack[0], slot, "");
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
      if (state.inputs().length > 0 && state.outputs().length > 0 && state.auxInt() >= 0) {
        MachineRecipe recipe = new MachineRecipe(state.ticks(), state.inputs(), state.outputs());
        processing.put(block, recipe);
        progress.put(block, Math.max(0, state.progress()));
        selectedInputSlots.put(block, state.auxInt());
        lastProgressCheckpoint.put(block, Math.max(0, state.progress()));
        nextIdleScan.remove(block);
        return true;
      }
    }

    SupremeSpecialMachineStateCodec.clear(block);
    return false;
  }

  private void clearCollectorState(Block block) {
    processing.remove(block);
    progress.remove(block);
    selectedInputSlots.remove(block);
    lastProgressCheckpoint.remove(block);
    nextIdleScan.remove(block);
    SupremeSpecialMachineStateCodec.clear(block);
  }

  @Override
  protected void onMachineBreak(Block block) {
    clearCollectorState(block);
  }

  @Override
  public List<String> getMachineDiagnosticLines(Block block) {
    List<String> lines = new ArrayList<>();
    BlockMenu inv = BlockStorage.getInventory(block);
    lines.add("Machine: " + getId() + " (MOB_COLLECTOR)");
    lines.add("Charge: " + getCharge(block.getLocation()) + " J | Consumption: "
        + UtilEnergy.toPerSecond(getEnergyConsumption()) + " J/s");
    if (inv == null) {
      lines.add("No Slimefun inventory is loaded for this block.");
      return lines;
    }

    restoreStateIfNeeded(block);
    MachineRecipe active = processing.get(block);
    if (active == null) {
      lines.add("State: IDLE / waiting for valid tool and nearby mob");
      lines.add("Mob scan range: " + mobRange + " blocks | Idle scan backoff: "
          + IDLE_SCAN_BACKOFF_TICKS + " ticks");
      return lines;
    }

    int timeLeft = progress.getOrDefault(block, active.getTicks());
    String state;
    if (notHasSpaceOutput(inv, active.getOutput())) {
      state = "OUTPUT FULL";
    } else if (getCharge(block.getLocation()) < getEnergyConsumption() && timeLeft > 0) {
      state = "WAITING FOR POWER";
    } else if (timeLeft <= 0) {
      state = "READY TO COMMIT INPUT/OUTPUT";
    } else {
      state = "PROCESSING";
    }
    lines.add("State: " + state + " | Progress: " + timeLeft + "/" + active.getTicks());
    if (active.getInput().length > 0 && active.getInput()[0] != null) {
      lines.add("Input: " + describeItem(active.getInput()[0]) + " x" + active.getInput()[0].getAmount()
          + " | Slot: " + selectedInputSlots.getOrDefault(block, -1));
    }
    if (active.getOutput().length > 0 && active.getOutput()[0] != null) {
      lines.add("Output: " + describeItem(active.getOutput()[0]) + " x" + active.getOutput()[0].getAmount());
    }
    lines.add("Mob scan range: " + mobRange + " blocks");
    return lines;
  }

  private String describeItem(ItemStack item) {
    SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
    return slimefunItem != null ? slimefunItem.getId() : item.getType().getKey().toString();
  }

  @Nonnull
  @Override
  public String getMachineIdentifier() {
    return "MOB_COLLECTOR";
  }

  @Override
  public ItemStack getProgressBar() {
    return new ItemStack(Material.IRON_SWORD);
  }

  public MachineRecipe getProcessing(Block b) {
    return processing.get(b);
  }

  public boolean isProcessing(Block b) {
    return getProcessing(b) != null;
  }
}
