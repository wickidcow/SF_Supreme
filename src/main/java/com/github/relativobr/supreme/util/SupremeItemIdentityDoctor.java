package com.github.relativobr.supreme.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Read-only comparison of two real item stacks for administrators diagnosing machine output.
 * No metadata is modified and no saved inventory is read or rewritten.
 */
public final class SupremeItemIdentityDoctor {

  private static final int MAX_DIFFERENCES = 14;

  private SupremeItemIdentityDoctor() {}

  public static List<String> compare(ItemStack mainHand, ItemStack offHand) {
    List<String> lines = new ArrayList<>();
    if (mainHand == null || mainHand.getType() == Material.AIR
        || offHand == null || offHand.getType() == Material.AIR) {
      lines.add("Hold a Tech Generator output in your main hand and its crafted/cheat counterpart in your off hand.");
      return lines;
    }

    lines.add("Main=" + mainHand.getType() + " x" + mainHand.getAmount()
        + " | Off=" + offHand.getType() + " x" + offHand.getAmount());
    lines.add("Bukkit stackable (ignoring quantity): " + mainHand.isSimilar(offHand));
    if (mainHand.getType() != offHand.getType()) {
      lines.add("Different materials; item metadata cannot make these stack.");
      return lines;
    }

    ItemMeta main = mainHand.getItemMeta();
    ItemMeta off = offHand.getItemMeta();
    if (main == null || off == null) {
      lines.add("Item meta present: main=" + (main != null) + ", off=" + (off != null));
      return lines;
    }

    boolean namesEqual = Objects.equals(main.displayName(), off.displayName());
    boolean loreEqual = Objects.equals(main.lore(), off.lore());
    lines.add("Adventure display name equal: " + namesEqual);
    lines.add("Adventure lore equal: " + loreEqual);
    if (!namesEqual) {
      lines.add("Main name: " + shortText(main.displayName()));
      lines.add("Off name: " + shortText(off.displayName()));
    }
    if (!loreEqual) {
      lines.add("Main lore lines=" + (main.lore() == null ? 0 : main.lore().size())
          + " | Off lore lines=" + (off.lore() == null ? 0 : off.lore().size()));
    }

    Set<String> mainKeys = new TreeSet<>();
    main.getPersistentDataContainer().getKeys().forEach(key -> mainKeys.add(key.toString()));
    Set<String> offKeys = new TreeSet<>();
    off.getPersistentDataContainer().getKeys().forEach(key -> offKeys.add(key.toString()));
    lines.add("PDC keys equal: " + mainKeys.equals(offKeys));
    if (!mainKeys.equals(offKeys)) {
      Set<String> mainOnly = new TreeSet<>(mainKeys);
      mainOnly.removeAll(offKeys);
      Set<String> offOnly = new TreeSet<>(offKeys);
      offOnly.removeAll(mainKeys);
      lines.add("Main-only PDC keys: " + mainOnly);
      lines.add("Off-only PDC keys: " + offOnly);
    }

    List<String> changedPaths = new ArrayList<>();
    findDifferences(main.serialize(), off.serialize(), "meta", changedPaths);
    if (changedPaths.isEmpty() && !mainHand.isSimilar(offHand)) {
      lines.add("Bukkit reports a difference outside serialized ItemMeta; inspect full item components.");
    } else {
      lines.add("Different serialized meta paths (up to " + MAX_DIFFERENCES + "): "
          + (changedPaths.isEmpty() ? "(none)" : String.join(", ", changedPaths)));
    }
    return lines;
  }

  private static void findDifferences(Map<?, ?> left, Map<?, ?> right, String path,
      List<String> changed) {
    List<Object> keys = new ArrayList<>(left.keySet());
    right.keySet().stream().filter(key -> !left.containsKey(key)).forEach(keys::add);
    keys.sort(java.util.Comparator.comparing(String::valueOf));
    for (Object key : keys) {
      if (changed.size() >= MAX_DIFFERENCES) {
        return;
      }
      Object a = left.get(key);
      Object b = right.get(key);
      String child = path + "." + key;
      if (!left.containsKey(key) || !right.containsKey(key)) {
        changed.add(child);
      } else if (a instanceof Map<?, ?> leftMap && b instanceof Map<?, ?> rightMap) {
        findDifferences(leftMap, rightMap, child, changed);
      } else if (!Objects.deepEquals(a, b)) {
        changed.add(child);
      }
    }
  }

  private static String shortText(Object component) {
    String value = String.valueOf(component);
    return value.length() <= 240 ? value : value.substring(0, 240) + "...";
  }
}
