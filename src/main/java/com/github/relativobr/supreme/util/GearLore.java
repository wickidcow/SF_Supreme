package com.github.relativobr.supreme.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

/** Append generated gear descriptions without round-tripping the existing lore through strings. */
final class GearLore {
    private GearLore() {}

    static void append(ItemMeta meta, List<String> generatedLines) {
        List<Component> existing = meta.lore();
        List<Component> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        if (lore.isEmpty()) {
            lore.add(Component.empty()); // Preserve the historical no-lore spacer.
        }
        for (String line : generatedLines) {
            lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        meta.lore(lore);
    }
}
