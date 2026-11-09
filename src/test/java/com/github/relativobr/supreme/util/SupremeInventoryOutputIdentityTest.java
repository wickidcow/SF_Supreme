package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Production-boundary identity regression: the Supreme Tech Generator delegates to pushAll,
 * which must not rebuild item display components or discard functional PDC.
 * This test intentionally does NOT assert that historical server items are equivalent.
 */
class SupremeInventoryOutputIdentityTest {

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void pushAllPreservesNestedComponentsAndTypedDataWithoutMutatingSource() {
    MockBukkit.mock();
    ItemStack source = new ItemStack(Material.DIAMOND, 17);
    ItemMeta meta = source.getItemMeta();
    Component nestedName = Component.text("")
        .append(Component.text("Carbonado", NamedTextColor.AQUA).decoration(
            net.kyori.adventure.text.format.TextDecoration.BOLD, false));
    meta.displayName(nestedName);
    meta.lore(java.util.List.of(Component.text("")
        .append(Component.text("Black Diamond", NamedTextColor.GRAY))));
    NamespacedKey id = new NamespacedKey("slimefun", "slimefun_item");
    NamespacedKey owner = new NamespacedKey("supreme", "identity_test_owner");
    NamespacedKey count = new NamespacedKey("supreme", "identity_test_counter");
    meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, "CARBONADO");
    meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "original-owner");
    meta.getPersistentDataContainer().set(count, PersistentDataType.LONG, 123456789L);
    source.setItemMeta(meta);
    ItemStack before = source.clone();

    BlockMenu menu = mock(BlockMenu.class);
    when(menu.pushItem(any(ItemStack.class), any(int[].class))).thenReturn(null);

    SupremeInventoryUtils.pushAll(menu, new int[]{19, 20}, source);

    var captured = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
    var slots = org.mockito.ArgumentCaptor.forClass(int[].class);
    verify(menu).pushItem(captured.capture(), slots.capture());
    assertArrayEquals(new int[]{19, 20}, slots.getValue());
    ItemStack inserted = captured.getValue();
    assertNotSame(source, inserted);
    assertEquals(before, source, "production must not mutate the recipe source");
    assertTrue(before.isSimilar(inserted), "output must retain exact Bukkit stack identity");
    assertEquals(17, inserted.getAmount());
    assertEquals(nestedName, inserted.getItemMeta().displayName());
    assertEquals(before.getItemMeta().lore(), inserted.getItemMeta().lore());
    assertEquals("CARBONADO", inserted.getItemMeta().getPersistentDataContainer()
        .get(id, PersistentDataType.STRING));
    assertEquals("original-owner", inserted.getItemMeta().getPersistentDataContainer()
        .get(owner, PersistentDataType.STRING));
    assertEquals(123456789L, inserted.getItemMeta().getPersistentDataContainer()
        .get(count, PersistentDataType.LONG));
  }
  @Test
  void pushAllKeepsDistinctRegisteredOutputComponentsAcrossMultipleProductionStacks() {
    MockBukkit.mock();
    ItemStack template = new ItemStack(Material.DIAMOND, 1);
    ItemMeta meta = template.getItemMeta();
    Component name = Component.text("").append(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
    Component lore = Component.text("").append(Component.text("Machine-grade carbon", NamedTextColor.GRAY));
    meta.displayName(name);
    meta.lore(java.util.List.of(lore));
    NamespacedKey identity = new NamespacedKey("slimefun", "slimefun_item");
    meta.getPersistentDataContainer().set(identity, PersistentDataType.STRING, "CARBONADO");
    template.setItemMeta(meta);

    ItemStack first = template.clone();
    first.setAmount(32);
    ItemStack second = template.clone();
    second.setAmount(7);
    BlockMenu menu = mock(BlockMenu.class);
    when(menu.pushItem(any(ItemStack.class), any(int[].class))).thenReturn(null);

    SupremeInventoryUtils.pushAll(menu, new int[]{19, 20}, first, second);

    var captor = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
    verify(menu, times(2)).pushItem(captor.capture(), any(int[].class));
    var inserted = captor.getAllValues();
    assertEquals(32, inserted.get(0).getAmount());
    assertEquals(7, inserted.get(1).getAmount());
    for (ItemStack stack : inserted) {
      assertTrue(template.isSimilar(stack), "production output must retain the registered identity");
      assertEquals(name, stack.getItemMeta().displayName());
      assertEquals(java.util.List.of(lore), stack.getItemMeta().lore());
      assertEquals("CARBONADO", stack.getItemMeta().getPersistentDataContainer()
          .get(identity, PersistentDataType.STRING));
    }
    assertEquals(1, template.getAmount(), "the registered template must remain unchanged");
    assertEquals(32, first.getAmount());
    assertEquals(7, second.getAmount());
  }

}
