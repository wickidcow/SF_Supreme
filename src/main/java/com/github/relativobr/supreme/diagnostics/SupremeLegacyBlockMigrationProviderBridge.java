package com.github.relativobr.supreme.diagnostics;

import com.github.relativobr.supreme.Supreme;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;

/** Optional reflective bridge into Slimefun Legacy's exact placed-block migration API. */
public final class SupremeLegacyBlockMigrationProviderBridge {

  private static final String NAME = "Supreme Legacy Placed-Block Migration";
  private static final String PROVIDER =
      "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider";
  private static final String CANDIDATE =
      "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate";
  private static final String RESULT =
      "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationResult";

  private static volatile boolean registered;

  private SupremeLegacyBlockMigrationProviderBridge() {
  }

  public static void register(Supreme plugin) {
    if (registered || Supreme.getSupremeOptions().isUseLegacySupremeexpansionItemId()
        || SupremeLegacyIdMappings.activeMappings().isEmpty()) {
      return;
    }
    var slimefun = plugin.getServer().getPluginManager().getPlugin("Slimefun");
    if (slimefun == null) return;

    try {
      ClassLoader loader = slimefun.getClass().getClassLoader();
      Class<?> providerClass = Class.forName(PROVIDER, false, loader);
      Class<?> candidateClass = Class.forName(CANDIDATE, false, loader);
      Class<?> resultClass = Class.forName(RESULT, false, loader);
      Runtime runtime = new Runtime(plugin, candidateClass, resultClass);
      Object proxy = java.lang.reflect.Proxy.newProxyInstance(
          providerClass.getClassLoader(),
          new Class<?>[]{providerClass},
          (self, method, args) -> switch (method.getName()) {
            case "getMigrationName" -> NAME;
            case "getLegacyBlockMappings" -> SupremeLegacyIdMappings.activeMappings();
            case "scanLoadedCandidates" -> runtime.scan();
            case "isCandidateStillValid" -> runtime.valid(args == null ? null : args[0]);
            case "migrate" -> runtime.migrate(args == null ? null : args[0]);
            case "toString" -> NAME + " provider";
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == (args == null || args.length == 0 ? null : args[0]);
            default -> null;
          });
      @SuppressWarnings({"rawtypes", "unchecked"})
      Class raw = providerClass;
      plugin.getServer().getServicesManager().register(raw, proxy, plugin, ServicePriority.Normal);
      registered = true;
      plugin.log(Level.INFO, "Registered exact Supreme placed-block migration with Slimefun Doctor.");
    } catch (ClassNotFoundException ignored) {
      // Older/non-Legacy Slimefun builds do not expose the exact placed-block API.
    } catch (ReflectiveOperationException | RuntimeException exception) {
      plugin.getLogger().log(Level.WARNING,
          "Could not register Supreme's exact placed-block migration provider", exception);
    }
  }

  public static void unregister(Supreme plugin) {
    if (registered) {
      plugin.getServer().getServicesManager().unregisterAll(plugin);
      registered = false;
    }
  }

  private static final class Runtime {
    private final Supreme plugin;
    private final Class<?> candidateClass;
    private final Constructor<?> candidateCtor;
    private final Method worldId;
    private final Method x;
    private final Method y;
    private final Method z;
    private final Method sourceId;
    private final Method targetId;
    private final Method stateClaim;
    private final Method migrated;
    private final Method skipped;
    private final Method blocked;
    private final Method failed;
    private final Method controller;
    private final Method loadedData;
    private final Method snapshotData;
    private final Method snapshotMenu;
    private final Method migrateBlock;

    Runtime(Supreme plugin, Class<?> candidateClass, Class<?> resultClass)
        throws ReflectiveOperationException {
      this.plugin = plugin;
      this.candidateClass = candidateClass;
      candidateCtor = candidateClass.getConstructor(
          UUID.class, int.class, int.class, int.class, String.class, String.class, String.class);
      worldId = candidateClass.getMethod("worldId");
      x = candidateClass.getMethod("x");
      y = candidateClass.getMethod("y");
      z = candidateClass.getMethod("z");
      sourceId = candidateClass.getMethod("sourceId");
      targetId = candidateClass.getMethod("targetId");
      stateClaim = candidateClass.getMethod("stateClaim");
      migrated = resultClass.getMethod("migrated", String.class);
      skipped = resultClass.getMethod("skipped", String.class);
      blocked = resultClass.getMethod("blocked", String.class);
      failed = resultClass.getMethod("failed", String.class);
      controller = privateMethod("blockDataController");
      loadedData = privateMethod("loadedBlockData", Object.class);
      snapshotData = privateMethod("snapshotData", Object.class);
      snapshotMenu = privateMethod("snapshotMenu", Object.class);
      migrateBlock = privateMethod(
          "migrateBlock", Object.class, Object.class, String.class, String.class, Location.class);
    }

    private Method privateMethod(String name, Class<?>... types) throws NoSuchMethodException {
      Method method = SupremeLegacyMigrationService.class.getDeclaredMethod(name, types);
      method.setAccessible(true);
      return method;
    }

    Collection<Object> scan() throws ReflectiveOperationException {
      SupremeLegacyMigrationService service = new SupremeLegacyMigrationService(plugin);
      Object c = controller.invoke(service);
      if (c == null) throw new IllegalStateException("Slimefun block-data controller unavailable");
      List<Object> result = new ArrayList<>();
      for (Object data : blocks(service, c)) {
        String from = stringCall(data, "getSfId");
        String to = from == null ? null : SupremeLegacyIdMappings.activeMappings().get(from);
        Location loc = locationCall(data);
        if (to == null || loc == null || loc.getWorld() == null
            || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
          continue;
        }
        result.add(candidateCtor.newInstance(
            loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            from, to, claim(service, data, from, to)));
      }
      return List.copyOf(result);
    }

    boolean valid(Object candidate) {
      try {
        View view = view(candidate);
        Live live = live(view);
        return live != null && view.claim.equals(claim(live.service, live.data, view.from, view.to));
      } catch (ReflectiveOperationException | RuntimeException exception) {
        return false;
      }
    }

    Object migrate(Object candidate) throws ReflectiveOperationException {
      if (!candidateClass.isInstance(candidate)) {
        return blocked.invoke(null, "Unrecognized candidate type.");
      }
      View view = view(candidate);
      if (!view.to.equals(SupremeLegacyIdMappings.activeMappings().get(view.from))) {
        return blocked.invoke(null, "Legacy mapping changed.");
      }
      Live live = live(view);
      if (live == null) {
        return skipped.invoke(null, "Block is no longer loaded with the approved legacy ID.");
      }
      if (!view.claim.equals(claim(live.service, live.data, view.from, view.to))) {
        return skipped.invoke(null, "Block state changed after authorization; nothing was modified.");
      }
      try {
        migrateBlock.invoke(live.service, live.controller, live.data, view.from, view.to, live.location);
        return migrated.invoke(null, view.from + " -> " + view.to);
      } catch (InvocationTargetException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        plugin.getLogger().log(Level.WARNING,
            "Exact Supreme placed-block migration failed at " + live.location, cause);
        return failed.invoke(null,
            "Migration failed; Supreme attempted rollback to the original placed block.");
      }
    }

    private Live live(View view) throws ReflectiveOperationException {
      World world = plugin.getServer().getWorld(view.world);
      if (world == null || !world.isChunkLoaded(view.x >> 4, view.z >> 4)) return null;
      Location loc = new Location(world, view.x, view.y, view.z);
      SupremeLegacyMigrationService service = new SupremeLegacyMigrationService(plugin);
      Object c = controller.invoke(service);
      if (c == null) return null;
      for (Object data : blocks(service, c)) {
        Location found = locationCall(data);
        if (sameBlock(loc, found) && view.from.equals(stringCall(data, "getSfId"))) {
          return new Live(service, c, data, loc);
        }
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> blocks(SupremeLegacyMigrationService service, Object c)
        throws ReflectiveOperationException {
      return (List<Object>) loadedData.invoke(service, c);
    }

    private String claim(SupremeLegacyMigrationService service, Object data, String from, String to)
        throws ReflectiveOperationException {
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, "supreme-block-v1\n" + from + "\n" + to + "\n");
        @SuppressWarnings("unchecked")
        Map<String, String> values = (Map<String, String>) snapshotData.invoke(service, data);
        for (var entry : new TreeMap<>(values).entrySet()) {
          update(digest, "kv:" + entry.getKey() + "=" + entry.getValue() + "\n");
        }
        ItemStack[] menu = (ItemStack[]) snapshotMenu.invoke(service, data);
        if (menu == null) {
          update(digest, "menu:none\n");
        } else {
          for (int i = 0; i < menu.length; i++) {
            ItemStack stack = menu[i];
            if (stack == null || stack.getType().isAir()) continue;
            update(digest, "slot:" + i + ":" + stack.getType() + ":" + stack.getAmount() + ":"
                + stack.serialize() + "\n");
          }
        }
        return HexFormat.of().formatHex(digest.digest());
      } catch (java.security.NoSuchAlgorithmException exception) {
        throw new IllegalStateException(exception);
      }
    }

    private View view(Object candidate) throws ReflectiveOperationException {
      return new View(
          (UUID) worldId.invoke(candidate),
          (int) x.invoke(candidate),
          (int) y.invoke(candidate),
          (int) z.invoke(candidate),
          (String) sourceId.invoke(candidate),
          (String) targetId.invoke(candidate),
          (String) stateClaim.invoke(candidate));
    }

    private static boolean sameBlock(Location a, Location b) {
      return b != null && a.getWorld() != null && b.getWorld() != null
          && a.getWorld().getUID().equals(b.getWorld().getUID())
          && a.getBlockX() == b.getBlockX()
          && a.getBlockY() == b.getBlockY()
          && a.getBlockZ() == b.getBlockZ();
    }

    private static Location locationCall(Object target) {
      Object value = call(target, "getLocation");
      return value instanceof Location location ? location : null;
    }

    private static String stringCall(Object target, String name) {
      Object value = call(target, name);
      return value instanceof String string ? string : null;
    }

    private static Object call(Object target, String name) {
      try {
        return target == null ? null : target.getClass().getMethod(name).invoke(target);
      } catch (ReflectiveOperationException | RuntimeException ignored) {
        return null;
      }
    }

    private static void update(MessageDigest digest, String text) {
      digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    private record View(UUID world, int x, int y, int z, String from, String to, String claim) {}
    private record Live(
        SupremeLegacyMigrationService service, Object controller, Object data, Location location) {}
  }
}
