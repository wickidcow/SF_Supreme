package com.github.relativobr.supreme.util;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Explicit, single-slot historical presentation migration.
 *
 * <p>This path is NEVER called by join, pickup, cargo, barrels, inventory-open or machine
 * listeners. It runs only after an administrator uses the separate confirm command.
 * The only permitted write is the player's current main-hand slot, and a rejected
 * candidate never writes anything. It never replaces an item with a guide template.
 */
public final class HistoricalHeldItemRepair {

  private HistoricalHeldItemRepair() {}

  public record Result(boolean repaired, String reason) {}

  public static Result applyOne(PlayerInventory inventory, ItemStack registered) {
    // Capture complete, independent source snapshots before any write.
    ItemStack held = inventory.getItemInMainHand();
    ItemStack reference = inventory.getItemInOffHand();
    HistoricalItemPresentationRepair.Candidate candidate =
        HistoricalItemPresentationRepair.prepare(held, reference, registered);
    if (!candidate.ready()) {
      return new Result(false, candidate.reason());
    }

    ItemStack original = held.clone();
    ItemStack originalReference = reference.clone();
    ItemStack replacement = candidate.replacement();
    if (replacement == null || replacement.getAmount() != original.getAmount()
        || !replacement.isSimilar(registered)) {
      return new Result(false, "Prepared replacement failed strict identity or amount checks.");
    }

    // Re-read both hands immediately before the write. A prior preview is NOT trusted;
    // every confirmation revalidates current held items against the current registry.
    if (!Objects.equals(inventory.getItemInMainHand(), original)
        || !Objects.equals(inventory.getItemInOffHand(), originalReference)) {
      return new Result(false, "Held item changed during confirmation: nothing repaired.");
    }

    try {
      inventory.setItemInMainHand(replacement.clone());
      ItemStack actual = inventory.getItemInMainHand();
      ItemStack other = inventory.getItemInOffHand();
      if (Objects.equals(actual, replacement)
          && Objects.equals(other, originalReference)
          && actual.getAmount() == original.getAmount()
          && actual.isSimilar(registered)) {
        return new Result(true, "Repaired ONE held stack, preserving quantity and all functional metadata.");
      }
    } catch (RuntimeException | LinkageError ex) {
      return rollback(inventory, original, originalReference,
          "A player-inventory write failed (" + ex.getClass().getSimpleName() + ").");
    }

    return rollback(inventory, original, originalReference, "Post-write verification failed.");
  }

  private static Result rollback(PlayerInventory inventory, ItemStack original,
      ItemStack originalReference, String why) {
    try {
      inventory.setItemInMainHand(original.clone());
      if (Objects.equals(inventory.getItemInMainHand(), original)
          && Objects.equals(inventory.getItemInOffHand(), originalReference)) {
        return new Result(false, why + " Original main-hand stack restored.");
      }
    } catch (RuntimeException | LinkageError ignored) {
      // A failed rollback is explicitly reported. Never silently claim success.
    }
    return new Result(false, "CRITICAL: " + why
        + " Original main/off-hand state could not be verified after rollback; restore from backup.");
  }
}
