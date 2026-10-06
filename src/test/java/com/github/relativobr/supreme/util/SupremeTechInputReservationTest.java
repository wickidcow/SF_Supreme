package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.relativobr.supreme.Supreme;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

/** Real reservation/codec paths with recording inventory, storage and opaque serializer boundaries. */
@SuppressWarnings("deprecation")
class SupremeTechInputReservationTest {

  private final Map<Integer, ItemStack> inventory = new LinkedHashMap<>();
  private final Map<String, String> data = new LinkedHashMap<>();
  private final Map<Integer, ItemStack[]> encoded = new LinkedHashMap<>();
  private final List<String> events = new ArrayList<>();
  private MockedStatic<ItemStack> items;
  private MockedStatic<SlimefunUtils> matching;
  private MockedStatic<BlockStorage> storage;
  private MockedStatic<Supreme> supreme;
  private Block block;
  private BlockMenu menu;
  private ItemStack[] templates;
  private ItemStack output;
  private int serializations;
  private int failAt;
  private Runnable afterEncoding = () -> {};

  @BeforeEach
  void setUp() {
    block = MockBukkit.mock().addSimpleWorld("reservation-test").getBlockAt(4, 64, 7);
    inventory.put(10, supplied(64, 1));
    inventory.put(15, supplied(8, 2));
    templates = new ItemStack[]{new ItemStack(Material.STONE), new ItemStack(Material.STONE)};
    output = new ItemStack(Material.DIAMOND, 2);
    menu = mock(BlockMenu.class);
    when(menu.getItemInSlot(anyInt())).thenAnswer(call -> inventory.get(call.getArgument(0)));
    doAnswer(call -> {
      int slot = call.getArgument(0), amount = call.getArgument(1);
      events.add("consume:" + slot + ":" + amount);
      ItemStack existing = inventory.get(slot);
      if (existing.getAmount() == amount) inventory.remove(slot);
      else existing.setAmount(existing.getAmount() - amount);
      return null;
    }).when(menu).consumeItem(anyInt(), anyInt());
    Supreme plugin = mock(Supreme.class);
    supreme = mockStatic(Supreme.class);
    supreme.when(Supreme::inst).thenReturn(plugin);
    matching = mockStatic(SlimefunUtils.class);
    matching.when(() -> SlimefunUtils.isItemSimilar(any(), any(), eq(false), anyBoolean()))
        .thenAnswer(call -> {
          ItemStack actual = call.getArgument(0), expected = call.getArgument(1);
          boolean checkAmount = call.getArgument(3);
          return actual != null && expected != null && actual.getType() == expected.getType()
              && (!checkAmount || actual.getAmount() >= expected.getAmount());
        });
    storage = mockStatic(BlockStorage.class);
    storage.when(() -> BlockStorage.addBlockInfo(eq(block), anyString(), nullable(String.class)))
        .thenAnswer(call -> {
          events.add("write");
          String key = call.getArgument(1), value = call.getArgument(2);
          if (value == null) data.remove(key); else data.put(key, value);
          return null;
        });
    storage.when(() -> BlockStorage.getLocationInfo(eq(block.getLocation()), anyString()))
        .thenAnswer(call -> data.get(call.getArgument(1)));
    items = mockStatic(ItemStack.class);
    items.when(() -> ItemStack.serializeItemsAsBytes(any(ItemStack[].class))).thenAnswer(call -> {
      events.add("serialize");
      if (++serializations == failAt) throw new IllegalStateException("injected serialization failure");
      ItemStack[] payload = call.getArgument(0);
      encoded.put(serializations, Arrays.stream(payload).map(ItemStack::clone).toArray(ItemStack[]::new));
      if (serializations == 2) afterEncoding.run();
      return new byte[]{(byte) serializations};
    });
    items.when(() -> ItemStack.deserializeItemsFromBytes(any(byte[].class))).thenAnswer(call -> {
      byte[] bytes = call.getArgument(0);
      return Arrays.stream(encoded.get((int) bytes[0])).map(ItemStack::clone).toArray(ItemStack[]::new);
    });
  }

  @AfterEach
  void tearDown() {
    if (items != null) items.close();
    if (storage != null) storage.close();
    if (matching != null) matching.close();
    if (supreme != null) supreme.close();
    MockBukkit.unmock();
  }

  @ParameterizedTest
  @ValueSource(ints = {64, 32, 16, 1})
  void suppliedMetadataAndExactQuantitiesSurviveReservationAndCheckpointRestore(int amount) {
    boolean mutation = amount == 1;
    ItemStack first = inventory.get(10).clone(), second = inventory.get(15).clone();
    ItemStack[] requiredBefore = Arrays.stream(templates).map(ItemStack::clone).toArray(ItemStack[]::new);
    var reservation = reserve(amount).orElseThrow();
    assertEquals(2, serializations);
    assertEquals(List.of("serialize", "serialize"), events.subList(0, 2));
    assertFalse(events.contains("write"));
    first.setAmount(amount);
    assertEquals(first, reservation.items()[0]);
    assertNotEquals(templates[0].getItemMeta(), reservation.items()[0].getItemMeta());
    assertEquals(64 - amount, inventory.containsKey(10) ? inventory.get(10).getAmount() : 0);
    if (mutation) {
      second.setAmount(1);
      assertEquals(second, reservation.items()[1]);
      assertEquals(7, inventory.get(15).getAmount());
      assertEquals(List.of("consume:10:1", "consume:15:1"), events.subList(2, 4));
    } else {
      assertEquals(second, inventory.get(15));
      assertEquals(List.of("consume:10:" + amount), events.subList(2, 3));
    }
    assertArrayEquals(requiredBefore, templates);
    SupremeSpecialMachineStateCodec.savePrepared(block, reservation.checkpoint());
    var state = SupremeSpecialMachineStateCodec.load(block, type(amount)).orElseThrow();
    assertArrayEquals(reservation.items(), state.reservedItems());
    assertArrayEquals(new ItemStack[]{output}, state.outputs());
    assertEquals(31, state.progress());
    assertEquals(31, state.ticks());
    assertEquals(mutation ? 75 : -1, state.auxInt());
    assertEquals("", state.auxText());
    assertEquals(2, serializations, "Commit must not serialize consumed inputs again");
    matching.verify(() -> SlimefunUtils.isItemSimilar(any(), same(templates[0]), eq(false), eq(!mutation)));
  }

  @ParameterizedTest
  @CsvSource({"64,1", "64,2", "32,1", "32,2", "16,1", "16,2", "1,1", "1,2"})
  void failureAtEitherSerializerLeavesAllInputsAndThePreviousRecordUntouched(int amount, int stage) {
    Map<Integer, ItemStack> before = snapshot();
    data.put("old_checkpoint", "retain-exactly");
    failAt = stage;
    assertTrue(reserve(amount).isEmpty());
    assertEquals(before, inventory);
    assertEquals(Map.of("old_checkpoint", "retain-exactly"), data);
    assertEquals(stage, serializations);
    assertTrue(events.stream().allMatch("serialize"::equals));
    verify(menu, never()).consumeItem(anyInt(), anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "quantity", "identity", "metadata"})
  void aChangedSecondInputAbortsTheWholeReservationBeforeEitherInputIsConsumed(String change) {
    Map<Integer, ItemStack> before = snapshot();
    afterEncoding = () -> {
      switch (change) {
        case "missing" -> inventory.remove(15);
        case "quantity" -> inventory.get(15).setAmount(7);
        case "identity" -> inventory.put(15, output.clone());
        case "metadata" -> inventory.get(15).editMeta(meta -> meta.displayName(Component.text("changed")));
        default -> fail(change);
      }
    };
    assertTrue(reserve(1).isEmpty());
    assertEquals(before.get(10), inventory.get(10));
    assertEquals(2, serializations);
    assertTrue(events.stream().allMatch("serialize"::equals));
    assertTrue(data.isEmpty());
    verify(menu, never()).consumeItem(anyInt(), anyInt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-first", "missing-second", "wrong-second", "insufficient-first"})
  void invalidInputsAreRejectedWithoutEncodingOrPartiallyConsumingARecipe(String damage) {
    int amount = 1;
    switch (damage) {
      case "missing-first" -> inventory.remove(10);
      case "missing-second" -> inventory.remove(15);
      case "wrong-second" -> inventory.put(15, output.clone());
      case "insufficient-first" -> { inventory.get(10).setAmount(15); amount = 16; }
      default -> fail(damage);
    }
    Map<Integer, ItemStack> before = snapshot();
    assertTrue(reserve(amount).isEmpty());
    assertEquals(before, inventory);
    assertTrue(events.isEmpty());
    assertTrue(data.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {64, 32, 16, 1})
  void preparedCheckpointIsDetachedFromLaterRuntimeItemChanges(int amount) {
    var reservation = reserve(amount).orElseThrow();
    ItemStack before = reservation.items()[0].clone();
    reservation.items()[0].editMeta(meta -> meta.displayName(Component.text("runtime change")));
    output.setAmount(1);
    failAt = 3;
    SupremeSpecialMachineStateCodec.savePrepared(block, reservation.checkpoint());
    var state = SupremeSpecialMachineStateCodec.load(block, type(amount)).orElseThrow();
    assertEquals(before, state.reservedItems()[0]);
    assertEquals(2, state.outputs()[0].getAmount());
    assertEquals(2, serializations);
  }

  private java.util.Optional<SupremeTechInputReservation.Reservation> reserve(int amount) {
    boolean mutation = amount == 1;
    return SupremeTechInputReservation.tryReserve(block, menu,
        mutation ? new int[]{10, 15} : new int[]{10},
        mutation ? new int[]{1, 1} : new int[]{amount},
        mutation ? templates : new ItemStack[]{templates[0]}, !mutation, type(amount), output,
        31, mutation ? 75 : -1);
  }

  private static String type(int amount) {
    return amount == 1 ? "TECH_MUTATION" : "TECH_ROBOTIC";
  }

  private Map<Integer, ItemStack> snapshot() {
    Map<Integer, ItemStack> result = new LinkedHashMap<>();
    inventory.forEach((slot, item) -> result.put(slot, item.clone()));
    return result;
  }

  private static ItemStack supplied(int amount, int identity) {
    ItemStack item = new ItemStack(Material.STONE, amount);
    item.editMeta(meta -> {
      meta.displayName(Component.text("Player input " + identity));
      meta.lore(List.of(Component.text("Original lore").hoverEvent(HoverEvent.showText(Component.text("keep")))));
      meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "counter"),
          PersistentDataType.LONG, 9_000_000_000L + identity);
      meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "identity"),
          PersistentDataType.INTEGER, identity);
      meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "opaque"),
          PersistentDataType.BYTE_ARRAY, new byte[]{3, 1, 4, (byte) identity});
    });
    return item;
  }
}
