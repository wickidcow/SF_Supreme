package com.github.relativobr.supreme.util;

import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Shared output-capacity checks that avoid partial insertion and item loss. */
public final class SupremeInventoryUtils {

  private SupremeInventoryUtils() {}

  @ParametersAreNonnullByDefault
  public static boolean canFit(BlockMenu menu, int[] slots, ItemStack... outputs) {
    List<ItemStack> simulated = new ArrayList<>(slots.length);
    for (int slot : slots) {
      ItemStack current = menu.getItemInSlot(slot);
      simulated.add(current == null ? null : current.clone());
    }

    for (ItemStack output : outputs) {
      if (output == null || output.getType() == Material.AIR || output.getAmount() <= 0) {
        continue;
      }
      int remaining = output.getAmount();

      for (ItemStack current : simulated) {
        if (current == null || current.getType() == Material.AIR) {
          continue;
        }
        if (SlimefunUtils.isItemSimilar(current, output, false, false)) {
          int capacity = Math.max(0, Math.min(current.getMaxStackSize(), output.getMaxStackSize())
              - current.getAmount());
          int moved = Math.min(capacity, remaining);
          current.setAmount(current.getAmount() + moved);
          remaining -= moved;
          if (remaining == 0) {
            break;
          }
        }
      }

      for (int i = 0; remaining > 0 && i < simulated.size(); i++) {
        ItemStack current = simulated.get(i);
        if (current == null || current.getType() == Material.AIR) {
          int moved = Math.min(output.getMaxStackSize(), remaining);
          ItemStack inserted = output.clone();
          inserted.setAmount(moved);
          simulated.set(i, inserted);
          remaining -= moved;
        }
      }

      if (remaining > 0) {
        return false;
      }
    }
    return true;
  }

  @ParametersAreNonnullByDefault
  public static void pushAll(BlockMenu menu, int[] slots, ItemStack... outputs) {
    for (ItemStack output : outputs) {
      if (output == null || output.getType() == Material.AIR || output.getAmount() <= 0) {
        continue;
      }
      ItemStack leftover = menu.pushItem(output.clone(), slots);
      if (leftover != null && menu.getLocation().getWorld() != null) {
        menu.getLocation().getWorld().dropItemNaturally(menu.getLocation(), leftover);
      }
    }
  }
}
