package com.github.relativobr.supreme.generic.electric;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.compat.SupremeEnergyProvider;
import com.github.relativobr.supreme.libs.guizhanlib.slimefun.machines.MenuBlock;
import com.github.relativobr.supreme.util.UtilEnergy;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/** Energy generator whose cached generation state is isolated per placed block. */
public final class EnergyGenerator extends MenuBlock implements SupremeEnergyProvider {

  private static final Method LONG_CHARGE_ACCESSOR = findLongChargeAccessor();

  private final Map<BlockPosition, Integer> generatedOutput = new ConcurrentHashMap<>();
  private final Map<BlockPosition, Integer> currentDelay = new ConcurrentHashMap<>();
  private int energy;
  private int buffer;
  private GenerationType type;

  public EnergyGenerator(ItemGroup categories, SlimefunItemStack item, ItemStack[] recipe) {
    super(categories, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
  }

  public GenerationType getType() {
    return type;
  }

  public EnergyGenerator setType(GenerationType value) {
    type = value;
    return this;
  }

  public EnergyGenerator setEnergy(int value) {
    energy = value;
    return this;
  }

  public EnergyGenerator setBuffer(int value) {
    buffer = value;
    return this;
  }

  @Nonnull
  @Override
  public EnergyNetComponentType getEnergyComponentType() {
    return EnergyNetComponentType.GENERATOR;
  }

  @Override
  protected void setup(BlockMenuPreset preset) {
    preset.drawBackground(new int[] {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26
    });
  }

  @Nonnull
  @Override
  protected int[] getInputSlots(DirtyChestMenu menu, ItemStack itemStack) {
    return new int[0];
  }

  @Override
  protected int[] getInputSlots() {
    return new int[0];
  }

  @Override
  protected int[] getOutputSlots() {
    return new int[0];
  }

  @Override
  public int getSupremeGeneratedOutput(Location location) {
    if (location == null || location.getWorld() == null || type == null) {
      return 0;
    }

    BlockPosition position = new BlockPosition(location);
    int generation = generatedOutput.getOrDefault(position, 0);
    int delay = currentDelay.getOrDefault(position, 0);
    int cacheTicks = Math.max(0,
        Supreme.getSupremeOptions().getDelayTimeValidGenerators());

    if (generation > 0 && delay < cacheTicks) {
      currentDelay.put(position, delay + 1);
    } else {
      generation = Math.max(0, type.generate(location.getWorld(), location.getBlock(), energy));
      generatedOutput.put(position, generation);
      currentDelay.put(position, 0);
    }

    BlockMenu menu = BlockStorage.getInventory(location);
    if (menu != null && menu.hasViewer()) {
      // Preserve the historical int-clamped status text. The persisted balance
      // and the generator's output/cadence remain completely untouched.
      int shownCharge = readDisplayedCharge(this, location);
      if (generation == 0) {
        menu.replaceExistingItem(13, new CustomItemStack(
            Material.RED_STAINED_GLASS_PANE,
            "&cNot generating",
            "&7Type: &6" + type,
            "&7Stored: &6" + UtilEnergy.format(shownCharge) + " J",
            "&7Capacity: &6" + UtilEnergy.format(buffer) + " J"));
      } else {
        menu.replaceExistingItem(13, new CustomItemStack(
            Material.GREEN_STAINED_GLASS_PANE,
            "&aGeneration",
            "&7Type: &6" + type,
            "&7Generating: &6" + UtilEnergy.format(UtilEnergy.toPerSecond(generation)) + " J/s ",
            "&7Stored: &6" + UtilEnergy.format(shownCharge) + " J",
            "&7Capacity: &6" + UtilEnergy.format(buffer) + " J"));
      }
    }
    return generation;
  }

  /**
   * Newer Legacy/United cores expose a long-valued charge reader; older Gugu
   * releases do not. Resolve this optional descriptor once rather than linking
   * to a method absent from an otherwise supported API at load time.
   */
  private static Method findLongChargeAccessor() {
    try {
      Method method = EnergyNetComponent.class.getMethod("getChargeLong", Location.class);
      return method.getReturnType() == long.class ? method : null;
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  /**
   * Read-only menu presentation: never change the stored balance or the
   * legacy signed-int display limit, irrespective of the Slimefun fork.
   */
  static int readDisplayedCharge(EnergyNetComponent component, Location location) {
    return readDisplayedCharge(component, location, LONG_CHARGE_ACCESSOR);
  }

  // The injected reader permits a direct regression test of the historical
  // Gugu fallback without changing the actual runtime lookup policy.
  static int readDisplayedCharge(EnergyNetComponent component, Location location, Method reader) {
    if (reader == null) {
      return legacyIntCharge(component, location);
    }
    try {
      return legacyDisplayedCharge(((Number) reader.invoke(component, location)).longValue());
    } catch (IllegalAccessException | InvocationTargetException ex) {
      throw new IllegalStateException("Unable to read generator charge for display", ex);
    }
  }

  // Older forks cannot link getChargeLong; retain their historical int API only
  // at this isolated compatibility boundary. Do not remove the fallback.
  @SuppressWarnings("deprecation")
  private static int legacyIntCharge(EnergyNetComponent component, Location location) {
    return component.getCharge(location);
  }

  /**
   * Mirrors Slimefun Legacy's original getCharge(Location) signed-int clamp
   * for display only. Never modify the underlying energy balance.
   */
  static int legacyDisplayedCharge(long charge) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, charge));
  }

  @Override
  protected void onBreak(BlockBreakEvent event, BlockMenu menu) {
    BlockPosition position = new BlockPosition(event.getBlock().getLocation());
    generatedOutput.remove(position);
    currentDelay.remove(position);
    super.onBreak(event, menu);
  }

  @Override
  public int getCapacity() {
    return buffer;
  }
}
