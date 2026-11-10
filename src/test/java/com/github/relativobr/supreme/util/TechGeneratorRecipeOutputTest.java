package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

class TechGeneratorRecipeOutputTest {

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void everyRegisteredSlimefunOutputUsesCurrentCraftingIdentityNotEarlySnapshot() {
    MockBukkit.mock();
    ItemStack oldSnapshot = new ItemStack(Material.DIAMOND, 1);
    ItemMeta oldMeta = oldSnapshot.getItemMeta();
    oldMeta.displayName(Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, false));
    oldMeta.getPersistentDataContainer().set(new NamespacedKey("slimefun", "slimefun_item"),
        PersistentDataType.STRING, "CARBONADO");
    oldSnapshot.setItemMeta(oldMeta);

    ItemStack registeredCraft = new ItemStack(Material.DIAMOND, 1);
    ItemMeta registeredMeta = registeredCraft.getItemMeta();
    Component canonicalName = Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
    Component canonicalLore = Component.text("Black Diamond", NamedTextColor.GRAY)
        .decoration(TextDecoration.ITALIC, true);
    registeredMeta.displayName(canonicalName);
    registeredMeta.lore(List.of(Component.empty(), canonicalLore));
    registeredMeta.getPersistentDataContainer().set(new NamespacedKey("slimefun", "slimefun_item"),
        PersistentDataType.STRING, "CARBONADO");
    registeredCraft.setItemMeta(registeredMeta);

    SlimefunItemStack declared = mock(SlimefunItemStack.class);
    when(declared.getItemId()).thenReturn("CARBONADO");
    when(declared.clone()).thenReturn(oldSnapshot);

    AtomicReference<String> resolvedId = new AtomicReference<>();
    ItemStack produced = TechGeneratorRecipeOutput.resolve(declared, id -> {
      resolvedId.set(id);
      return registeredCraft;
    });

    assertEquals("CARBONADO", resolvedId.get());
    assertTrue(registeredCraft.isSimilar(produced));
    assertFalse(oldSnapshot.isSimilar(produced));
    assertNotSame(registeredCraft, produced);
    assertEquals(canonicalName, produced.getItemMeta().displayName());
    assertEquals(List.of(Component.empty(), canonicalLore), produced.getItemMeta().lore());
    assertEquals(1, produced.getAmount());
    produced.setAmount(3);
    assertEquals(1, registeredCraft.getAmount(), "do not mutate shared registry definitions");
  }

  @Test
  void plainOrCustomNonTemplateOutputsAreNeverReplacedByRegistryLookup() {
    MockBukkit.mock();
    ItemStack plain = new ItemStack(Material.IRON_INGOT, 7);
    ItemStack result = TechGeneratorRecipeOutput.resolve(plain, id -> {
      fail("vanilla and non-template recipe outputs must never be looked up by ID");
      return null;
    });
    assertEquals(plain, result);
    assertNotSame(plain, result);
  }

  @Test
  void missingRegisteredItemFallsBackToOriginalRecipeWithoutLosingComponents() {
    MockBukkit.mock();
    ItemStack snapshot = new ItemStack(Material.EMERALD, 2);
    ItemMeta meta = snapshot.getItemMeta();
    Component name = Component.text("Unregistered custom output", NamedTextColor.GREEN);
    meta.displayName(name);
    meta.getPersistentDataContainer().set(new NamespacedKey("supreme", "custom"),
        PersistentDataType.INTEGER, 123);
    snapshot.setItemMeta(meta);

    SlimefunItemStack declared = mock(SlimefunItemStack.class);
    when(declared.getItemId()).thenReturn("UNREGISTERED_TEST_ITEM");
    when(declared.clone()).thenReturn(snapshot.clone());
    ItemStack output = TechGeneratorRecipeOutput.resolve(declared, id -> null);

    assertEquals(snapshot, output);
    assertNotSame(snapshot, output);
    assertEquals(123, output.getItemMeta().getPersistentDataContainer()
        .get(new NamespacedKey("supreme", "custom"), PersistentDataType.INTEGER));
  }
}
