package com.github.relativobr.supreme.diagnostics;

import com.github.relativobr.supreme.Supreme;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;

/** Optional reflective bridge for loaded Supreme ItemStack legacy-ID migration. */
public final class SupremeLegacyMigrationProviderBridge {

  private static final String MIGRATION_NAME = "Supreme Legacy Item Migration";
  private static final String PROVIDER_CLASS =
      "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider";
  private static final String REPORT_CLASS =
      "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport";

  private static volatile boolean registered;

  private SupremeLegacyMigrationProviderBridge() {
  }

  public static void register(Supreme plugin) {
    SupremeLegacyBlockMigrationProviderBridge.register(plugin);

    if (registered || Supreme.getSupremeOptions().isUseLegacySupremeexpansionItemId()
        || SupremeLegacyIdMappings.activeMappings().isEmpty()) {
      return;
    }

    var slimefun = plugin.getServer().getPluginManager().getPlugin("Slimefun");
    if (slimefun == null) {
      return;
    }

    ClassLoader loader = slimefun.getClass().getClassLoader();
    try {
      Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, loader);
      Class<?> reportClass = Class.forName(REPORT_CLASS, false, loader);
      Constructor<?> reportConstructor = reportClass.getConstructor(
          String.class, boolean.class, long.class, long.class, long.class, long.class, List.class);

      Object provider = Proxy.newProxyInstance(providerClass.getClassLoader(), new Class<?>[]{providerClass},
          (proxy, method, arguments) -> invokeProvider(plugin, reportConstructor, proxy, method, arguments));

      @SuppressWarnings({"unchecked", "rawtypes"})
      Class rawProviderClass = providerClass;
      plugin.getServer().getServicesManager().register(rawProviderClass, provider, plugin, ServicePriority.Normal);
      registered = true;
      plugin.log(Level.INFO, "Registered Supreme legacy ItemStack migration with Slimefun Doctor.");
    } catch (ClassNotFoundException ignored) {
      // Other Slimefun implementations do not necessarily expose Legacy's migration-provider API.
    } catch (ReflectiveOperationException | RuntimeException exception) {
      plugin.getLogger().log(Level.WARNING,
          "Could not register Supreme's optional Slimefun Doctor item migration provider", exception);
    }
  }

  public static void unregister(Supreme plugin) {
    SupremeLegacyBlockMigrationProviderBridge.unregister(plugin);
    if (registered) {
      plugin.getServer().getServicesManager().unregisterAll(plugin);
      registered = false;
    }
  }

  private static Object invokeProvider(
      Supreme plugin,
      Constructor<?> reportConstructor,
      Object proxy,
      Method method,
      Object[] arguments) throws ReflectiveOperationException {
    return switch (method.getName()) {
      case "getMigrationName" -> MIGRATION_NAME;
      case "getLegacyItemMappings" -> SupremeLegacyIdMappings.activeMappings();
      case "runMigration" -> runMigration(plugin, reportConstructor,
          arguments != null && arguments.length > 0 && Boolean.TRUE.equals(arguments[0]));
      case "toString" -> MIGRATION_NAME + " provider";
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
      default -> null;
    };
  }

  private static Object runMigration(Supreme plugin, Constructor<?> reportConstructor, boolean repair)
      throws ReflectiveOperationException {
    SupremeLegacyItemMigrationService.MigrationStats stats =
        new SupremeLegacyItemMigrationService(plugin).scanLoaded(repair);

    List<String> details = new ArrayList<>();
    details.add("Item stacks scanned: " + stats.itemStacksScanned
        + "; legacy: " + stats.legacyItemsFound + "; migrated: " + stats.itemsMigrated
        + "; failures: " + stats.itemFailures + '.');
    details.add("Loaded inventories scanned: " + stats.inventoriesScanned + '.');
    details.add("Scope is loaded-only: loaded chunks/entities/containers and online players.");
    details.add("Placed Supreme blocks are intentionally excluded from this provider and use the exact location-bound block migration lane.");
    details.add("Only Supreme's existing verified old-ID catalog is eligible; disabled-module targets are excluded.");
    details.add("No chunks were force-loaded and Supreme's legacy-ID registration mode is never overridden.");
    if (!repair) {
      details.add("Read-only scan complete. Slimefun Doctor must approve a fingerprinted execution plan before repair.");
    } else {
      details.add("Migrated ItemStacks retain their existing metadata/PDC; only the Slimefun identity is rewritten.");
      details.add("Run the provider again after normal exploration to catch legacy items in newly loaded areas.");
    }
    details.addAll(stats.details);

    return reportConstructor.newInstance(
        MIGRATION_NAME,
        repair,
        stats.itemStacksScanned,
        stats.legacyItemsFound,
        stats.itemsMigrated,
        stats.failures,
        details);
  }
}
