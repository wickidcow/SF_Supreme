package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Regression for the AlbionMC old Fluffy barrel CARBONADO versus new /sf cheat
 * CARBONADO screenshots (2026-11-10). The formatting, Slimefun ID and model
 * number are reproduced here, NOT the full owner skull texture/profile data.
 * This is a synthetic component-level regression, not a native saved-world test.
 */
class AlbionMcCarbonadoHistoricalRegressionTest {
  private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
  private static final NamespacedKey TYPED_VALUE = new NamespacedKey("supreme", "regression-typed-marker");

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  @Test
  void historicalBarrelFormattingCanBePreparedAndConfirmedWithoutChangingTheOffhand() {
    MockBukkit.mock();
    ItemStack barrelOld = historicalCarbonado(16);
    ItemStack cheatFresh = freshCarbonado(barrelOld);
    ItemStack registered = cheatFresh.clone();
    registered.setAmount(1);
    ItemStack oldSnapshot = barrelOld.clone();
    ItemStack freshSnapshot = cheatFresh.clone();
    ItemStack registeredSnapshot = registered.clone();

    assertFalse(barrelOld.isSimilar(cheatFresh), "historical and new metadata must differ");
    assertTrue(cheatFresh.isSimilar(registered), "fresh cheat/reference must match registry");
    var preview = HistoricalItemPresentationRepair.prepare(barrelOld, cheatFresh, registered);
    assertTrue(preview.ready(), preview.reason());
    assertEquals("READY: exact registered presentation on a detached clone.", preview.reason());
    assertEquals(16, preview.replacement().getAmount());
    assertTrue(preview.replacement().isSimilar(registered));
    assertEquals(oldSnapshot, barrelOld, "preview must not touch the old stack");
    assertEquals(freshSnapshot, cheatFresh, "preview must not touch the new stack");
    assertEquals(registeredSnapshot, registered, "preview must not touch the registered template");

    AtomicReference<ItemStack> main = new AtomicReference<>(barrelOld);
    AtomicReference<ItemStack> off = new AtomicReference<>(cheatFresh);
    PlayerInventory inventory = inventory(main, off);
    var confirmation = HistoricalHeldItemRepair.applyOne(inventory, registered);
    assertTrue(confirmation.repaired(), confirmation.reason());
    assertEquals(16, main.get().getAmount());
    assertTrue(main.get().isSimilar(cheatFresh), "repaired historical CARBONADO should now stack");
    assertEquals(2200094, main.get().getItemMeta().getCustomModelData());
    assertEquals("CARBONADO", main.get().getItemMeta().getPersistentDataContainer()
        .get(ID, PersistentDataType.STRING));
    assertEquals(3377L, main.get().getItemMeta().getPersistentDataContainer()
        .get(TYPED_VALUE, PersistentDataType.LONG));
    assertEquals(oldSnapshot, barrelOld, "historical source must remain detached");
    assertEquals(freshSnapshot, off.get(), "new offhand reference must not be rewritten");
    verify(inventory, times(1)).setItemInMainHand(any(ItemStack.class));
    verify(inventory, never()).setItemInOffHand(any());
  }

  @Test
  void reversedHandsAreRejectedWithoutWritingAnyItems() {
    MockBukkit.mock();
    ItemStack barrelOld = historicalCarbonado(16);
    ItemStack cheatFresh = freshCarbonado(barrelOld);
    ItemStack registered = cheatFresh.clone();
    registered.setAmount(1);
    AtomicReference<ItemStack> main = new AtomicReference<>(cheatFresh);
    AtomicReference<ItemStack> off = new AtomicReference<>(barrelOld);
    PlayerInventory inventory = inventory(main, off);
    ItemStack oldSnapshot = barrelOld.clone();
    ItemStack freshSnapshot = cheatFresh.clone();

    var preview = HistoricalItemPresentationRepair.prepare(cheatFresh, barrelOld, registered);
    assertFalse(preview.ready());
    assertEquals("Fresh reference does not match current registered item: no repair. Check OLD in MAIN and fresh /sf cheat in OFF.",
        preview.reason());
    var confirmation = HistoricalHeldItemRepair.applyOne(inventory, registered);
    assertFalse(confirmation.repaired());
    assertEquals(freshSnapshot, main.get());
    assertEquals(oldSnapshot, off.get());
    verify(inventory, never()).setItemInMainHand(any(ItemStack.class));
    verify(inventory, never()).setItemInOffHand(any());
  }

  private static PlayerInventory inventory(
      AtomicReference<ItemStack> main, AtomicReference<ItemStack> off) {
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(inventory.getItemInMainHand()).thenAnswer(call -> main.get());
    when(inventory.getItemInOffHand()).thenAnswer(call -> off.get());
    doAnswer(call -> {
      main.set(((ItemStack) call.getArgument(0)).clone());
      return null;
    }).when(inventory).setItemInMainHand(any(ItemStack.class));
    return inventory;
  }

  private static ItemStack historicalCarbonado(int amount) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD, amount);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("").append(
        Component.text("Carbonado", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.OBFUSCATED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)));
    meta.lore(List.of(Component.text("").append(
        Component.text("Black Diamond", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, true)
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)
            .decoration(TextDecoration.OBFUSCATED, false))));
    meta.setCustomModelData(2200094);
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "CARBONADO");
    meta.getPersistentDataContainer().set(TYPED_VALUE, PersistentDataType.LONG, 3377L);
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack freshCarbonado(ItemStack old) {
    ItemStack fresh = old.clone();
    ItemMeta meta = fresh.getItemMeta();
    meta.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, true));
    meta.lore(List.of(Component.text("Black Diamond", NamedTextColor.GRAY)
        .decoration(TextDecoration.ITALIC, false)));
    fresh.setItemMeta(meta);
    return fresh;
  }
}
