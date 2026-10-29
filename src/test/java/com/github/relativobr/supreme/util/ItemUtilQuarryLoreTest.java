package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Parity tests for the pre-existing quarry item tooltip without stored-lore flattening. */
class ItemUtilQuarryLoreTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanillaAndNamedOutputsKeepTheOriginalLegacyChanceText() {
        for (Material material : new Material[] {Material.DIAMOND, Material.IRON_PICKAXE, Material.NETHER_STAR}) {
            ItemStack item = new ItemStack(material);
            for (int chance : new int[] {-1, 0, 1, 33, 100, Integer.MAX_VALUE}) {
                assertEquals(oldOutputLine(item, chance), ItemUtil.formatQuarryOutputLine(item, chance));
            }

            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Named item", NamedTextColor.DARK_PURPLE));
            item.setItemMeta(meta);
            for (int chance : new int[] {-1, 0, 25, 100, Integer.MAX_VALUE}) {
                assertEquals(oldOutputLine(item, chance), ItemUtil.formatQuarryOutputLine(item, chance));
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation") // The original String-lore setter is a differential test oracle only.
    void legacyQuarryHeadersAndOldStringLoreKeepExactOrderAndColors() {
        List<String> generated = List.of(" ", "\u00a7bIron pickaxe \u00a7e75%", " ",
                "\u00a76Limit Production Quarry");
        for (List<String> oldLines : List.of(List.<String>of(),
                List.of("\u00a7aHistoric machine", "\u00a77Stored text"))) {
            ItemMeta original = new ItemStack(Material.DIAMOND_PICKAXE).getItemMeta();
            if (!oldLines.isEmpty()) {
                original.setLore(oldLines);
            }

            ItemMeta old = original.clone();
            ItemMeta candidate = original.clone();
            List<String> expectedLines = new ArrayList<>(generated);
            if (old.hasLore()) {
                expectedLines.addAll(old.getLore());
            }
            old.setLore(expectedLines);

            ItemUtil.prependQuarryLore(candidate, generated);
            assertEquals(old.lore(), candidate.lore(), "Original lines: " + oldLines);
        }
    }

    @Test
    void existingRichLoreKeepsAllComponentsAndMetadataWithoutReserialization() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        Component rich = Component.text("Owner", NamedTextColor.GREEN)
                .font(Key.key("oldaddon:font"))
                .hoverEvent(HoverEvent.showText(Component.text("Original hover")))
                .insertion("retained-owner");
        Component translated = Component.translatable("item.minecraft.diamond_pickaxe");
        List<Component> original = List.of(rich, translated);
        meta.lore(original);
        var before = meta.clone();
        List<String> generated = List.of(" ", "\u00a7bDiamond \u00a7e20%");

        ItemUtil.prependQuarryLore(meta, generated);
        assertEquals(original, meta.lore().subList(generated.size(), meta.lore().size()));
        assertEquals(Component.text(" "), meta.lore().get(0));
        assertEquals(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize("\u00a7bDiamond \u00a7e20%"), meta.lore().get(1));

        before.lore(null);
        meta.lore(null);
        assertEquals(before, meta, "Non-lore item metadata must be byte/field compatible");
        assertEquals(original, List.of(rich, translated), "Input components must not be mutated");
    }

    @Test
    void repeatedPrependDoesNotDropExistingLoreOrMutateItsInputs() {
        ItemMeta meta = new ItemStack(Material.IRON_PICKAXE).getItemMeta();
        meta.lore(List.of(Component.text("Old item")));
        List<String> generated = List.of(" ", "\u00a7bIron \u00a7e25%");
        ItemUtil.prependQuarryLore(meta, generated);
        ItemUtil.prependQuarryLore(meta, generated);
        assertEquals(5, meta.lore().size());
        assertEquals(Component.text("Old item"), meta.lore().get(4));
        assertEquals(List.of(" ", "\u00a7bIron \u00a7e25%"), generated);
    }

    @SuppressWarnings("deprecation") // Exact historical Bukkit display-name rendering oracle.
    private static String oldOutputLine(ItemStack item, int chance) {
        String name = item.getType().name();
        if (item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName();
        } else {
            name = name.replace("_", " ");
            name = name.substring(0, 1).toUpperCase().concat(name.substring(1).toLowerCase());
            name = "\u00a7b" + name;
        }
        return name + " \u00a7e" + chance + "%";
    }
}
