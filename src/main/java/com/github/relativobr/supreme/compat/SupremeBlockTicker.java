package com.github.relativobr.supreme.compat;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.Objects;
import java.util.function.Consumer;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.block.Block;

/**
 * Cross-fork ticker adapter for Slimefun Legacy, Gugu and United.
 *
 * <p>The modern block-data overload is the primary execution path. The legacy {@link Config}
 * overload is retained in one place for forks or older schedulers that still dispatch through it.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class SupremeBlockTicker extends BlockTicker {

  private final boolean synchronizedTicker;
  private final Consumer<Block> ticker;

  public SupremeBlockTicker(boolean synchronizedTicker, Consumer<Block> ticker) {
    this.synchronizedTicker = synchronizedTicker;
    this.ticker = Objects.requireNonNull(ticker, "ticker");
  }

  @Override
  public void tick(Block block, SlimefunItem item, SlimefunBlockData data) {
    ticker.accept(block);
  }

  /** Compatibility bridge for older Gugu/United dispatcher paths. */
  @Deprecated
  @Override
  public void tick(Block block, SlimefunItem item, Config data) {
    ticker.accept(block);
  }

  @Override
  public boolean isSynchronized() {
    return synchronizedTicker;
  }
}
