package com.github.relativobr.supreme.diagnostics;

import com.github.relativobr.supreme.Supreme;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

/** Loaded-only ItemStack migration for Supreme's verified historical Slimefun IDs. */
final class SupremeLegacyItemMigrationService {

  private static final int MAX_NESTED_DEPTH = 4;
  private static final int MAX_DETAIL_SAMPLES = 16;

  private final Supreme plugin;
  private final Map<String, String> mappings = SupremeLegacyIdMappings.activeMappings();
  private final Set<Inventory> seenInventories = Collections.newSetFromMap(new IdentityHashMap<>());

  private Object itemDataService;
  private Method getItemData;
  private Method setItemData;

  SupremeLegacyItemMigrationService(@Nonnull Supreme plugin) {
    this.plugin = plugin;
  }

  @Nonnull
  MigrationStats scanLoaded(boolean repair) {
    MigrationStats stats = new MigrationStats();
    if (mappings.isEmpty()) {
      addDetail(stats, Supreme.getSupremeOptions().isUseLegacySupremeexpansionItemId()
          ? "Supreme legacy-ID registration mode is enabled; old IDs are canonical and Doctor migration is disabled."
          : "No active Supreme legacy mappings are available for the currently enabled Supreme modules.");
      return stats;
    }

    resolveItemDataService(stats);

    for (World world : plugin.getServer().getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        scanChunk(chunk, repair, stats);
      }
    }

    for (Player player : plugin.getServer().getOnlinePlayers()) {
      scanInventory(player.getInventory(), repair, stats, 0);
      scanInventory(player.getEnderChest(), repair, stats, 0);
    }

    return stats;
  }

  private void scanChunk(Chunk chunk, boolean repair, MigrationStats stats) {
    for (BlockState state : chunk.getTileEntities()) {
      if (state instanceof InventoryHolder holder) {
        scanInventory(holder.getInventory(), repair, stats, 0);
      }
    }

    for (Entity entity : chunk.getEntities()) {
      if (entity instanceof Player) {
        continue;
      }
      if (entity instanceof Item itemEntity) {
        ItemStack stack = itemEntity.getItemStack();
        if (inspectItem(stack, repair, stats, 0)) itemEntity.setItemStack(stack);
      } else if (entity instanceof ItemFrame frame) {
        ItemStack stack = frame.getItem();
        if (inspectItem(stack, repair, stats, 0)) frame.setItem(stack);
      } else if (entity instanceof ItemDisplay display) {
        ItemStack stack = display.getItemStack();
        if (inspectItem(stack, repair, stats, 0)) display.setItemStack(stack);
      } else if (entity instanceof LivingEntity living && living.getEquipment() != null) {
        var equipment = living.getEquipment();
        ItemStack main = equipment.getItemInMainHand();
        if (inspectItem(main, repair, stats, 0)) equipment.setItemInMainHand(main);
        ItemStack off = equipment.getItemInOffHand();
        if (inspectItem(off, repair, stats, 0)) equipment.setItemInOffHand(off);
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (ItemStack stack : armor) changed |= inspectItem(stack, repair, stats, 0);
        if (changed) equipment.setArmorContents(armor);
      }

      if (entity instanceof InventoryHolder holder) {
        scanInventory(holder.getInventory(), repair, stats, 0);
      }
    }
  }

  private void scanInventory(Inventory inventory, boolean repair, MigrationStats stats, int depth) {
    if (inventory == null || !seenInventories.add(inventory)) return;
    stats.inventoriesScanned++;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      if (inspectItem(stack, repair, stats, depth)) inventory.setItem(slot, stack);
    }
  }

  private boolean inspectItem(@Nullable ItemStack stack, boolean repair, MigrationStats stats, int depth) {
    if (stack == null || stack.getType().isAir()) return false;

    stats.itemStacksScanned++;
    boolean changed = false;
    String sourceId = rawSlimefunId(stack);
    String targetId = sourceId == null ? null : mappings.get(sourceId);
    if (targetId != null) {
      stats.legacyItemsFound++;
      if (repair) {
        if (!isTargetRegistered(targetId) || !setItemId(stack, targetId)) {
          stats.itemFailures++;
          stats.failures++;
          addDetail(stats, "Could not rewrite Supreme ItemStack " + sourceId + " -> " + targetId + '.');
        } else {
          stats.itemsMigrated++;
          changed = true;
        }
      }
    }

    if (depth >= MAX_NESTED_DEPTH || !stack.hasItemMeta()) return changed;

    var meta = stack.getItemMeta();
    if (meta instanceof BundleMeta bundleMeta) {
      List<ItemStack> items = new ArrayList<>(bundleMeta.getItems());
      boolean nestedChanged = false;
      for (ItemStack nested : items) nestedChanged |= inspectItem(nested, repair, stats, depth + 1);
      if (nestedChanged) {
        bundleMeta.setItems(items);
        stack.setItemMeta(bundleMeta);
        changed = true;
      }
    } else if (meta instanceof BlockStateMeta blockStateMeta) {
      BlockState nestedState = blockStateMeta.getBlockState();
      if (nestedState instanceof InventoryHolder holder) {
        Inventory nestedInventory = holder.getInventory();
        boolean nestedChanged = false;
        for (int slot = 0; slot < nestedInventory.getSize(); slot++) {
          ItemStack nested = nestedInventory.getItem(slot);
          if (inspectItem(nested, repair, stats, depth + 1)) {
            nestedInventory.setItem(slot, nested);
            nestedChanged = true;
          }
        }
        if (nestedChanged) {
          blockStateMeta.setBlockState(nestedState);
          stack.setItemMeta(blockStateMeta);
          changed = true;
        }
      }
    }
    return changed;
  }

  private void resolveItemDataService(MigrationStats stats) {
    try {
      Method getter = Slimefun.class.getMethod("getItemDataService");
      itemDataService = getter.invoke(null);
      getItemData = itemDataService.getClass().getMethod("getItemData", ItemStack.class);
      setItemData = itemDataService.getClass().getMethod("setItemData", ItemStack.class, String.class);
    } catch (ReflectiveOperationException | RuntimeException exception) {
      stats.failures++;
      addDetail(stats, "Slimefun item-data service was unavailable; legacy Supreme ItemStacks cannot be rewritten.");
      plugin.getLogger().log(Level.WARNING, "Supreme Doctor could not resolve Slimefun item-data service", exception);
    }
  }

  @Nullable
  private String rawSlimefunId(ItemStack stack) {
    if (itemDataService == null || getItemData == null) return null;
    try {
      Object result = getItemData.invoke(itemDataService, stack);
      if (result instanceof Optional<?> optional && optional.orElse(null) instanceof String id) return id;
    } catch (ReflectiveOperationException | RuntimeException exception) {
      plugin.getLogger().log(Level.FINE, "Supreme Doctor could not read a Slimefun item ID", exception);
    }
    return null;
  }

  private boolean setItemId(ItemStack stack, String targetId) {
    if (itemDataService == null || setItemData == null) return false;
    try {
      setItemData.invoke(itemDataService, stack, targetId);
      return true;
    } catch (ReflectiveOperationException | RuntimeException exception) {
      plugin.getLogger().log(Level.WARNING, "Supreme Doctor could not rewrite an item ID to " + targetId, exception);
      return false;
    }
  }

  private boolean isTargetRegistered(String targetId) {
    return SlimefunItem.getById(targetId) != null;
  }

  private static void addDetail(MigrationStats stats, String detail) {
    if (stats.details.size() < MAX_DETAIL_SAMPLES) stats.details.add(detail);
  }

  static final class MigrationStats {
    long inventoriesScanned;
    long itemStacksScanned;
    long legacyItemsFound;
    long itemsMigrated;
    long itemFailures;
    long failures;
    final List<String> details = new ArrayList<>();
  }
}
