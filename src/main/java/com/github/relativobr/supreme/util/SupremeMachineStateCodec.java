package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Persists Supreme machine processing state in Slimefun block data.
 *
 * <p>ItemStacks are stored using Paper's NBT byte serialization so exact Slimefun metadata survives
 * restarts and Minecraft data migrations. Reserved quantities are stored separately so amounts larger
 * than a legal physical stack never need to be represented as an illegal ItemStack.</p>
 */
@SuppressWarnings("deprecation")
public final class SupremeMachineStateCodec {

  private static final String KEY_VERSION = "supreme_machine_state_version";
  private static final String KEY_INPUT = "supreme_machine_recipe_input";
  private static final String KEY_OUTPUT = "supreme_machine_recipe_output";
  private static final String KEY_TICKS = "supreme_machine_recipe_ticks";
  private static final String KEY_PROGRESS = "supreme_machine_progress";
  private static final String KEY_ATTEMPTS = "supreme_machine_attempts";
  private static final String KEY_CONSUMED = "supreme_machine_consumed";
  private static final String STATE_VERSION = "1";

  private SupremeMachineStateCodec() {
  }

  public record State(MachineRecipe recipe, int progress, int attempts,
                      Map<ItemStack, Integer> consumedItems) {
  }

  public static boolean hasState(Block block) {
    return STATE_VERSION.equals(BlockStorage.getLocationInfo(block.getLocation(), KEY_VERSION));
  }

  public static void save(Block block, MachineRecipe recipe, int progress, int attempts,
      Map<ItemStack, Integer> consumedItems) {
    if (block == null || recipe == null) {
      return;
    }

    try {
      Base64.Encoder encoder = Base64.getEncoder();
      String input = encoder.encodeToString(ItemStack.serializeItemsAsBytes(recipe.getInput()));
      String output = encoder.encodeToString(ItemStack.serializeItemsAsBytes(recipe.getOutput()));

      BlockStorage.addBlockInfo(block, KEY_VERSION, STATE_VERSION);
      BlockStorage.addBlockInfo(block, KEY_INPUT, input);
      BlockStorage.addBlockInfo(block, KEY_OUTPUT, output);
      BlockStorage.addBlockInfo(block, KEY_TICKS, Integer.toString(recipe.getTicks()));
      BlockStorage.addBlockInfo(block, KEY_PROGRESS, Integer.toString(Math.max(progress, 0)));
      BlockStorage.addBlockInfo(block, KEY_ATTEMPTS, Integer.toString(Math.max(attempts, 0)));
      BlockStorage.addBlockInfo(block, KEY_CONSUMED, encodeConsumed(consumedItems, encoder));
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not persist Supreme machine state at " + describe(block) + ": " + ex.getMessage());
    }
  }

  public static Optional<State> load(Block block) {
    if (block == null || !hasState(block)) {
      return Optional.empty();
    }

    try {
      Base64.Decoder decoder = Base64.getDecoder();
      String inputData = require(block, KEY_INPUT);
      String outputData = require(block, KEY_OUTPUT);
      int ticks = parseNonNegative(require(block, KEY_TICKS), KEY_TICKS);
      int progress = parseNonNegative(require(block, KEY_PROGRESS), KEY_PROGRESS);
      int attempts = parseNonNegative(require(block, KEY_ATTEMPTS), KEY_ATTEMPTS);

      ItemStack[] input = ItemStack.deserializeItemsFromBytes(decoder.decode(inputData));
      ItemStack[] output = ItemStack.deserializeItemsFromBytes(decoder.decode(outputData));
      if (input == null || output == null || output.length == 0) {
        throw new IllegalArgumentException("recipe arrays are empty");
      }

      MachineRecipe recipe = new MachineRecipe(ticks, input, output);
      Map<ItemStack, Integer> consumed = decodeConsumed(
          BlockStorage.getLocationInfo(block.getLocation(), KEY_CONSUMED), decoder);
      return Optional.of(new State(recipe, progress, attempts, consumed));
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not restore Supreme machine state at " + describe(block) + ": " + ex.getMessage());
      return Optional.empty();
    }
  }

  public static Map<ItemStack, Integer> loadConsumedOnly(Block block) {
    if (block == null || !hasState(block)) {
      return new LinkedHashMap<>();
    }

    try {
      return decodeConsumed(BlockStorage.getLocationInfo(block.getLocation(), KEY_CONSUMED),
          Base64.getDecoder());
    } catch (RuntimeException ex) {
      Supreme.inst().log(Level.WARNING,
          "Could not decode reserved Supreme inputs at " + describe(block) + ": " + ex.getMessage());
      return new LinkedHashMap<>();
    }
  }

  public static void clear(Block block) {
    if (block == null) {
      return;
    }
    BlockStorage.addBlockInfo(block, KEY_VERSION, null);
    BlockStorage.addBlockInfo(block, KEY_INPUT, null);
    BlockStorage.addBlockInfo(block, KEY_OUTPUT, null);
    BlockStorage.addBlockInfo(block, KEY_TICKS, null);
    BlockStorage.addBlockInfo(block, KEY_PROGRESS, null);
    BlockStorage.addBlockInfo(block, KEY_ATTEMPTS, null);
    BlockStorage.addBlockInfo(block, KEY_CONSUMED, null);
  }

  private static String encodeConsumed(Map<ItemStack, Integer> consumedItems,
      Base64.Encoder encoder) {
    if (consumedItems == null || consumedItems.isEmpty()) {
      return "";
    }

    StringBuilder value = new StringBuilder();
    for (Map.Entry<ItemStack, Integer> entry : consumedItems.entrySet()) {
      ItemStack item = entry.getKey();
      int amount = entry.getValue() == null ? 0 : entry.getValue();
      if (item == null || item.getType().isAir() || amount <= 0) {
        continue;
      }

      ItemStack template = item.clone();
      template.setAmount(1);
      if (value.length() > 0) {
        value.append(';');
      }
      value.append(amount)
          .append(',')
          .append(encoder.encodeToString(template.serializeAsBytes()));
    }
    return value.toString();
  }

  private static Map<ItemStack, Integer> decodeConsumed(String stored, Base64.Decoder decoder) {
    Map<ItemStack, Integer> consumed = new LinkedHashMap<>();
    if (stored == null || stored.isBlank()) {
      return consumed;
    }

    for (String token : stored.split(";")) {
      if (token.isBlank()) {
        continue;
      }
      int separator = token.indexOf(',');
      if (separator <= 0 || separator >= token.length() - 1) {
        throw new IllegalArgumentException("invalid reserved-item token");
      }

      int amount = parseNonNegative(token.substring(0, separator), KEY_CONSUMED);
      if (amount <= 0) {
        continue;
      }
      ItemStack item = ItemStack.deserializeBytes(decoder.decode(token.substring(separator + 1)));
      item.setAmount(1);
      consumed.put(item, amount);
    }
    return consumed;
  }

  private static String require(Block block, String key) {
    String value = BlockStorage.getLocationInfo(block.getLocation(), key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("missing " + key);
    }
    return value;
  }

  private static int parseNonNegative(String value, String key) {
    int parsed = Integer.parseInt(value);
    if (parsed < 0) {
      throw new IllegalArgumentException(key + " must not be negative");
    }
    return parsed;
  }

  private static String describe(Block block) {
    return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
  }
}
