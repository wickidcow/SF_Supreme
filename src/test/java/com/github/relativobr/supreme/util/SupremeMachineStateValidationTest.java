package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class SupremeMachineStateValidationTest {

  private static final int[] INPUT_SLOTS = {10, 11};

  @BeforeEach void setUp() { MockBukkit.mock(); }
  @AfterEach void tearDown() { MockBukkit.unmock(); }

  @ParameterizedTest
  @ValueSource(strings = {"VIRTUAL_GARDEN", "VIRTUAL_AQUARIUM", "MOB_COLLECTOR", "TECH_ROBOTIC", "TECH_MUTATION"})
  void validMachineShapesPreserveOriginalStacksAndTypedMetadata(String type) {
    var state = valid(type);
    ItemStack[] inputs = copy(state.inputs());
    ItemStack[] outputs = copy(state.outputs());
    ItemStack[] reserved = copy(state.reservedItems());
    assertTrue(SupremeMachineStateValidation.isUsable(state, INPUT_SLOTS));
    assertArrayEquals(inputs, state.inputs());
    assertArrayEquals(outputs, state.outputs());
    assertArrayEquals(reserved, state.reservedItems());
    assertEquals(19, state.progress());
    assertEquals(31, state.ticks());
  }

  @ParameterizedTest
  @ValueSource(strings = {"VIRTUAL_GARDEN", "VIRTUAL_AQUARIUM", "MOB_COLLECTOR", "TECH_ROBOTIC", "TECH_MUTATION"})
  void missingOutputIsBlockedForEverySpecializedEngine(String type) {
    var s = valid(type);
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        type, s.progress(), s.ticks(), s.inputs(), new ItemStack[0], s.reservedItems(),
        s.auxInt(), s.auxText()), INPUT_SLOTS));
  }

  @ParameterizedTest
  @ValueSource(strings = {"VIRTUAL_GARDEN", "VIRTUAL_AQUARIUM", "MOB_COLLECTOR"})
  void unexpectedReservedItemsCannotBeSilentlyIgnored(String type) {
    var s = valid(type);
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        type, s.progress(), s.ticks(), s.inputs(), s.outputs(), new ItemStack[]{item()},
        s.auxInt(), s.auxText()), INPUT_SLOTS));
  }

  @ParameterizedTest
  @CsvSource({"VIRTUAL_AQUARIUM,-1", "VIRTUAL_AQUARIUM,22", "VIRTUAL_AQUARIUM,999",
      "MOB_COLLECTOR,-1", "MOB_COLLECTOR,22", "MOB_COLLECTOR,999"})
  void deferredInputCommitMustReferToAnActualInputSlot(String type, int slot) {
    var s = valid(type);
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        type, s.progress(), s.ticks(), s.inputs(), s.outputs(), s.reservedItems(),
        slot, s.auxText()), INPUT_SLOTS));
  }

  @ParameterizedTest
  @CsvSource({"TECH_ROBOTIC,0", "TECH_ROBOTIC,2", "TECH_MUTATION,0", "TECH_MUTATION,1", "TECH_MUTATION,3"})
  void hiddenIngredientsMustMatchTheOwningEngine(String type, int count) {
    var s = valid(type);
    ItemStack[] reserved = new ItemStack[count];
    Arrays.setAll(reserved, i -> item());
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        type, s.progress(), s.ticks(), s.inputs(), s.outputs(), reserved,
        s.auxInt(), s.auxText()), INPUT_SLOTS));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "true", "false", "TRUE", "FALSE"})
  void existingMutationResultEncodingsRemainReadable(String result) {
    var s = valid("TECH_MUTATION");
    assertTrue(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        s.type(), s.progress(), s.ticks(), s.inputs(), s.outputs(), s.reservedItems(),
        s.auxInt(), result), INPUT_SLOTS));
  }

  @Test
  void damagedMutationResultIsNeverTreatedAsAnUnrolledResult() {
    var s = valid("TECH_MUTATION");
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        s.type(), s.progress(), s.ticks(), s.inputs(), s.outputs(), s.reservedItems(),
        s.auxInt(), "unreadable-result"), INPUT_SLOTS));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void invalidMutationChanceIsRetainedInsteadOfBeingClamped(int chance) {
    var s = valid("TECH_MUTATION");
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        s.type(), s.progress(), s.ticks(), s.inputs(), s.outputs(), s.reservedItems(),
        chance, s.auxText()), INPUT_SLOTS));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 31, 600, Integer.MAX_VALUE})
  void specializedRecipeRestorePreservesStoredTickUnits(int ticks) {
    var s = valid("VIRTUAL_GARDEN");
    var state = new SupremeSpecialMachineStateCodec.State(s.type(), 0, ticks, s.inputs(),
        s.outputs(), s.reservedItems(), s.auxInt(), s.auxText());
    assertEquals(ticks, state.recipe().getTicks());
    assertArrayEquals(s.inputs(), state.recipe().getInput());
    assertArrayEquals(s.outputs(), state.recipe().getOutput());
  }

  @ParameterizedTest
  @ValueSource(strings = {"VIRTUAL_GARDEN", "VIRTUAL_AQUARIUM", "MOB_COLLECTOR", "TECH_ROBOTIC", "TECH_MUTATION"})
  void progressBeyondTheSavedDurationIsBlocked(String type) {
    var s = valid(type);
    assertFalse(SupremeMachineStateValidation.isUsable(new SupremeSpecialMachineStateCodec.State(
        type, 32, 31, s.inputs(), s.outputs(), s.reservedItems(), s.auxInt(), s.auxText()), INPUT_SLOTS));
  }

  @Test
  void emptyAirAndNullItemsAreNotUsableOutputs() {
    assertFalse(SupremeMachineStateValidation.hasUsableItems(null));
    assertFalse(SupremeMachineStateValidation.hasUsableItems(new ItemStack[0]));
    assertFalse(SupremeMachineStateValidation.hasUsableItems(new ItemStack[]{null}));
    assertFalse(SupremeMachineStateValidation.hasUsableItems(new ItemStack[]{new ItemStack(Material.AIR)}));
    // Paper rejects zero-amount construction, so model a damaged decoded stack at the boundary.
    ItemStack empty = mock(ItemStack.class);
    when(empty.getType()).thenReturn(Material.STONE);
    when(empty.getAmount()).thenReturn(0);
    assertFalse(SupremeMachineStateValidation.hasUsableItems(new ItemStack[]{empty}));
  }

  private static SupremeSpecialMachineStateCodec.State valid(String type) {
    boolean tech = type.startsWith("TECH_");
    int count = type.equals("TECH_MUTATION") ? 2 : tech ? 1 : 0;
    ItemStack[] reserved = new ItemStack[count];
    Arrays.setAll(reserved, i -> item());
    return new SupremeSpecialMachineStateCodec.State(type, 19, 31,
        tech ? new ItemStack[0] : new ItemStack[]{item()}, new ItemStack[]{item()}, reserved,
        type.equals("TECH_MUTATION") ? 50 : 10, "");
  }

  private static ItemStack item() {
    ItemStack item = new ItemStack(Material.STONE, 32);
    var meta = item.getItemMeta();
    meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "counter"),
        PersistentDataType.LONG, 9_000_000_001L);
    item.setItemMeta(meta);
    return item;
  }

  private static ItemStack[] copy(ItemStack[] items) {
    return Arrays.stream(items).map(ItemStack::clone).toArray(ItemStack[]::new);
  }
}
