package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Persistent state storage for Supreme machines that use a specialized processing engine instead of
 * GenericMachine's staged recipe engine.
 */
@SuppressWarnings("deprecation")
public final class SupremeSpecialMachineStateCodec {

  private static final String KEY_VERSION = "supreme_special_state_version";
  private static final String KEY_TYPE = "supreme_special_state_type";
  private static final String KEY_PROGRESS = "supreme_special_progress";
  private static final String KEY_TICKS = "supreme_special_ticks";
  private static final String KEY_INPUTS = "supreme_special_inputs";
  private static final String KEY_OUTPUTS = "supreme_special_outputs";
  private static final String KEY_RESERVED = "supreme_special_reserved";
  private static final String KEY_AUX_INT = "supreme_special_aux_int";
  private static final String KEY_AUX_TEXT = "supreme_special_aux_text";
  private static final String STATE_VERSION = "1";
  private static final String[] KEYS = {KEY_VERSION, KEY_TYPE, KEY_PROGRESS, KEY_TICKS,
      KEY_INPUTS, KEY_OUTPUTS, KEY_RESERVED, KEY_AUX_INT, KEY_AUX_TEXT};

  private SupremeSpecialMachineStateCodec() {
  }

  public record State(String type, int progress, int ticks, ItemStack[] inputs, ItemStack[] outputs,
                      ItemStack[] reservedItems, int auxInt, String auxText) {
    public MachineRecipe recipe() {
      MachineRecipe recipe = new MachineRecipe(0, inputs, outputs);
      recipe.setTicks(ticks);
      return recipe;
    }
  }

  public static boolean hasState(Block block, String expectedType) {
    if (block == null || expectedType == null) {
      return false;
    }
    return STATE_VERSION.equals(BlockStorage.getLocationInfo(block.getLocation(), KEY_VERSION))
        && expectedType.equals(BlockStorage.getLocationInfo(block.getLocation(), KEY_TYPE));
  }

  /** Includes partial and unsupported records, which must never be mistaken for an idle machine. */
  public static boolean hasStoredData(Block block) {
    if (block == null) return false;
    for (String key : KEYS) {
      if (BlockStorage.getLocationInfo(block.getLocation(), key) != null) return true;
    }
    return false;
  }

  public static void save(Block block, String type, int progress, int ticks, ItemStack[] inputs,
      ItemStack[] outputs, ItemStack[] reservedItems, int auxInt, String auxText) {
    if (block == null || type == null || type.isBlank()) {
      return;
    }

    try {
      // Finish serialization before replacing any part of the previous checkpoint.
      String encodedInputs = encodeItems(inputs);
      String encodedOutputs = encodeItems(outputs);
      String encodedReserved = encodeItems(reservedItems);

      BlockStorage.addBlockInfo(block, KEY_VERSION, STATE_VERSION);
      BlockStorage.addBlockInfo(block, KEY_TYPE, type);
      BlockStorage.addBlockInfo(block, KEY_PROGRESS, Integer.toString(Math.max(0, progress)));
      BlockStorage.addBlockInfo(block, KEY_TICKS, Integer.toString(Math.max(0, ticks)));
      BlockStorage.addBlockInfo(block, KEY_INPUTS, encodedInputs);
      BlockStorage.addBlockInfo(block, KEY_OUTPUTS, encodedOutputs);
      BlockStorage.addBlockInfo(block, KEY_RESERVED, encodedReserved);
      BlockStorage.addBlockInfo(block, KEY_AUX_INT, Integer.toString(auxInt));
      BlockStorage.addBlockInfo(block, KEY_AUX_TEXT, auxText == null ? "" : auxText);
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not persist specialized Supreme machine state at " + describe(block) + ": "
              + ex.getMessage());
    }
  }

  public static void saveProgress(Block block, String expectedType, int progress) {
    if (hasState(block, expectedType)) {
      BlockStorage.addBlockInfo(block, KEY_PROGRESS, Integer.toString(Math.max(0, progress)));
    }
  }

  public static void saveAuxText(Block block, String expectedType, String auxText) {
    if (hasState(block, expectedType)) {
      BlockStorage.addBlockInfo(block, KEY_AUX_TEXT, auxText == null ? "" : auxText);
    }
  }

  public static Optional<State> load(Block block, String expectedType) {
    if (!hasState(block, expectedType)) {
      return Optional.empty();
    }

    try {
      int progress = parseInt(block, KEY_PROGRESS);
      int ticks = parseInt(block, KEY_TICKS);
      int auxInt = parseInt(block, KEY_AUX_INT);
      if (progress < 0 || ticks < 0) throw new IllegalArgumentException("negative checkpoint time");
      String auxText = require(block, KEY_AUX_TEXT);
      return Optional.of(new State(expectedType, progress, ticks,
          decodeItems(require(block, KEY_INPUTS)),
          decodeItems(require(block, KEY_OUTPUTS)),
          decodeItems(require(block, KEY_RESERVED)), auxInt, auxText));
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not restore specialized Supreme machine state at " + describe(block) + ": "
              + ex.getMessage());
      return Optional.empty();
    }
  }

  public static ItemStack[] loadReservedOnly(Block block, String expectedType) {
    if (!hasState(block, expectedType)) {
      return new ItemStack[0];
    }
    try {
      return decodeItems(BlockStorage.getLocationInfo(block.getLocation(), KEY_RESERVED));
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not decode reserved specialized Supreme inputs at " + describe(block) + ": "
              + ex.getMessage());
      return new ItemStack[0];
    }
  }

  public static void clear(Block block) {
    if (block == null) {
      return;
    }
    BlockStorage.addBlockInfo(block, KEY_VERSION, null);
    BlockStorage.addBlockInfo(block, KEY_TYPE, null);
    BlockStorage.addBlockInfo(block, KEY_PROGRESS, null);
    BlockStorage.addBlockInfo(block, KEY_TICKS, null);
    BlockStorage.addBlockInfo(block, KEY_INPUTS, null);
    BlockStorage.addBlockInfo(block, KEY_OUTPUTS, null);
    BlockStorage.addBlockInfo(block, KEY_RESERVED, null);
    BlockStorage.addBlockInfo(block, KEY_AUX_INT, null);
    BlockStorage.addBlockInfo(block, KEY_AUX_TEXT, null);
  }

  private static String encodeItems(ItemStack[] items) {
    if (items == null || items.length == 0) {
      return "";
    }
    return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
  }

  private static ItemStack[] decodeItems(String stored) {
    if (stored == null || stored.isBlank()) {
      return new ItemStack[0];
    }
    return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(stored));
  }

  private static String require(Block block, String key) {
    String value = BlockStorage.getLocationInfo(block.getLocation(), key);
    if (value == null) throw new IllegalArgumentException("missing " + key);
    return value;
  }

  private static int parseInt(Block block, String key) {
    return Integer.parseInt(require(block, key));
  }

  private static String describe(Block block) {
    return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
  }
}
