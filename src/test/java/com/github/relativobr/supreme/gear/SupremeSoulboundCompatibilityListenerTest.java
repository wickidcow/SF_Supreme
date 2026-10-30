package com.github.relativobr.supreme.gear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Diagnostic queries must tolerate empty inventory slots without consulting
 * Slimefun's item-data service or attempting any legacy migration.
 */
class SupremeSoulboundCompatibilityListenerTest {
    private final SupremeSoulboundCompatibilityListener listener =
            mock(SupremeSoulboundCompatibilityListener.class, CALLS_REAL_METHODS);

    @Test
    void nullSlotIsNotHistoricalRegisteredOrSoulbound() {
        assertFalse(listener.isHistoricalGear(null));
        assertFalse(listener.recognizesSupremeGear(null));
        assertFalse(listener.needsNativeSoulbound(null, null));
    }

    @Test
    void airSlotIsNotHistoricalRegisteredOrSoulbound() {
        ItemStack empty = new ItemStack(Material.AIR);
        assertFalse(listener.isHistoricalGear(empty));
        assertFalse(listener.recognizesSupremeGear(empty));
        assertFalse(listener.needsNativeSoulbound(empty, null));
    }
}
