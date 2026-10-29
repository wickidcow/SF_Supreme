package com.github.relativobr.supreme.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

/** Compatibility for Supreme's older Bukkit enchantment names in config.yml. */
final class SupremeEnchantmentResolver {
    private static final Map<String, String> OLD_KEYS = Map.ofEntries(
            Map.entry("fall_damage", "feather_falling"),
            Map.entry("protection_fall", "feather_falling"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"),
            Map.entry("protection_explosions", "blast_protection"),
            Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("damage_all", "sharpness"),
            Map.entry("damage_undead", "smite"),
            Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("dig_speed", "efficiency"),
            Map.entry("durability", "unbreaking"),
            Map.entry("loot_bonus_mobs", "looting"),
            Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("oxygen", "respiration"),
            Map.entry("water_worker", "aqua_affinity"));

    private SupremeEnchantmentResolver() {}

    static Enchantment resolve(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }
        String key = configuredKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        key = OLD_KEYS.getOrDefault(key, key);
        try {
            return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(key));
        } catch (IllegalArgumentException invalidKey) {
            return null;
        }
    }
}
