package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.Optional;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Input reservation shared by the synchronized Tech Robotic and Tech Mutation engines. */
public final class SupremeTechInputReservation {

  private SupremeTechInputReservation() {
  }

  public record Reservation(ItemStack[] items, SupremeSpecialMachineStateCodec.PreparedState checkpoint) {
  }

  /**
   * Copies the supplied stacks, encodes their checkpoint, then consumes the requested quantities.
   * Recipe templates are used only for matching. Failed preparation leaves every input untouched.
   * This is not a disk transaction between inventory storage and the later checkpoint write.
   */
  public static Optional<Reservation> tryReserve(Block block, BlockMenu menu, int[] slots,
      int[] amounts, ItemStack[] required, boolean checkRecipeAmount, String type, ItemStack output,
      int ticks, int auxInt) {
    if (slots.length != amounts.length || slots.length != required.length) {
      throw new IllegalArgumentException("Reservation slots, quantities and recipes must align");
    }
    ItemStack[] visible = new ItemStack[slots.length];
    ItemStack[] reserved = new ItemStack[slots.length];
    SupremeSpecialMachineStateCodec.PreparedState checkpoint;
    try {
      for (int i = 0; i < slots.length; i++) {
        ItemStack supplied = menu.getItemInSlot(slots[i]);
        if (amounts[i] <= 0 || supplied == null || supplied.getType().isAir()
            || supplied.getAmount() < amounts[i]
            || !SlimefunUtils.isItemSimilar(supplied, required[i], false, checkRecipeAmount)) {
          return Optional.empty();
        }
        visible[i] = supplied.clone();
        reserved[i] = visible[i].clone();
        reserved[i].setAmount(amounts[i]);
      }
      checkpoint = SupremeSpecialMachineStateCodec.prepare(type, ticks, ticks,
          new ItemStack[0], new ItemStack[]{output}, reserved, auxInt, "");
      // Abort before any consumption if a slot changed while the checkpoint was prepared.
      for (int i = 0; i < slots.length; i++) {
        if (!visible[i].equals(menu.getItemInSlot(slots[i]))) return Optional.empty();
      }
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING, "Could not reserve Supreme machine inputs at "
          + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + ","
          + block.getZ() + ": " + ex.getMessage());
      return Optional.empty();
    }
    for (int i = 0; i < slots.length; i++) {
      menu.consumeItem(slots[i], amounts[i]);
    }
    return Optional.of(new Reservation(reserved, checkpoint));
  }
}
