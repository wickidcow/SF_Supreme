package com.github.relativobr.supreme.compat;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import javax.annotation.Nonnull;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import org.bukkit.Location;

/**
 * Cross-fork generator output bridge for Slimefun Legacy, Gugu and United.
 *
 * <p>Supreme generators implement one location-only calculation. Modern block storage and legacy
 * Config dispatch both delegate to it, preventing the two API paths from drifting apart.
 */
@SuppressWarnings({"deprecation", "removal"})
public interface SupremeEnergyProvider extends EnergyNetProvider {

  /** Calculates this generator's output for the supplied location. */
  int getSupremeGeneratedOutput(@Nonnull Location location);

  @Override
  default int getGeneratedOutput(@Nonnull Location location, @Nonnull SlimefunBlockData data) {
    return Math.max(0, getSupremeGeneratedOutput(location));
  }

  /** Compatibility bridge for older Gugu/United energy-network dispatch. */
  @Deprecated
  @Override
  default int getGeneratedOutput(@Nonnull Location location, @Nonnull Config data) {
    return Math.max(0, getSupremeGeneratedOutput(location));
  }
}
