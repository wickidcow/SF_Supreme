package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GearLoreTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void richItemLoreIsRetainedExactly() {
        ItemMeta meta = meta(); var before = rich(); meta.lore(before);
        GearLore.append(meta, List.of("\u00a7bSoulbound", "\u00a75Strength II"));
        assertEquals(before, meta.lore().subList(0, before.size()));
        assertEquals(before.size() + 2, meta.lore().size());
    }

    @Test
    void absentAndEmptyLoreKeepTheOldSpacer() {
        for (boolean empty : List.of(false, true)) {
            ItemMeta meta = meta(); meta.lore(empty ? List.of() : null);
            GearLore.append(meta, List.of("\u00a7bSoulbound"));
            assertEquals(List.of(Component.empty(), legacy("\u00a7bSoulbound")), meta.lore());
        }
    }

    @Test
    void noSpacerIsInsertedWhenExistingLoreIsPresent() {
        ItemMeta meta = meta(); meta.lore(List.of(Component.text("existing")));
        GearLore.append(meta, List.of("\u00a7bSoulbound"));
        assertEquals(List.of(Component.text("existing"), legacy("\u00a7bSoulbound")), meta.lore());
    }

    @Test
    @SuppressWarnings("deprecation") // Differential fixture for the former String-lore append.
    void generatedLegacyTextAndOrderingMatchTheOriginalBehavior() {
        var random = new Random(92301L);
        for (int attempt = 0; attempt < 250; attempt++) {
            ItemMeta old = meta();
            var lines = new ArrayList<String>();
            for (int i = random.nextInt(7); i > 0; i--) lines.add("\u00a7aOriginal " + i);
            if (random.nextBoolean()) old.setLore(lines);
            ItemMeta candidate = old.clone();
            var tail = List.of("\u00a7bSoulbound", "\u00a75Strength II", "\u00a75Speed X");
            var expected = old.hasLore() ? new ArrayList<>(old.getLore()) : new ArrayList<String>();
            if (!old.hasLore()) expected.add("");
            expected.addAll(tail); old.setLore(expected);
            GearLore.append(candidate, tail);
            assertEquals(old.lore(), candidate.lore(), "Historical layout " + attempt);
        }
    }

    @Test
    void existingItemIdentityAmountsAndNonLoreMetadataRemainIntact() {
        var item = new ItemStack(Material.DIAMOND_CHESTPLATE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "OLD_SUPREME_ARMOR");
        meta.getPersistentDataContainer().set(key("oldaddon:owner"), PersistentDataType.STRING, "retained-owner");
        meta.getPersistentDataContainer().set(key("oldaddon:count"), PersistentDataType.LONG, 9_000_000_001L);
        meta.getPersistentDataContainer().set(key("oldaddon:charge"), PersistentDataType.FLOAT, 123.4567F);
        ((Damageable) meta).setDamage(117); meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        var models = meta.getCustomModelDataComponent(); models.setFloats(List.of(12345.25F));
        models.setStrings(List.of("old-armor-model")); meta.setCustomModelDataComponent(models);
        meta.displayName(Component.text("Custom armor name"));
        var before = meta.clone();
        GearLore.append(meta, List.of("\u00a7bSoulbound")); item.setItemMeta(meta);
        before.lore(null); meta.lore(null); assertEquals(before, meta);
        assertEquals(1, item.getAmount()); assertEquals(Material.DIAMOND_CHESTPLATE, item.getType());
    }

    @Test
    void immutableInputsAreNotModified() {
        ItemMeta meta = meta(); var before = rich(); meta.lore(before);
        var lines = List.of("\u00a7bSoulbound");
        assertDoesNotThrow(() -> GearLore.append(meta, lines));
        assertEquals(rich(), before); assertEquals(List.of("\u00a7bSoulbound"), lines);
    }

    @Test
    void repeatedCallsPreserveTheExistingAppendOnlyContract() {
        ItemMeta meta = meta(); meta.lore(rich());
        GearLore.append(meta, List.of("\u00a7bSoulbound"));
        GearLore.append(meta, List.of("\u00a7bSoulbound"));
        assertEquals(rich().size() + 2, meta.lore().size());
        assertEquals(rich(), meta.lore().subList(0, rich().size()));
    }

    @Test
    void generatedEmptyTailDoesNotEraseExistingLore() {
        ItemMeta meta = meta(); meta.lore(rich());
        GearLore.append(meta, List.of()); assertEquals(rich(), meta.lore());
    }

    private static ItemMeta meta() { return new ItemStack(Material.DIAMOND_CHESTPLATE).getItemMeta(); }
    private static Component legacy(String line) { return LegacyComponentSerializer.legacySection().deserialize(line); }
    private static NamespacedKey key(String value) { return java.util.Objects.requireNonNull(NamespacedKey.fromString(value)); }
    private static List<Component> rich() {
        return List.of(Component.translatable("item.minecraft.diamond_chestplate"),
            Component.text("Original", NamedTextColor.GOLD).font(Key.key("oldaddon:font"))
                .hoverEvent(HoverEvent.showText(Component.text("Kept"))).insertion("original"));
    }
}
