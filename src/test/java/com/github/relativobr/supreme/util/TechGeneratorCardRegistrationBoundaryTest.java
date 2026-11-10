package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
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

/**
 * Exercises the card recipe container -> final registry lookup -> output push boundary.
 * SlimefunItemStack cloning is mocked; this is not a native Paper saved-world test.
 */
class TechGeneratorCardRegistrationBoundaryTest {
  private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
  private static final NamespacedKey TYPED = new NamespacedKey("supreme", "boundary-test-value");

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  @Test
  void cardRecipeRoundTripRetainsSubtypeAndPushesExactRegisteredOutput() {
    MockBukkit.mock();
    SlimefunItemStack setupOutput = mock(SlimefunItemStack.class);
    SlimefunItemStack storedOutput = mock(SlimefunItemStack.class);
    when(setupOutput.clone()).thenReturn(storedOutput);
    when(storedOutput.getItemId()).thenReturn("CARBONADO");

    // TechGenerator.addRecipesToProcess() clones the two inputs, then constructs
    // AbstractItemRecipe. This test isolates that boundary without booting a plugin.
    AbstractItemRecipe cardRecipe = new AbstractItemRecipe(
        new ItemStack(Material.PAPER).clone(), setupOutput.clone());
    assertInstanceOf(SlimefunItemStack.class, cardRecipe.getFirstItemOutput(),
        "a stored Slimefun recipe output must not degrade to a plain ItemStack");

    ItemStack finalRegisteredCraft = new ItemStack(Material.PLAYER_HEAD, 1);
    ItemMeta meta = finalRegisteredCraft.getItemMeta();
    Component name = Component.text("Carbonado", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, true)
        .decoration(TextDecoration.ITALIC, false);
    Component lore = Component.text("Black Diamond", NamedTextColor.GRAY)
        .decoration(TextDecoration.ITALIC, false);
    meta.displayName(name);
    meta.lore(List.of(lore));
    meta.setCustomModelData(2200094);
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "CARBONADO");
    meta.getPersistentDataContainer().set(TYPED, PersistentDataType.LONG, 991L);
    finalRegisteredCraft.setItemMeta(meta);

    AtomicInteger registryLookups = new AtomicInteger();
    ItemStack resolved = TechGeneratorRecipeOutput.resolve(cardRecipe.getFirstItemOutput(), id -> {
      assertEquals("CARBONADO", id);
      registryLookups.incrementAndGet();
      return finalRegisteredCraft;
    });
    assertEquals(1, registryLookups.get(), "card selection must use final registration");
    assertNotSame(finalRegisteredCraft, resolved, "do not mutate shared registry template");
    assertTrue(finalRegisteredCraft.isSimilar(resolved));

    resolved.setAmount(9);
    BlockMenu inventory = mock(BlockMenu.class);
    when(inventory.pushItem(any(ItemStack.class), any(int[].class))).thenReturn(null);
    SupremeInventoryUtils.pushAll(inventory, new int[]{19, 20}, resolved);
    var output = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
    verify(inventory).pushItem(output.capture(), any(int[].class));
    assertEquals(9, output.getValue().getAmount());
    assertTrue(finalRegisteredCraft.isSimilar(output.getValue()),
        "post-push output must stack with registered crafting and cheat output");
    assertEquals(name, output.getValue().getItemMeta().displayName());
    assertEquals(List.of(lore), output.getValue().getItemMeta().lore());
    assertEquals(2200094, output.getValue().getItemMeta().getCustomModelData());
    assertEquals(991L, output.getValue().getItemMeta().getPersistentDataContainer()
        .get(TYPED, PersistentDataType.LONG));
    assertEquals(1, finalRegisteredCraft.getAmount());
  }

  @Test
  void plainCustomOutputWithSlimefunIdPdcIsNotSilentlyReplaced() {
    MockBukkit.mock();
    ItemStack customOutput = new ItemStack(Material.PLAYER_HEAD, 4);
    ItemMeta meta = customOutput.getItemMeta();
    meta.displayName(Component.text("Custom owner-defined output", NamedTextColor.RED));
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "CARBONADO");
    meta.getPersistentDataContainer().set(TYPED, PersistentDataType.LONG, 123L);
    customOutput.setItemMeta(meta);
    AbstractItemRecipe cardRecipe = new AbstractItemRecipe(new ItemStack(Material.PAPER),
        customOutput.clone());
    ItemStack result = TechGeneratorRecipeOutput.resolve(cardRecipe.getFirstItemOutput(), id -> {
      fail("A plain ItemStack must never be resolved by PDC alone");
      return null;
    });
    assertEquals(customOutput, result);
    assertNotSame(customOutput, result);
    assertEquals(4, result.getAmount());
  }
}
