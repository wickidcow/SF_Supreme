package com.github.relativobr.supreme.machine.tech;

import com.github.relativobr.supreme.Supreme;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Tech Generator wrapper that repairs legacy/invalid overstacked output safely before the normal
 * generator tick runs. Existing item counts are preserved: overflow is split into legal stacks and
 * reinserted into the generator output. If the output is completely full, remaining overflow is
 * dropped at the machine instead of being silently deleted.
 */
public final class SafeTechGenerator extends TechGenerator {

  private static final int HARD_MAX_STACK = 64;

  public SafeTechGenerator(io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack item,
      ItemStack[] recipe) {
    super(item, recipe);
  }

  @Override
  public void tick(Block block) {
    BlockMenu inventory = BlockStorage.getInventory(block);
    if (inventory != null) {
      sanitizeOutputSlots(block, inventory);
    }
    super.tick(block);
  }

  private void sanitizeOutputSlots(Block block, BlockMenu inventory) {
    List<ItemStack> overflow = new ArrayList<>();
    int correctedSlots = 0;
    int overflowItems = 0;

    for (int slot : getOutputSlots()) {
      ItemStack current = inventory.getItemInSlot(slot);
      if (current == null || current.getType() == Material.AIR) {
        continue;
      }

      int legalMax = legalStackSize(current);
      if (current.getAmount() <= legalMax) {
        continue;
      }

      int remainder = current.getAmount() - legalMax;
      ItemStack repaired = current.clone();
      repaired.setAmount(legalMax);
      inventory.replaceExistingItem(slot, repaired);
      correctedSlots++;
      overflowItems += remainder;

      while (remainder > 0) {
        int stackSize = Math.min(legalMax, remainder);
        ItemStack split = current.clone();
        split.setAmount(stackSize);
        overflow.add(split);
        remainder -= stackSize;
      }
    }

    if (overflow.isEmpty()) {
      return;
    }

    int dropped = 0;
    for (ItemStack split : overflow) {
      ItemStack leftover = inventory.pushItem(split, getOutputSlots());
      if (leftover != null && leftover.getType() != Material.AIR && leftover.getAmount() > 0) {
        dropped += leftover.getAmount();
        if (block.getWorld() != null) {
          block.getWorld().dropItemNaturally(block.getLocation(), leftover);
        }
      }
    }

    Supreme.inst().log(Level.WARNING,
        "[TechGenerator] Repaired " + correctedSlots + " overstacked output slot(s) at "
            + block.getWorld().getName() + "@" + block.getX() + "," + block.getY() + "," + block.getZ()
            + "; preserved overflow=" + overflowItems + (dropped > 0 ? ", dropped=" + dropped : ""));
  }

  private static int legalStackSize(ItemStack item) {
    return Math.max(1, Math.min(HARD_MAX_STACK, item.getMaxStackSize()));
  }
}
