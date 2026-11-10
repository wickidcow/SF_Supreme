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
    assertTrue(report.contains("Non-presentation metadata equal (diagnostic clones): true"));
    assertTrue(report.stream().anyMatch(line -> line.startsWith("Main name:")));
    assertTrue(report.stream().anyMatch(line -> line.startsWith("Off name:")));
    assertEquals(original, generated);
    assertEquals(originalCrafted, crafted);
  }

  @Test
  void realOwnerCarbonadoNestedGeneratorVersusFlatCheatBoldMismatchIsNotStackable() {
    MockBukkit.mock();
    ItemStack machine = new ItemStack(Material.PLAYER_HEAD, 64);
    ItemMeta machineMeta = machine.getItemMeta();
    machineMeta.displayName(Component.text("").append(
        Component.text("Carbonado", NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)
            .decoration(TextDecoration.OBFUSCATED, false)));
    machineMeta.lore(List.of(Component.text("").append(
        Component.text("Carbonado lore", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false))));
    machineMeta.setCustomModelData(2200094);
    machineMeta.getPersistentDataContainer().set(
        new NamespacedKey("slimefun", "slimefun_item"),
        PersistentDataType.STRING, "CARBONADO");
    machine.setItemMeta(machineMeta);

    ItemStack cheat = machine.clone();
    ItemMeta cheatMeta = cheat.getItemMeta();
    cheatMeta.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, true));
    cheatMeta.lore(List.of(Component.text("Carbonado lore", NamedTextColor.GRAY)));
    cheat.setItemMeta(cheatMeta);
    ItemStack originalMachine = machine.clone();
    ItemStack originalCheat = cheat.clone();

    List<String> report = SupremeItemIdentityDoctor.compare(machine, cheat);
    assertTrue(report.contains("Bukkit stackable (ignoring quantity): false"));
    assertTrue(report.contains("Adventure display name equal: false"));
    assertTrue(report.contains("Adventure lore equal: false"));
    assertTrue(report.contains("PDC keys equal: true"));
    assertTrue(report.contains("Non-presentation metadata equal (diagnostic clones): true"));
    assertEquals(originalMachine, machine);
    assertEquals(originalCheat, cheat);
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
  void legacySyntheticSapphireWrapperAndLoreMismatchArePresentationOnly() {
    MockBukkit.mock();
    ItemStack older = new ItemStack(Material.PLAYER_HEAD, 32);
    ItemMeta oldMeta = older.getItemMeta();
    Component wrappedName = Component.text("").append(
        Component.text("Synthetic Sapphire", NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, false));
    oldMeta.displayName(wrappedName);
    oldMeta.lore(List.of(Component.text("Original lore", NamedTextColor.GRAY)
        .decoration(TextDecoration.BOLD, false)));
    NamespacedKey itemId = new NamespacedKey("slimefun", "slimefun_item");
    NamespacedKey ownerKey = new NamespacedKey("supreme", "diagnostic-owner");
    oldMeta.getPersistentDataContainer().set(itemId, PersistentDataType.STRING, "SYNTHETIC_SAPPHIRE");
    oldMeta.getPersistentDataContainer().set(ownerKey, PersistentDataType.LONG, 8123L);
    older.setItemMeta(oldMeta);
    ItemStack beforeOld = older.clone();

    ItemStack newer = older.clone();
    ItemMeta newMeta = newer.getItemMeta();
    newMeta.displayName(Component.text("Synthetic Sapphire", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET));
    newMeta.lore(List.of(Component.text("Original lore", NamedTextColor.GRAY)));
    newer.setItemMeta(newMeta);
    ItemStack beforeNew = newer.clone();

    List<String> report = SupremeItemIdentityDoctor.compare(older, newer);
    assertTrue(report.contains("Bukkit stackable (ignoring quantity): false"));
    assertTrue(report.contains("PDC keys equal: true"));
    assertTrue(report.contains("Adventure display name equal: false"));
    assertTrue(report.contains("Adventure lore equal: false"));
    assertTrue(report.contains("Non-presentation metadata equal (diagnostic clones): true"));
    assertEquals(beforeOld, older);
    assertEquals(beforeNew, newer);
  }

  @Test
  void differentModelDataCannotBeClassifiedAsPresentationOnly() {
    MockBukkit.mock();
    ItemStack older = new ItemStack(Material.DIAMOND);
    ItemMeta first = older.getItemMeta();
    first.displayName(Component.text("Carbonado", NamedTextColor.AQUA));
    first.setCustomModelData(2200094);
    older.setItemMeta(first);
    ItemStack newer = older.clone();
    ItemMeta second = newer.getItemMeta();
    second.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, true));
    second.setCustomModelData(2200095);
    newer.setItemMeta(second);
    ItemStack beforeOld = older.clone();
    ItemStack beforeNew = newer.clone();

    List<String> report = SupremeItemIdentityDoctor.compare(older, newer);
    assertTrue(report.contains("Non-presentation metadata equal (diagnostic clones): false"));
    assertEquals(beforeOld, older);
    assertEquals(beforeNew, newer);
  }

  @Test
  void missingOffhandDoesNotCrashOrMutateInventory() {
    MockBukkit.mock();
    List<String> report = SupremeItemIdentityDoctor.compare(
        new ItemStack(Material.IRON_INGOT), new ItemStack(Material.AIR));
    assertTrue(report.getFirst().contains("main hand"));
  }
  @Test
  void distinguishesChangedPersistentValuesEvenWhenIdKeysAndDisplayMatch() {
    MockBukkit.mock();
    ItemStack main = new ItemStack(Material.EMERALD);
    ItemMeta mainMeta = main.getItemMeta();
    NamespacedKey key = new NamespacedKey("slimefun", "slimefun_item");
    mainMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "SUPREME_ITEM_ONE");
    main.setItemMeta(mainMeta);

    ItemStack off = main.clone();
    ItemMeta offMeta = off.getItemMeta();
    offMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "SUPREME_ITEM_TWO");
    off.setItemMeta(offMeta);

    List<String> report = SupremeItemIdentityDoctor.compare(main, off);
    assertTrue(report.contains("PDC keys equal: true"));
    assertTrue(report.contains("Adventure display name equal: true"));
    assertTrue(report.contains("Bukkit stackable (ignoring quantity): false"));
    assertTrue(report.contains("Non-presentation metadata equal (diagnostic clones): false"));
    assertTrue(report.stream().anyMatch(line ->
        line.startsWith("Different serialized meta paths") && !line.endsWith("(none)")),
        "persistent value changes must show up as an actionable meta path");
  }

}
