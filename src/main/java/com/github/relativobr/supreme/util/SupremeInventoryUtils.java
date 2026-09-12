package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Shared inventory helpers for safe machine output and lightweight idle-state checks. */
public final class SupremeInventoryUtils {

  private SupremeInventoryUtils() {}

  @ParametersAreNonnullByDefault
  public static boolean canFit(BlockMenu menu, int[] slots, ItemStack... outputs) {
    ItemStack[] simulatedItems = new ItemStack[slots.length];
    int[] simulatedAmounts = new int[slots.length];

    for (int i = 0; i < slots.length; i++) {
      ItemStack current = menu.getItemInSlot(slots[i]);
      if (current != null && current.getType() != Material.AIR) {
        simulatedItems[i] = current;
        simulatedAmounts[i] = current.getAmount();
      }
    }

    for (ItemStack output : outputs) {
      if (output == null || output.getType() == Material.AIR || output.getAmount() <= 0) {
        continue;
      }

      int remaining = output.getAmount();
      for (int i = 0; i < simulatedItems.length && remaining > 0; i++) {
        ItemStack current = simulatedItems[i];
        if (current == null || !SlimefunUtils.isItemSimilar(current, output, false, false)) {
          continue;
        }

        int capacity = Math.max(0,
            Math.min(current.getMaxStackSize(), output.getMaxStackSize()) - simulatedAmounts[i]);
        int moved = Math.min(capacity, remaining);
        simulatedAmounts[i] += moved;
        remaining -= moved;
      }

      for (int i = 0; i < simulatedItems.length && remaining > 0; i++) {
        if (simulatedItems[i] != null) {
          continue;
        }

        int moved = Math.min(output.getMaxStackSize(), remaining);
        simulatedItems[i] = output;
        simulatedAmounts[i] = moved;
        remaining -= moved;
      }

      if (remaining > 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a cheap runtime fingerprint for the requested menu slots. This is only used to wake
   * idle-machine recipe checks when their relevant inventory changes; it is never persisted or
   * used as an item identity key, so a hash collision can at worst defer a retry by a few ticks.
   */
  @ParametersAreNonnullByDefault
  public static int fingerprint(BlockMenu menu, int[]... slotGroups) {
    int hash = 1;
    for (int[] slots : slotGroups) {
      for (int slot : slots) {
        ItemStack item = menu.getItemInSlot(slot);
        hash = 31 * hash + slot;
        hash = 31 * hash + (item == null || item.getType().isAir() ? 0 : item.hashCode());
      }
    }
    return hash;
  }

  /**
   * Pushes complete machine outputs into their destination slots. Normal callers preflight output
   * capacity before reaching this method, so a leftover can only occur if another inventory write
   * races that preflight. Preserve that leftover instead of voiding it, but create the item entity
   * on the owning Paper region so asynchronous Slimefun machine tickers cannot trip AsyncCatcher.
   */
  @ParametersAreNonnullByDefault
  public static void pushAll(BlockMenu menu, int[] slots, ItemStack... outputs) {
    for (ItemStack output : outputs) {
      if (output == null || output.getType() == Material.AIR || output.getAmount() <= 0) {
        continue;
      }
      ItemStack leftover = menu.pushItem(output.clone(), slots);
      if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
        dropItemNaturallySafe(menu.getLocation(), leftover);
      }
    }
  }

  @ParametersAreNonnullByDefault
  private static void dropItemNaturallySafe(Location location, ItemStack item) {
    if (location.getWorld() == null || item.getType().isAir() || item.getAmount() <= 0) {
      return;
    }

    Location dropLocation = location.clone();
    ItemStack dropped = item.clone();
    Bukkit.getRegionScheduler().execute(Supreme.inst(), dropLocation,
        () -> dropLocation.getWorld().dropItemNaturally(dropLocation, dropped));
  }
}
