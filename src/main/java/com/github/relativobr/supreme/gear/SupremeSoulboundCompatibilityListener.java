package com.github.relativobr.supreme.gear;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.diagnostics.SupremeLegacyIdMappings;
import com.github.relativobr.supreme.tools.AbstractTools;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.Soulbound;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Repairs known historical Supreme gear IDs before Slimefun's native death handler runs.
 *
 * <p>Modern Supreme gear is normally identified via Slimefun's Soulbound attribute.
 * Historical ARMOR_/WEAPON_/TOOL_ IDs may not resolve after an ID-mode change; this
 * listener only repairs IDs with an explicit, registered Supreme mapping. It never
 * trusts an item's name, lore alone, or an unknown ID.</p>
 */
public final class SupremeSoulboundCompatibilityListener implements Listener {
    private final Map<String, SlimefunItem> historicalGear = new HashMap<>();

    public SupremeSoulboundCompatibilityListener(Supreme plugin) {
        for (Map.Entry<String, String> mapping : SupremeLegacyIdMappings.activeMappings().entrySet()) {
            SlimefunItem current = SlimefunItem.getById(mapping.getValue());
            if (isSupremeGear(current)) {
                historicalGear.put(mapping.getKey(), current);
            }
        }
        if (!historicalGear.isEmpty()) {
            plugin.getLogger().info("Soulbound compatibility recognizes "
                    + historicalGear.size() + " historical Supreme gear IDs.");
        }
    }

    private static boolean isSupremeGear(SlimefunItem item) {
        return item instanceof Soulbound
                && (item instanceof AbstractArmor
                    || item instanceof AbstractWeapons
                    || item instanceof AbstractTools);
    }

    /** Read-only diagnostic: returns whether Supreme recognizes this item's saved ID. */
    public boolean recognizesSupremeGear(ItemStack stack) {
        return getRegisteredGear(stack) != null;
    }

    /** Read-only diagnostic: returns whether this is an unmigrated historical ID. */
    public boolean isHistoricalGear(ItemStack stack) {
        String id = storedId(stack);
        return id != null && SlimefunItem.getById(id) == null && historicalGear.containsKey(id);
    }

    private String storedId(ItemStack stack) {
        return Slimefun.getItemDataService().getItemData(stack).orElse(null);
    }

    private SlimefunItem getRegisteredGear(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String id = storedId(stack);
        if (id == null) {
            return null;
        }
        SlimefunItem candidate = SlimefunItem.getById(id);
        // A registered third-party item with the same historical ID owns that ID.
        // Never reinterpret someone else's registered item as Supreme gear.
        if (candidate == null) {
            candidate = historicalGear.get(id);
        }
        return isSupremeGear(candidate) && candidate.getItem().getType() == stack.getType()
                ? candidate : null;
    }

    boolean needsNativeSoulbound(ItemStack stack, World world) {
        SlimefunItem gear = getRegisteredGear(stack);
        return gear != null && !gear.isDisabledIn(world) && !SlimefunUtils.isSoulbound(stack, world);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }

        Player player = event.getEntity();
        // The item must exist in BOTH the original inventory and the death drop list
        // or no repair is performed. This avoids rescuing a second copy of removed gear.
        SupremeSoulboundGear.repairDeathDrops(player.getInventory(), event.getDrops(),
                stack -> needsNativeSoulbound(stack, player.getWorld()),
                Slimefun.getRegistry().getSoulboundDataKey());
        // Slimefun's HIGHEST handler is the sole owner of drop removal and respawn returns.
    }
}
