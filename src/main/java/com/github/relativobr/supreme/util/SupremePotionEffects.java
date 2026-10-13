package com.github.relativobr.supreme.util;

import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

/**
 * Resolves Supreme's historical potion names using modern Bukkit registry keys.
 *
 * <p>Older Supreme configurations used pre-1.20.5 Bukkit constant names such as
 * damage_resistance, increase_damage and fast_digging. Those do not match the
 * canonical Minecraft registry names used by modern Paper.</p>
 */
final class SupremePotionEffects {
    private static final Map<String, String> LEGACY_KEYS = Map.ofEntries(
            Map.entry("damage_resistance", "resistance"),
            Map.entry("increase_damage", "strength"),
            Map.entry("fast_digging", "haste"),
            Map.entry("slow_digging", "mining_fatigue"),
            Map.entry("slow", "slowness"),
            Map.entry("jump", "jump_boost"),
            Map.entry("confusion", "nausea"),
            Map.entry("heal", "instant_health"),
            Map.entry("harm", "instant_damage"));

    private SupremePotionEffects() {}

    static PotionEffectType resolve(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return null;
        }

        String key = configuredKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        key = LEGACY_KEYS.getOrDefault(key, key);

        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        } catch (IllegalArgumentException invalidKey) {
            return null;
        }
    }

    static String displayName(PotionEffectType type) {
        String key = type.getKeyOrThrow().getKey();
        if ("dolphins_grace".equals(key)) {
            return "Dolphin's Grace";
        }
        String[] parts = key.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
