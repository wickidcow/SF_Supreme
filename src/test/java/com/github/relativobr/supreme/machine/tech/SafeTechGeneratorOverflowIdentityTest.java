package com.github.relativobr.supreme.machine.tech;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SafeTechGeneratorOverflowIdentityTest {

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void overflowSplitsPreserveExactNameLoreAndTypedSlimefunIdentity() {
    MockBukkit.mock();
    ItemStack source = new ItemStack(Material.DIAMOND, 99);
    ItemMeta meta = source.getItemMeta();
    Component name = Component.text("").append(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, false));
    Component lore = Component.text("").append(Component.text("Black Diamond", NamedTextColor.GRAY));
    meta.displayName(name);
    meta.lore(List.of(lore));
    NamespacedKey id = new NamespacedKey("slimefun", "slimefun_item");
    NamespacedKey counter = new NamespacedKey("supreme", "overflow_counter");
    meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, "CARBONADO");
    meta.getPersistentDataContainer().set(counter, PersistentDataType.LONG, 123L);
    source.setItemMeta(meta);
    ItemStack before = source.clone();

    List<ItemStack> split = SafeTechGenerator.splitOverflow(source, 70, 64);
    assertEquals(2, split.size());
    assertEquals(64, split.get(0).getAmount());
    assertEquals(6, split.get(1).getAmount());
    assertEquals(before, source, "splitting overflow must not mutate the original stack");
    for (ItemStack item : split) {
      assertNotSame(source, item);
      assertTrue(source.isSimilar(item));
      assertEquals(name, item.getItemMeta().displayName());
      assertEquals(List.of(lore), item.getItemMeta().lore());
      assertEquals("CARBONADO", item.getItemMeta().getPersistentDataContainer()
          .get(id, PersistentDataType.STRING));
      assertEquals(123L, item.getItemMeta().getPersistentDataContainer()
          .get(counter, PersistentDataType.LONG));
    }
  }
}
