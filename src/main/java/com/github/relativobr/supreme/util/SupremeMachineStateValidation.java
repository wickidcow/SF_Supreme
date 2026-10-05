package com.github.relativobr.supreme.util;

import org.bukkit.inventory.ItemStack;

/** Read-only checks shared by transport, restoration and break preflight. */
public final class SupremeMachineStateValidation {

  private SupremeMachineStateValidation() {
  }

  public static boolean hasUsableItems(ItemStack[] items) {
    if (items == null || items.length == 0) return false;
    for (ItemStack item : items) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) return false;
    }
    return true;
  }

  public static boolean isUsable(SupremeSpecialMachineStateCodec.State state, int[] inputSlots) {
    if (state == null || state.type() == null) return false;
    if (state.inputs() == null || state.outputs() == null || state.reservedItems() == null) return false;
    if (state.progress() < 0 || state.ticks() < 0 || state.progress() > state.ticks()
        || !hasUsableItems(state.outputs())) return false;
    return switch (state.type()) {
      case "VIRTUAL_GARDEN" -> hasUsableItems(state.inputs()) && state.reservedItems().length == 0;
      case "VIRTUAL_AQUARIUM" -> hasUsableItems(state.inputs()) && state.outputs().length == 1
          && state.reservedItems().length == 0 && isInputSlot(inputSlots, state.auxInt());
      case "MOB_COLLECTOR" -> hasUsableItems(state.inputs())
          && state.reservedItems().length == 0 && isInputSlot(inputSlots, state.auxInt());
      case "TECH_ROBOTIC" -> state.inputs().length == 0 && state.outputs().length == 1
          && state.reservedItems().length == 1 && hasUsableItems(state.reservedItems());
      case "TECH_MUTATION" -> state.inputs().length == 0 && state.outputs().length == 1
          && state.reservedItems().length == 2 && hasUsableItems(state.reservedItems())
          && state.auxInt() >= 0 && state.auxInt() <= 100
          && ("".equals(state.auxText()) || "true".equalsIgnoreCase(state.auxText())
              || "false".equalsIgnoreCase(state.auxText()));
      default -> false;
    };
  }

  private static boolean isInputSlot(int[] inputSlots, int slot) {
    if (inputSlots == null) return false;
    for (int input : inputSlots) if (slot == input) return true;
    return false;
  }
}
