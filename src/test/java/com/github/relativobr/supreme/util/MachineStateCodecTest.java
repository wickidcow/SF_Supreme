package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.relativobr.supreme.Supreme;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

/** Tests the storage boundary; opaque serialization bytes stand in for Paper's NBT serializer. */
@SuppressWarnings("deprecation")
class MachineStateCodecTest {

  private static final String GENERIC = "supreme_machine_";
  private static final String SPECIAL = "supreme_special_";
  private static final byte[] INPUT_BYTES = {1, 2, 3};
  private static final byte[] OUTPUT_BYTES = {4, 5, 6};
  private static final byte[] RESERVED_BYTES = {7, 8, 9};
  private final Map<String, String> data = new LinkedHashMap<>();
  private MockedStatic<BlockStorage> storage;
  private MockedStatic<ItemStack> items;
  private MockedStatic<Supreme> supreme;
  private Supreme plugin;
  private Block block;
  private ItemStack[] inputs;
  private ItemStack[] outputs;
  private ItemStack[] reserved;
  private ItemStack reservedTemplate;
  private int writes;
  private int serializationCalls;
  private int failAt;

  @BeforeEach
  void setUp() {
    block = MockBukkit.mock().addSimpleWorld("checkpoint-test").getBlockAt(2, 64, 3);
    inputs = new ItemStack[]{new ItemStack(Material.STONE, 32)};
    outputs = new ItemStack[]{new ItemStack(Material.DIAMOND, 2)};
    reserved = new ItemStack[]{mock(ItemStack.class)};
    reservedTemplate = mock(ItemStack.class);
    when(reserved[0].getType()).thenReturn(Material.STONE);
    when(reserved[0].clone()).thenReturn(reservedTemplate);
    when(reservedTemplate.serializeAsBytes()).thenAnswer(call -> serialize(RESERVED_BYTES));

    plugin = mock(Supreme.class);
    supreme = mockStatic(Supreme.class);
    supreme.when(Supreme::inst).thenReturn(plugin);
    storage = mockStatic(BlockStorage.class);
    storage.when(() -> BlockStorage.addBlockInfo(eq(block), anyString(), nullable(String.class)))
        .thenAnswer(call -> {
          writes++;
          String key = call.getArgument(1);
          String value = call.getArgument(2);
          if (value == null) {
            data.remove(key);
          } else {
            data.put(key, value);
          }
          return null;
        });
    storage.when(() -> BlockStorage.getLocationInfo(eq(block.getLocation()), anyString()))
        .thenAnswer(call -> data.get(call.getArgument(1)));

    items = mockStatic(ItemStack.class);
    items.when(() -> ItemStack.serializeItemsAsBytes(inputs))
        .thenAnswer(call -> serialize(INPUT_BYTES));
    items.when(() -> ItemStack.serializeItemsAsBytes(outputs))
        .thenAnswer(call -> serialize(OUTPUT_BYTES));
    items.when(() -> ItemStack.serializeItemsAsBytes(reserved))
        .thenAnswer(call -> serialize(RESERVED_BYTES));
    items.when(() -> ItemStack.deserializeItemsFromBytes(INPUT_BYTES)).thenReturn(inputs);
    items.when(() -> ItemStack.deserializeItemsFromBytes(OUTPUT_BYTES)).thenReturn(outputs);
    items.when(() -> ItemStack.deserializeItemsFromBytes(RESERVED_BYTES)).thenReturn(reserved);
    items.when(() -> ItemStack.deserializeBytes(RESERVED_BYTES)).thenReturn(reservedTemplate);
  }

  @AfterEach
  void tearDown() {
    if (items != null) items.close();
    if (storage != null) storage.close();
    if (supreme != null) supreme.close();
    MockBukkit.unmock();
  }

  @ParameterizedTest
  @CsvSource({"1,false", "2,false", "3,false", "1,true", "2,true", "3,true"})
  void genericSerializationFailureLeavesTheWholePreviousRecordUntouched(int stage,
      boolean existing) {
    seedPreviousRecord(GENERIC, existing);
    Map<String, String> before = new LinkedHashMap<>(data);
    failAt = stage;

    SupremeMachineStateCodec.save(block, recipe(31), 17, 4, Map.of(reserved[0], 901));

    assertEquals(stage, serializationCalls);
    assertEquals(before, data);
    assertEquals(0, writes, "No field may be written before serialization succeeds");
    verify(plugin).log(eq(Level.WARNING), contains("injected serialization failure"));
  }

  @ParameterizedTest
  @CsvSource({"1,false", "2,false", "3,false", "1,true", "2,true", "3,true"})
  void specializedSerializationFailureLeavesTheWholePreviousRecordUntouched(int stage,
      boolean existing) {
    seedPreviousRecord(SPECIAL, existing);
    Map<String, String> before = new LinkedHashMap<>(data);
    failAt = stage;

    SupremeSpecialMachineStateCodec.save(block, "robotic", 17, 31, inputs, outputs,
        reserved, 3, "stored-result");

    assertEquals(stage, serializationCalls);
    assertEquals(before, data);
    assertEquals(0, writes, "No field may be written before serialization succeeds");
    verify(plugin).log(eq(Level.WARNING), contains("injected serialization failure"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 31, 600, Integer.MAX_VALUE})
  void genericRoundTripPreservesStoredTicksBytesAndLargeReservedQuantities(int ticks) {
    SupremeMachineStateCodec.save(block, recipe(ticks), 17, 4, Map.of(reserved[0], 901));

    assertEquals(Map.of(
        GENERIC + "state_version", "1",
        GENERIC + "recipe_input", encoded(INPUT_BYTES),
        GENERIC + "recipe_output", encoded(OUTPUT_BYTES),
        GENERIC + "recipe_ticks", Integer.toString(ticks),
        GENERIC + "progress", "17",
        GENERIC + "attempts", "4",
        GENERIC + "consumed", "901," + encoded(RESERVED_BYTES)), data);
    assertEquals(7, writes);
    verify(reservedTemplate).setAmount(1);
    verify(reserved[0], never()).setAmount(anyInt());

    SupremeMachineStateCodec.State state = SupremeMachineStateCodec.load(block).orElseThrow();
    assertEquals(ticks, state.recipe().getTicks());
    assertArrayEquals(inputs, state.recipe().getInput());
    assertArrayEquals(outputs, state.recipe().getOutput());
    assertEquals(17, state.progress());
    assertEquals(4, state.attempts());
    assertEquals(Map.of(reservedTemplate, 901), state.consumedItems());
    verifyNoInteractions(plugin);
  }

  @Test
  void specializedRoundTripRetainsTheExistingFieldFormatAndResult() {
    SupremeSpecialMachineStateCodec.save(block, "mutation", 17, 31, inputs, outputs,
        reserved, -1, "persisted-success");

    assertEquals(Map.of(
        SPECIAL + "state_version", "1",
        SPECIAL + "state_type", "mutation",
        SPECIAL + "progress", "17",
        SPECIAL + "ticks", "31",
        SPECIAL + "inputs", encoded(INPUT_BYTES),
        SPECIAL + "outputs", encoded(OUTPUT_BYTES),
        SPECIAL + "reserved", encoded(RESERVED_BYTES),
        SPECIAL + "aux_int", "-1",
        SPECIAL + "aux_text", "persisted-success"), data);
    assertEquals(9, writes);

    var state = SupremeSpecialMachineStateCodec.load(block, "mutation").orElseThrow();
    assertEquals("mutation", state.type());
    assertEquals(17, state.progress());
    assertEquals(31, state.ticks());
    assertArrayEquals(inputs, state.inputs());
    assertArrayEquals(outputs, state.outputs());
    assertArrayEquals(reserved, state.reservedItems());
    assertEquals(-1, state.auxInt());
    assertEquals("persisted-success", state.auxText());
    verifyNoInteractions(plugin);
  }

  @Test
  void optionalSpecializedFieldsAndNonNegativeCheckpointsKeepTheirEncoding() {
    SupremeSpecialMachineStateCodec.save(block, "garden", -1, -3, null,
        new ItemStack[0], null, -1, null);
    assertEquals(0, serializationCalls);
    var state = SupremeSpecialMachineStateCodec.load(block, "garden").orElseThrow();
    assertEquals(0, state.progress());
    assertEquals(0, state.ticks());
    assertEquals(0, state.inputs().length);
    assertEquals(0, state.outputs().length);
    assertEquals(0, state.reservedItems().length);
    assertEquals(-1, state.auxInt());
    assertEquals("", state.auxText());
    verifyNoInteractions(plugin);
  }

  @Test
  void genericEmptyReservationsAndNonNegativeCheckpointsKeepTheirEncoding() {
    SupremeMachineStateCodec.save(block, recipe(31), -1, -3, null);
    assertEquals(2, serializationCalls);
    assertEquals("", data.get(GENERIC + "consumed"));
    var state = SupremeMachineStateCodec.load(block).orElseThrow();
    assertEquals(0, state.progress());
    assertEquals(0, state.attempts());
    assertTrue(state.consumedItems().isEmpty());
    verifyNoInteractions(plugin);
  }

  private byte[] serialize(byte[] bytes) {
    if (++serializationCalls == failAt) {
      throw new IllegalStateException("injected serialization failure");
    }
    return bytes;
  }

  private MachineRecipe recipe(int ticks) {
    MachineRecipe recipe = new MachineRecipe(0, inputs, outputs);
    recipe.setTicks(ticks);
    return recipe;
  }

  private void seedPreviousRecord(String prefix, boolean existing) {
    data.put("id", "SUPREME_OLD_MACHINE");
    data.put("third_party_field", "keep-exactly");
    if (!existing) return;
    data.put(prefix + "state_version", "1");
    String[] fields = prefix.equals(GENERIC)
        ? new String[]{"recipe_input", "recipe_output", "recipe_ticks", "progress", "attempts", "consumed"}
        : new String[]{"state_type", "progress", "ticks", "inputs", "outputs", "reserved", "aux_int", "aux_text"};
    for (String field : fields) data.put(prefix + field, "previous-" + field);
  }

  private static String encoded(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
