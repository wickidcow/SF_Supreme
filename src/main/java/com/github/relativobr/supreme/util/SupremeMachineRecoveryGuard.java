package com.github.relativobr.supreme.util;

import com.github.relativobr.supreme.Supreme;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.block.Block;

/** Runtime-only validation cache. The original checkpoint remains in BlockStorage unchanged. */
public final class SupremeMachineRecoveryGuard {

  public static final String BLOCKED_MESSAGE =
      "State: RECOVERY BLOCKED - original machine checkpoint retained";
  public static final String RECOVERY_HELP =
      "Restore the checkpoint from a backup or repair its data, then use /supreme doctor retry.";

  private final Map<Block, Boolean> readable = new ConcurrentHashMap<>();

  public boolean isBlocked(Block block, Predicate<Block> validator) {
    return !readable.computeIfAbsent(block, candidate -> {
      boolean valid;
      try {
        valid = validator.test(candidate);
      } catch (RuntimeException ex) {
        valid = false;
      }
      if (!valid) warn(candidate);
      return valid;
    });
  }

  public void block(Block block) {
    if (!Boolean.FALSE.equals(readable.put(block, false))) warn(block);
  }

  /** Revalidates the original record; never clears or rewrites it. */
  public boolean retry(Block block, Predicate<Block> validator) {
    readable.remove(block);
    return !isBlocked(block, validator);
  }

  public void forget(Block block) {
    readable.remove(block);
  }

  private static void warn(Block block) {
    Supreme.inst().log(Level.WARNING, "Supreme machine recovery blocked at "
        + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + ","
        + block.getZ() + ". Original checkpoint retained. " + RECOVERY_HELP);
  }
}
