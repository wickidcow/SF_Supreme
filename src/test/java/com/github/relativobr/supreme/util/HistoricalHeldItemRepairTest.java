package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Only one intentional main-hand write; all other stacks and stores are immutable. */
class HistoricalHeldItemRepairTest {
  private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
  private static final NamespacedKey OWNER = new NamespacedKey("supreme", "test-typed-owner");
  private static final NamespacedKey COUNTER = new NamespacedKey("supreme", "test-typed-counter");

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CARBONADO", "SYNTHETIC_SAPPHIRE", "SYNTHETIC_EMERALD", "SMALL_URANIUM"})
  void singleConfirmedWritePreservesStackAndTypedMetadata(String id) {
    MockBukkit.mock();
    ItemStack old = legacy(id, 37);
    ItemStack fresh = fresh(old, id);
    ItemStack registered = fresh.clone();
    registered.setAmount(1);
    ItemStack oldSnapshot = old.clone(), offSnapshot = fresh.clone(), registeredSnapshot = registered.clone();

    AtomicReference<ItemStack> held = new AtomicReference<>(old);
    AtomicReference<ItemStack> off = new AtomicReference<>(fresh);
    PlayerInventory inventory = recordingInventory(held, off);

    var result = HistoricalHeldItemRepair.applyOne(inventory, registered);
    assertTrue(result.repaired(), result.reason());
    ItemStack converted = held.get();
    assertEquals(37, converted.getAmount());
    assertTrue(converted.isSimilar(registered));
    assertEquals(id, converted.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    assertEquals("no-change", converted.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
    assertEquals(90817L, converted.getItemMeta().getPersistentDataContainer().get(COUNTER, PersistentDataType.LONG));
    assertEquals(2200089, converted.getItemMeta().getCustomModelData());
    assertEquals(oldSnapshot, old, "original item object must not be modified in-place");
    assertEquals(offSnapshot, off.get());
    assertEquals(registeredSnapshot, registered);
    verify(inventory, times(1)).setItemInMainHand(any(ItemStack.class));
    verify(inventory, never()).setItemInOffHand(any());
  }

  @Test
  void rejectsChangedTypedPdcWithoutAnySlotWrite() {
    MockBukkit.mock();
    ItemStack old = legacy("CARBONADO", 21);
    ItemStack fresh = fresh(old, "CARBONADO");
    ItemMeta altered = old.getItemMeta();
    altered.getPersistentDataContainer().remove(COUNTER);
    altered.getPersistentDataContainer().set(COUNTER, PersistentDataType.STRING, "90817");
    old.setItemMeta(altered);
    ItemStack before = old.clone();
    AtomicReference<ItemStack> held = new AtomicReference<>(old);
    PlayerInventory inventory = recordingInventory(held, new AtomicReference<>(fresh));
    var result = HistoricalHeldItemRepair.applyOne(inventory, fresh);

    assertFalse(result.repaired());
    assertEquals(before, held.get());
    verify(inventory, never()).setItemInMainHand(any(ItemStack.class));
  }

  @Test
  void postWriteAmountCorruptionTriggersExactRollback() {
    MockBukkit.mock();
    ItemStack old = legacy("SYNTHETIC_EMERALD", 19);
    ItemStack fresh = fresh(old, "SYNTHETIC_EMERALD");
    ItemStack before = old.clone();
    AtomicReference<ItemStack> held = new AtomicReference<>(old);
    AtomicReference<ItemStack> off = new AtomicReference<>(fresh);
    PlayerInventory inv = mock(PlayerInventory.class);
    AtomicInteger writes = new AtomicInteger();
    when(inv.getItemInMainHand()).thenAnswer(call -> held.get());
    when(inv.getItemInOffHand()).thenAnswer(call -> off.get());
    doAnswer(call -> {
      ItemStack supplied = ((ItemStack) call.getArgument(0)).clone();
      if (writes.getAndIncrement() == 0) supplied.setAmount(1);
      held.set(supplied);
      return null;
    }).when(inv).setItemInMainHand(any(ItemStack.class));

    var result = HistoricalHeldItemRepair.applyOne(inv, fresh);
    assertFalse(result.repaired());
    assertTrue(result.reason().contains("restored"), result.reason());
    assertEquals(2, writes.get(), "one attempted write and one verified rollback");
    assertEquals(before, held.get());
    assertEquals(fresh, off.get());
  }

  @Test
  void thrownInventoryWriteTriggersVerifiedRollbackWithoutTouchingOffhand() {
    MockBukkit.mock();
    ItemStack old = legacy("CARBONADO", 23);
    ItemStack fresh = fresh(old, "CARBONADO");
    ItemStack original = old.clone();
    AtomicReference<ItemStack> main = new AtomicReference<>(old);
    AtomicReference<ItemStack> off = new AtomicReference<>(fresh);
    PlayerInventory inventory = mock(PlayerInventory.class);
    AtomicInteger writes = new AtomicInteger();
    when(inventory.getItemInMainHand()).thenAnswer(call -> main.get());
    when(inventory.getItemInOffHand()).thenAnswer(call -> off.get());
    doAnswer(call -> {
      if (writes.getAndIncrement() == 0) {
        main.set(((ItemStack) call.getArgument(0)).clone());
        throw new IllegalStateException("simulated inventory write failure");
      }
      main.set(((ItemStack) call.getArgument(0)).clone());
      return null;
    }).when(inventory).setItemInMainHand(any(ItemStack.class));

    var result = HistoricalHeldItemRepair.applyOne(inventory, fresh);
    assertFalse(result.repaired());
    assertTrue(result.reason().contains("restored"), result.reason());
    assertEquals(2, writes.get(), "failed write must be followed by one rollback");
    assertEquals(original, main.get(), "rollback must restore the exact original stack");
    assertEquals(fresh, off.get(), "offhand reference must remain untouched");
    verify(inventory, never()).setItemInOffHand(any());
  }

  @Test
  void failedRollbackReportsCriticalWithoutClaimingRecovery() {
    MockBukkit.mock();
    ItemStack old = legacy("CARBONADO", 29);
    ItemStack fresh = fresh(old, "CARBONADO");
    AtomicReference<ItemStack> main = new AtomicReference<>(old);
    AtomicReference<ItemStack> off = new AtomicReference<>(fresh);
    PlayerInventory inventory = mock(PlayerInventory.class);
    AtomicInteger writes = new AtomicInteger();
    when(inventory.getItemInMainHand()).thenAnswer(call -> main.get());
    when(inventory.getItemInOffHand()).thenAnswer(call -> off.get());
    doAnswer(call -> {
      writes.incrementAndGet();
      throw new IllegalStateException("simulated permanent inventory write failure");
    }).when(inventory).setItemInMainHand(any(ItemStack.class));

    var result = HistoricalHeldItemRepair.applyOne(inventory, fresh);
    assertFalse(result.repaired());
    assertTrue(result.reason().startsWith("CRITICAL:"), result.reason());
    assertTrue(result.reason().contains("restore from backup"), result.reason());
    assertEquals(2, writes.get(), "rollback must be attempted even after first write throws");
    assertEquals(old, main.get());
    assertEquals(fresh, off.get());
    verify(inventory, never()).setItemInOffHand(any());
  }

  @Test
  void changesBetweenSnapshotAndCommitAreRejectedWithoutWrite() {
    MockBukkit.mock();
    ItemStack old = legacy("SYNTHETIC_SAPPHIRE", 28);
    ItemStack fresh = fresh(old, "SYNTHETIC_SAPPHIRE");
    ItemStack changedOff = fresh.clone();
    changedOff.setAmount(22);
    PlayerInventory inv = mock(PlayerInventory.class);
    AtomicInteger reads = new AtomicInteger();
    when(inv.getItemInMainHand()).thenReturn(old);
    when(inv.getItemInOffHand()).thenAnswer(call -> reads.getAndIncrement() == 0 ? fresh : changedOff);

    var result = HistoricalHeldItemRepair.applyOne(inv, fresh);
    assertFalse(result.repaired());
    assertTrue(result.reason().contains("changed"), result.reason());
    verify(inv, never()).setItemInMainHand(any(ItemStack.class));
  }

  @Test
  void sourceAlreadyCanonicalIsNeverReplaced() {
    MockBukkit.mock();
    ItemStack fresh = fresh(legacy("CARBONADO", 22), "CARBONADO");
    AtomicReference<ItemStack> held = new AtomicReference<>(fresh);
    PlayerInventory inv = recordingInventory(held, new AtomicReference<>(fresh.clone()));
    var result = HistoricalHeldItemRepair.applyOne(inv, fresh);
    assertFalse(result.repaired());
    verify(inv, never()).setItemInMainHand(any(ItemStack.class));
  }

  private static PlayerInventory recordingInventory(
      AtomicReference<ItemStack> held, AtomicReference<ItemStack> off) {
    PlayerInventory inv = mock(PlayerInventory.class);
    when(inv.getItemInMainHand()).thenAnswer(call -> held.get());
    when(inv.getItemInOffHand()).thenAnswer(call -> off.get());
    doAnswer(call -> {
      held.set(((ItemStack) call.getArgument(0)).clone());
      return null;
    }).when(inv).setItemInMainHand(any(ItemStack.class));
    return inv;
  }

  private static ItemStack legacy(String id, int amount) {
    Material type = "SYNTHETIC_EMERALD".equals(id) ? Material.EMERALD : Material.PLAYER_HEAD;
    String label = switch (id) {
      case "CARBONADO" -> "Carbonado";
      case "SYNTHETIC_SAPPHIRE" -> "Synthetic Sapphire";
      case "SYNTHETIC_EMERALD" -> "Synthetic Emerald";
      default -> "Small Chunk of Uranium";
    };
    ItemStack item = new ItemStack(type, amount);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("").append(
        Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
    meta.lore(List.of(
        Component.text("").append(Component.text("Unchanged static lore", NamedTextColor.GRAY))));
    meta.setCustomModelData(2200089);
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
    meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "no-change");
    meta.getPersistentDataContainer().set(COUNTER, PersistentDataType.LONG, 90817L);
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack fresh(ItemStack old, String id) {
    String label = switch (id) {
      case "CARBONADO" -> "Carbonado";
      case "SYNTHETIC_SAPPHIRE" -> "Synthetic Sapphire";
      case "SYNTHETIC_EMERALD" -> "Synthetic Emerald";
      default -> "Small Chunk of Uranium";
    };
    ItemStack item = old.clone();
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text(label, NamedTextColor.AQUA));
    meta.lore(List.of(Component.text("Unchanged static lore", NamedTextColor.GRAY)));
    item.setItemMeta(meta);
    return item;
  }
}
