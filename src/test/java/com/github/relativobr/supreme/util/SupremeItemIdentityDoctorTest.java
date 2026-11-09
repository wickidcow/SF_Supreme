package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class SupremeItemIdentityDoctorTest {

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void reportsIdenticalIdButDifferentAdventureComponentsWithoutChangingItems() {
    MockBukkit.mock();
    ItemStack generated = new ItemStack(Material.DIAMOND);
    ItemMeta generatedMeta = generated.getItemMeta();
    generatedMeta.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, false));
    NamespacedKey id = new NamespacedKey("slimefun", "slimefun_item");
    generatedMeta.getPersistentDataContainer().set(id, PersistentDataType.STRING, "CARBONADO");
    generated.setItemMeta(generatedMeta);
    ItemStack original = generated.clone();

    ItemStack crafted = generated.clone();
    ItemMeta craftedMeta = crafted.getItemMeta();
    craftedMeta.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, true));
    crafted.setItemMeta(craftedMeta);
    ItemStack originalCrafted = crafted.clone();

    List<String> report = SupremeItemIdentityDoctor.compare(generated, crafted);

    assertTrue(report.stream().anyMatch(line -> line.equals(
        "Bukkit stackable (ignoring quantity): false")));
    assertTrue(report.stream().anyMatch(line -> line.equals(
        "Adventure display name equal: false")));
    assertTrue(report.stream().anyMatch(line -> line.equals("PDC keys equal: true")));
    assertTrue(report.stream().anyMatch(line -> line.startsWith("Main name:")));
    assertTrue(report.stream().anyMatch(line -> line.startsWith("Off name:")));
    assertEquals(original, generated);
    assertEquals(originalCrafted, crafted);
  }

  @Test
  void identicalMetadataWithDifferentAmountsIsStackable() {
    MockBukkit.mock();
    ItemStack generated = new ItemStack(Material.IRON_INGOT, 2);
    ItemStack crafted = generated.clone();
    crafted.setAmount(5);
    List<String> report = SupremeItemIdentityDoctor.compare(generated, crafted);
    assertTrue(report.contains("Bukkit stackable (ignoring quantity): true"));
    assertTrue(report.contains("Adventure display name equal: true"));
    assertTrue(report.contains("Adventure lore equal: true"));
  }

  @Test
  void missingOffhandDoesNotCrashOrMutateInventory() {
    MockBukkit.mock();
    List<String> report = SupremeItemIdentityDoctor.compare(
        new ItemStack(Material.IRON_INGOT), new ItemStack(Material.AIR));
    assertTrue(report.getFirst().contains("main hand"));
  }
}
