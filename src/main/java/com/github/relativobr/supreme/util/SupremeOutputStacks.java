package com.github.relativobr.supreme.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Preserves the exact metadata of machine output when splitting legacy oversized stacks. */
public final class SupremeOutputStacks {
  private SupremeOutputStacks() {}

  public static List<ItemStack> splitOverflow(ItemStack source, int remainder, int legalMax) {
    List<ItemStack> splits = new ArrayList<>();
    while (remainder > 0) {
      int stackSize = Math.min(legalMax, remainder);
      ItemStack split = source.clone();
      split.setAmount(stackSize);
      splits.add(split);
      remainder -= stackSize;
    }
    return splits;
  }
}
