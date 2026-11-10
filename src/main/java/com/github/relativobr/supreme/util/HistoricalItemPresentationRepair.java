package com.github.relativobr.supreme.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Explicit, single-held-stack repair preparation for three static Legacy core materials.
 *
 * <p>Never visits inventories, storage, barrel templates, item entities or historical records.
 * Only the caller may choose to install the independently cloned replacement. Functional
 * item metadata, including skull profiles, model components, typed PDC and amounts, is
 * required to match the registered template exactly before any presentation is copied.
 */
public final class HistoricalItemPresentationRepair {
  private static final Set<String> SUPPORTED = Set.of(
      "CARBONADO", "SYNTHETIC_SAPPHIRE", "SMALL_URANIUM");
  private static final NamespacedKey SLIMEFUN_ID =
      new NamespacedKey("slimefun", "slimefun_item");
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  private HistoricalItemPresentationRepair() {}

  public record Candidate(String reason, ItemStack replacement) {
    public Candidate {
      if (replacement != null) replacement = replacement.clone();
    }

    public boolean ready() {
      return replacement != null;
    }

    @Override
    public ItemStack replacement() {
      return replacement == null ? null : replacement.clone();
    }
  }

  public static Candidate prepare(ItemStack historical, ItemStack reference, ItemStack registered) {
    if (!valid(historical) || !valid(reference) || !valid(registered)) {
      return skipped("Hold one old stack in MAIN and a fresh stack in OFF; registration must exist.");
    }
    if (historical.getType() != reference.getType()
        || historical.getType() != registered.getType()) {
      return skipped("Different item materials: no repair.");
    }
    ItemMeta oldMeta = historical.getItemMeta();
    ItemMeta freshMeta = reference.getItemMeta();
    ItemMeta registeredMeta = registered.getItemMeta();
    if (oldMeta == null || freshMeta == null || registeredMeta == null) {
      return skipped("Missing ItemMeta: no repair.");
    }

    String id = itemId(oldMeta);
    if (id == null || !SUPPORTED.contains(id)) {
      return skipped("Only CARBONADO, SYNTHETIC_SAPPHIRE and SMALL_URANIUM are supported.");
    }
    if (!Objects.equals(id, itemId(freshMeta))
        || !Objects.equals(id, itemId(registeredMeta))) {
      return skipped("Slimefun IDs differ: no repair.");
    }
    if (!reference.isSimilar(registered)) {
      return skipped("Fresh reference does not match current registered item: no repair.");
    }
    if (historical.isSimilar(registered)) {
      return skipped("Old item already matches current item.");
    }
    if (hasSensitivePresentation(oldMeta.displayName())
        || hasSensitivePresentation(freshMeta.displayName())
        || hasSensitivePresentation(registeredMeta.displayName())
        || hasSensitiveLore(oldMeta.lore())
        || hasSensitiveLore(freshMeta.lore())
        || hasSensitiveLore(registeredMeta.lore())) {
      return skipped("Interactive, translated or custom-font presentation must not be overwritten.");
    }
    if (!sameText(oldMeta.displayName(), registeredMeta.displayName())
        || !sameLoreText(oldMeta.lore(), registeredMeta.lore())) {
      return skipped("Visible item name/lore text differs; preserve custom or stateful text.");
    }
    if (!sameWithoutPresentation(historical, registered)) {
      return skipped("Non-presentation metadata differs; preserve PDC/model/skull/other state.");
    }

    ItemStack candidate = historical.clone();
    ItemMeta replacementMeta = candidate.getItemMeta();
    replacementMeta.displayName(registeredMeta.displayName());
    replacementMeta.lore(registeredMeta.lore());
    if (!candidate.setItemMeta(replacementMeta)
        || candidate.getAmount() != historical.getAmount()
        || !candidate.isSimilar(registered)) {
      return skipped("Final strict similarity check failed; no repair.");
    }
    return new Candidate("READY: exact registered presentation on a detached clone.", candidate);
  }

  private static boolean valid(ItemStack item) {
    return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
  }

  private static String itemId(ItemMeta meta) {
    return meta.getPersistentDataContainer().get(SLIMEFUN_ID, PersistentDataType.STRING);
  }

  private static boolean hasSensitivePresentation(Component component) {
    if (component == null) return false;
    if (!(component instanceof TextComponent)
        || component.clickEvent() != null || component.hoverEvent() != null
        || component.insertion() != null || component.style().font() != null) return true;
    for (Component child : component.children()) {
      if (hasSensitivePresentation(child)) return true;
    }
    return false;
  }

  private static boolean hasSensitiveLore(List<Component> lore) {
    if (lore == null) return false;
    for (Component line : lore) {
      if (hasSensitivePresentation(line)) return true;
    }
    return false;
  }

  private static boolean sameText(Component left, Component right) {
    return left != null && right != null && PLAIN.serialize(left).equals(PLAIN.serialize(right));
  }

  private static boolean sameLoreText(List<Component> left, List<Component> right) {
    if (left == null || right == null || left.size() != right.size()) return false;
    for (int i = 0; i < left.size(); i++) {
      if (!sameText(left.get(i), right.get(i))) return false;
    }
    return true;
  }

  private static boolean sameWithoutPresentation(ItemStack historical, ItemStack current) {
    ItemStack oldCopy = historical.clone();
    ItemStack newCopy = current.clone();
    ItemMeta oldMeta = oldCopy.getItemMeta();
    ItemMeta newMeta = newCopy.getItemMeta();
    if (oldMeta == null || newMeta == null) return false;
    oldMeta.displayName(null);
    newMeta.displayName(null);
    oldMeta.lore(null);
    newMeta.lore(null);
    oldCopy.setItemMeta(oldMeta);
    newCopy.setItemMeta(newMeta);
    return oldCopy.isSimilar(newCopy);
  }

  private static Candidate skipped(String reason) {
    return new Candidate(reason, null);
  }
}
