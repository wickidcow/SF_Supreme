package com.github.relativobr.supreme.generic.machine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.command.SupremeCommand;
import com.github.relativobr.supreme.util.SupremeMachineBreakHandler;
import com.github.relativobr.supreme.util.SupremeMachineRecoveryGuard;
import io.github.thebusybiscuit.slimefun4.api.events.AndroidMineEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

/** Exercises production recovery gates with a recording BlockStorage boundary, not a live world DB. */
@SuppressWarnings("deprecation")
class GenericMachineRecoveryTest {

  private static final String PREFIX = "supreme_machine_";
  private final Map<String, String> data = new LinkedHashMap<>();
  private MockedStatic<BlockStorage> storage;
  private MockedStatic<ItemStack> items;
  private MockedStatic<Supreme> supreme;
  private Supreme plugin;
  private Block block;
  private BlockMenu menu;
  private GenericMachine machine;
  private int writes;

  @BeforeEach
  void setUp() throws Exception {
    block = MockBukkit.mock().addSimpleWorld("recovery-test").getBlockAt(3, 64, 5);
    menu = mock(BlockMenu.class);
    when(menu.getBlock()).thenReturn(block);
    plugin = mock(Supreme.class);
    supreme = mockStatic(Supreme.class);
    supreme.when(Supreme::inst).thenReturn(plugin);
    storage = mockStatic(BlockStorage.class);
    storage.when(() -> BlockStorage.getInventory(block)).thenReturn(menu);
    storage.when(() -> BlockStorage.getLocationInfo(eq(block.getLocation()), anyString()))
        .thenAnswer(call -> data.get(call.getArgument(1)));
    storage.when(() -> BlockStorage.addBlockInfo(eq(block), anyString(), nullable(String.class)))
        .thenAnswer(call -> {
          writes++;
          String key = call.getArgument(1);
          String value = call.getArgument(2);
          if (value == null) data.remove(key); else data.put(key, value);
          return null;
        });
    ItemStack[] inputs = {new ItemStack(Material.STONE, 32)};
    ItemStack[] outputs = {new ItemStack(Material.DIAMOND, 2)};
    items = mockStatic(ItemStack.class);
    items.when(() -> ItemStack.deserializeItemsFromBytes(new byte[]{1})).thenReturn(inputs);
    items.when(() -> ItemStack.deserializeItemsFromBytes(new byte[]{2})).thenReturn(outputs);
    data.put("id", "SUPREME_OLD_MACHINE");
    data.put("other_addon_payload", "retain-exactly");
    data.put(PREFIX + "state_version", "1");
    data.put(PREFIX + "recipe_input", "AQ==");
    data.put(PREFIX + "recipe_output", "Ag==");
    data.put(PREFIX + "recipe_ticks", "31");
    data.put(PREFIX + "progress", "19");
    data.put(PREFIX + "attempts", "2");
    data.put(PREFIX + "consumed", "unreadable-reserved-item");
    machine = freshMachine();
  }

  @AfterEach
  void tearDown() {
    if (items != null) items.close();
    if (storage != null) storage.close();
    if (supreme != null) supreme.close();
    MockBukkit.unmock();
  }

  @Test
  void repeatedTicksRetainTheRecordWithoutTouchingInventoryOrEnergy() {
    Map<String, String> before = new LinkedHashMap<>(data);
    for (int tick = 0; tick < 100; tick++) machine.tick(block);
    assertEquals(before, data);
    assertEquals(0, writes);
    verifyNoInteractions(menu);
    verify(machine, never()).removeCharge(any(), anyInt());
    items.verify(() -> ItemStack.deserializeItemsFromBytes(new byte[]{1}), times(1));
    items.verify(() -> ItemStack.deserializeItemsFromBytes(new byte[]{2}), times(1));
  }

  @Test
  void transportUsesTheOccupiedStatusSlotWithoutRestoringOrConsumingItems() throws Exception {
    Method method = GenericMachine.class.getDeclaredMethod("getRecipeAwareInsertSlots",
        DirtyChestMenu.class, ItemStack.class);
    method.setAccessible(true);
    int[] slots = (int[]) method.invoke(machine, menu, new ItemStack(Material.STONE));
    assertArrayEquals(new int[]{22}, slots);
    assertEquals(0, writes);
    verify(menu, never()).getItemInSlot(anyInt());
    verify(machine, never()).restoreCheckpointForBreak(any());
  }

  @Test
  void playerBreakIsCancelledBeforeInventoriesOrCheckpointAreCleared() {
    Map<String, String> before = new LinkedHashMap<>(data);
    Player player = mock(Player.class);
    BlockBreakEvent event = new BlockBreakEvent(block, player);
    List<ItemStack> drops = new ArrayList<>();
    machine.onBlockBreak().onPlayerBreak(event, new ItemStack(Material.STONE), drops);
    assertTrue(event.isCancelled());
    assertTrue(drops.isEmpty());
    assertEquals(before, data);
    assertEquals(0, writes);
    verifyNoInteractions(menu);
  }

  @Test
  void androidAndExplosionPathsAlsoPreserveTheRecord() {
    var handler = machine.onBlockBreak();
    assertFalse(handler.isAndroidAllowed(block));
    assertFalse(handler.isExplosionAllowed(block));
    AndroidMineEvent event = new AndroidMineEvent(block, null);
    handler.onAndroidBreak(event);
    handler.onExplode(block, new ArrayList<>());
    assertTrue(event.isCancelled());
    assertEquals(0, writes);
    verifyNoInteractions(menu);
  }

  @Test
  void doctorReportsBlockedInsteadOfIdleWithoutDeletingTheRecord() {
    List<String> lines = machine.getMachineDiagnosticLines(block);
    assertTrue(lines.contains(SupremeMachineRecoveryGuard.BLOCKED_MESSAGE));
    assertTrue(lines.stream().anyMatch(line -> line.contains("doctor retry")));
    assertFalse(lines.stream().anyMatch(line -> line.contains("IDLE")));
    assertEquals(0, writes);
    verifyNoInteractions(menu);
  }

  @Test
  void retryRevalidatesButNeverRepairsOrErasesTheOriginalPayload() {
    Map<String, String> before = new LinkedHashMap<>(data);
    assertTrue(machine.isRecoveryBlocked(block));
    assertFalse(machine.retryMachineRecovery(block));
    assertEquals(before, data);
    // An operator restored a valid version-1 empty reservation. Retry only reads it.
    data.put(PREFIX + "consumed", "");
    Map<String, String> repaired = new LinkedHashMap<>(data);
    assertTrue(machine.isRecoveryBlocked(block), "Held until an explicit retry or fresh lifecycle");
    assertTrue(machine.retryMachineRecovery(block));
    assertFalse(machine.isRecoveryBlocked(block));
    assertEquals(repaired, data);
    assertEquals(0, writes);
  }

  @Test
  void freshMachineLifecycleRechecksTheSameSavedPayload() throws Exception {
    assertTrue(machine.isRecoveryBlocked(block));
    GenericMachine restarted = freshMachine();
    assertTrue(restarted.isRecoveryBlocked(block));
    assertEquals("unreadable-reserved-item", data.get(PREFIX + "consumed"));
    assertEquals(0, writes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"unknown-version", "missing-version", "missing-reserved", "wrong-engine"})
  void unsupportedAndPartialRecordsStayBlocked(String damage) {
    switch (damage) {
      case "unknown-version" -> data.put(PREFIX + "state_version", "999");
      case "missing-version" -> data.remove(PREFIX + "state_version");
      case "missing-reserved" -> data.remove(PREFIX + "consumed");
      case "wrong-engine" -> doReturn("TECH_ROBOTIC").when(machine).getPersistentStateType();
      default -> fail(damage);
    }
    Map<String, String> before = new LinkedHashMap<>(data);
    machine.tick(block);
    assertTrue(machine.isRecoveryBlocked(block));
    assertEquals(before, data);
    assertEquals(0, writes);
    verifyNoInteractions(menu);
  }

  @Test
  void anActuallyAbsentCheckpointRemainsAValidIdleMachine() {
    data.keySet().removeIf(key -> key.startsWith(PREFIX));
    assertFalse(machine.isRecoveryBlocked(block));
    assertEquals(0, writes);
  }

  @Test
  void healthyBreakHandlerRetainsItsOriginalThreeBreakModes() {
    AtomicInteger actions = new AtomicInteger();
    var handler = new SupremeMachineBreakHandler(ignored -> false, ignored -> actions.incrementAndGet());
    BlockBreakEvent playerBreak = new BlockBreakEvent(block, mock(Player.class));
    AndroidMineEvent androidBreak = new AndroidMineEvent(block, null);
    assertTrue(handler.isAndroidAllowed(block));
    assertTrue(handler.isExplosionAllowed(block));
    handler.onPlayerBreak(playerBreak, null, new ArrayList<>());
    handler.onAndroidBreak(androidBreak);
    handler.onExplode(block, new ArrayList<>());
    assertFalse(playerBreak.isCancelled());
    assertFalse(androidBreak.isCancelled());
    assertEquals(3, actions.get());
  }

  @Test
  void retryCommandRequiresTheExistingAdminPermission() {
    Player player = mock(Player.class);
    new SupremeCommand(plugin).onCommand(player, mock(Command.class), "supreme",
        new String[]{"doctor", "retry"});
    verify(player, never()).getTargetBlockExact(anyInt());
    verify(machine, never()).retryMachineRecovery(any());
    assertEquals(0, writes);
  }

  @Test
  void retryCommandReportsAnUnrepairedRecordWithoutResettingIt() {
    Player player = mock(Player.class);
    when(player.hasPermission("supreme.admin")).thenReturn(true);
    when(player.getTargetBlockExact(8)).thenReturn(block);
    storage.when(() -> BlockStorage.check(block)).thenReturn(machine);
    new SupremeCommand(plugin).onCommand(player, mock(Command.class), "supreme",
        new String[]{"doctor", "retry"});
    verify(player).sendMessage(contains("Recovery remains blocked"));
    assertEquals("unreadable-reserved-item", data.get(PREFIX + "consumed"));
    assertEquals(0, writes);
  }

  private GenericMachine freshMachine() throws Exception {
    // Avoid item registration; execute real gate/tick/handler methods, never a fake replacement.
    GenericMachine result = mock(GenericMachine.class, CALLS_REAL_METHODS);
    for (Field field : GenericMachine.class.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
      field.setAccessible(true);
      if (Map.class.isAssignableFrom(field.getType())) field.set(result, new java.util.concurrent.ConcurrentHashMap<>());
      if (List.class.isAssignableFrom(field.getType())) field.set(result, new ArrayList<>());
    }
    Field guard = GenericMachine.class.getDeclaredField("recoveryGuard");
    guard.setAccessible(true);
    guard.set(result, new SupremeMachineRecoveryGuard());
    doReturn(new int[]{10, 11}).when(result).getInputSlots();
    doReturn(22).when(result).getStatusSlot();
    return result;
  }
}
