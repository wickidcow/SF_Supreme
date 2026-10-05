package com.github.relativobr.supreme.util;

import io.github.thebusybiscuit.slimefun4.api.events.AndroidMineEvent;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/** Checks recovery before Slimefun removes a machine's block record or drops its inventories. */
public final class SupremeMachineBreakHandler extends SimpleBlockBreakHandler {

  private final Predicate<Block> blocked;
  private final Consumer<Block> breakMachine;

  public SupremeMachineBreakHandler(Predicate<Block> blocked, Consumer<Block> breakMachine) {
    this.blocked = blocked;
    this.breakMachine = breakMachine;
  }

  @Override
  public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
    if (blocked.test(event.getBlock())) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(SupremeMachineRecoveryGuard.BLOCKED_MESSAGE);
      return;
    }
    onBlockBreak(event.getBlock());
  }

  @Override
  public boolean isAndroidAllowed(Block block) {
    return !blocked.test(block);
  }

  @Override
  public boolean isExplosionAllowed(Block block) {
    return !blocked.test(block);
  }

  @Override
  public void onAndroidBreak(AndroidMineEvent event) {
    if (blocked.test(event.getBlock())) {
      event.setCancelled(true);
      return;
    }
    onBlockBreak(event.getBlock());
  }

  @Override
  public void onBlockBreak(Block block) {
    if (!blocked.test(block)) breakMachine.accept(block);
  }
}
