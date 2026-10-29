package com.github.relativobr.supreme.resource.magical;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** The existing attribute-item operation, isolated from static template initialization. */
final class SupremeAttributeEnchantments {
    private SupremeAttributeEnchantments() {}

    static void apply(ItemStack item, String key, int level) {
        // Resolve at the same point of use; do not cache a registry across server lifecycles.
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }
}
