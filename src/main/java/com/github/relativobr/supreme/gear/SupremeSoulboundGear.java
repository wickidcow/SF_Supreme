package com.github.relativobr.supreme.gear;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Writes Slimefun's actual soulbound PDC flag rather than relying on decorative lore.
 *
 * <p>Older Supreme equipment used an aqua "Soulbound" description, while Slimefun's
 * legacy lore fallback only recognizes the canonical gray line. Repairing that single
 * line and adding the native flag leaves item IDs, other metadata and customizations intact.</p>
 */
public final class SupremeSoulboundGear {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Component REAL_SOULBOUND_LORE = LEGACY.deserialize("§7Soulbound");
    private static final Component OLD_DISPLAY_ONLY_LORE = LEGACY.deserialize("§bSoulbound");

    private SupremeSoulboundGear() {}

    /** Stamp new equipment templates using the current Slimefun registry's key. */
    public static void markTemplate(ItemMeta meta, NamespacedKey soulboundKey) {
        meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Repairs a verified historical piece without mutating the caller's stack or its ID.
     * Never call this for an item selected solely by its display name or lore.
     */
    public static ItemStack repair(ItemStack original, NamespacedKey soulboundKey) {
        ItemStack result = original.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return result;
        }

        markTemplate(meta, soulboundKey);
        List<Component> existing = meta.lore();
        List<Component> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (!lore.contains(REAL_SOULBOUND_LORE)) {
            int oldLine = lore.indexOf(OLD_DISPLAY_ONLY_LORE);
            if (oldLine >= 0) {
                lore.set(oldLine, REAL_SOULBOUND_LORE);
            } else {
                lore.add(REAL_SOULBOUND_LORE);
            }
            meta.lore(lore);
        }
        result.setItemMeta(meta);
        return result;
    }

    /**
     * Mark only verified gear that is present in both the death inventory and drop list.
     * Slimefun's HIGHEST-priority listener then removes these drops and restores the
     * equipment on respawn. No second cache or return-items listener is created.
     */
    public static int repairDeathDrops(PlayerInventory inventory, List<ItemStack> drops,
                                        Predicate<ItemStack> requiresRepair, NamespacedKey soulboundKey) {
        Set<Integer> consumedDrops = new HashSet<>();
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            if (original == null || original.getType().isAir() || !requiresRepair.test(original)) {
                continue;
            }

            int matchingDrop = -1;
            for (int index = 0; index < drops.size(); index++) {
                if (!consumedDrops.contains(index) && original.equals(drops.get(index))) {
                    matchingDrop = index;
                    break;
                }
            }
            if (matchingDrop < 0) {
                continue;
            }

            ItemStack protectedStack = repair(original, soulboundKey);
            inventory.setItem(slot, protectedStack);
            drops.set(matchingDrop, protectedStack.clone());
            consumedDrops.add(matchingDrop);
            repaired++;
        }
        return repaired;
    }
}
