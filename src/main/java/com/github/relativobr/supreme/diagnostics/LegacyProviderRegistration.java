package com.github.relativobr.supreme.diagnostics;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/** Register an optional, reflectively loaded API provider without unchecked raw service types. */
final class LegacyProviderRegistration {

  private LegacyProviderRegistration() {
  }

  static <T> void register(ServicesManager services, Class<T> serviceType, Object proxy, Plugin owner) {
    services.register(serviceType, serviceType.cast(proxy), owner, ServicePriority.Normal);
  }
}
