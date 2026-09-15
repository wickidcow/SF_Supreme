package com.github.relativobr.supreme.diagnostics;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.util.CompatibilySupremeLegacyItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Publishes Supreme's existing verified historical ID catalog to Slimefun Legacy Doctor. */
public final class SupremeLegacyIdMappings {

  private SupremeLegacyIdMappings() {
  }

  /**
   * Returns only mappings whose modern target is actually registered in this runtime.
   *
   * <p>When Supreme's legacy-ID compatibility mode is enabled, the old IDs are intentionally canonical for that
   * runtime, so Doctor migration is disabled instead of publishing a misleading old-to-new plan.</p>
   */
  public static Map<String, String> activeMappings() {
    if (Supreme.getSupremeOptions().isUseLegacySupremeexpansionItemId()) {
      return Map.of();
    }

    Map<String, String> mappings = new LinkedHashMap<>();
    for (CompatibilySupremeLegacyItem legacy : Supreme.getLegacyItem()) {
      String oldId = legacy.getOldSupremeID();
      String newId = legacy.getNewSupremeID();
      if (oldId == null || newId == null || oldId.isBlank() || newId.isBlank() || oldId.equals(newId)) {
        continue;
      }
      if (SlimefunItem.getById(newId) == null) {
        continue;
      }
      String previous = mappings.putIfAbsent(oldId, newId);
      if (previous != null && !previous.equals(newId)) {
        throw new IllegalStateException("Conflicting Supreme legacy mapping for " + oldId);
      }
    }
    return Collections.unmodifiableMap(mappings);
  }

  public static void publish(Supreme plugin) {
    if (Supreme.getSupremeOptions().isUseLegacySupremeexpansionItemId()) {
      plugin.log(Level.INFO,
          "Slimefun Doctor legacy-ID migration is disabled because Supreme legacy-ID registration mode is enabled.");
      return;
    }

    Map<String, String> mappings = activeMappings();
    if (mappings.isEmpty()) {
      return;
    }

    Object registry = Slimefun.getRegistry();
    Method register = findRegistrationMethod(registry);
    if (register == null) {
      return;
    }

    int published = 0;
    int failed = 0;
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      try {
        register.invoke(registry, entry.getKey(), entry.getValue());
        published++;
      } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
        failed++;
        plugin.log(Level.WARNING,
            "Could not publish Supreme legacy mapping " + entry.getKey() + " -> " + entry.getValue()
                + ": " + exception.getMessage());
      }
    }

    if (published > 0) {
      plugin.log(Level.INFO,
          "Published " + published + " active Supreme legacy item-ID mappings to Slimefun Doctor.");
    }
    if (failed > 0) {
      plugin.log(Level.WARNING, failed + " Supreme legacy item-ID mapping(s) could not be published.");
    }
  }

  private static Method findRegistrationMethod(Object registry) {
    for (Method method : registry.getClass().getMethods()) {
      if (method.getName().equals("registerLegacySlimefunItemId")
          && method.getParameterCount() == 2
          && method.getParameterTypes()[0] == String.class
          && method.getParameterTypes()[1] == String.class) {
        return method;
      }
    }
    return null;
  }
}
