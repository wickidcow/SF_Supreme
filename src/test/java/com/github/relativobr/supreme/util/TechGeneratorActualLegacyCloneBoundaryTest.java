package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.relativobr.supreme.generic.recipe.AbstractItemRecipe;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService;
import io.github.thebusybiscuit.slimefun4.core.services.CustomTextureService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
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
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Tests the REAL Legacy SlimefunItemStack.clone() implementation rather than
 * stubbing its return value. The static core services are replaced with
 * deterministic in-memory mocks; real native Paper/profile persistence still
 * needs its separate saved-world acceptance test.
 */
class TechGeneratorActualLegacyCloneBoundaryTest {
  private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
  private static final NamespacedKey TYPED = new NamespacedKey("supreme", "actual-clone-test");

  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  @Test
  void actualLegacyCloneStaysTypedAndRetainsCanonicalOutputIdentity() {
    MockBukkit.mock();
    try (MockedStatic<Slimefun> core = mockStatic(Slimefun.class)) {
      core.when(Slimefun::instance).thenReturn(mock(Slimefun.class));
      CustomItemDataService dataService = mock(CustomItemDataService.class);
      core.when(Slimefun::getItemDataService).thenReturn(dataService);
      core.when(Slimefun::getItemTextureService).thenReturn(mock(CustomTextureService.class));
      doAnswer(call -> {
        ItemMeta meta = call.getArgument(0);
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, call.getArgument(1));
        return null;
      }).when(dataService).setItemData(any(ItemMeta.class), eq("CARBONADO"));

      ItemStack original = new ItemStack(Material.PLAYER_HEAD);
      ItemMeta meta = original.getItemMeta();
      Component display = Component.text("Carbonado", NamedTextColor.AQUA)
          .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
      Component lore = Component.text("Black Diamond", NamedTextColor.GRAY)
          .decoration(TextDecoration.ITALIC, false);
      meta.displayName(display);
      meta.lore(List.of(lore));
      meta.setCustomModelData(2200094);
      meta.getPersistentDataContainer().set(TYPED, PersistentDataType.LONG, 114L);
      original.setItemMeta(meta);

      SlimefunItemStack declared = new SlimefunItemStack("CARBONADO", original);
      ItemStack sourceSnapshot = declared.clone();
      assertInstanceOf(SlimefunItemStack.class, sourceSnapshot,
          "Legacy clone must retain the concrete SlimefunItemStack subtype");
      assertTrue(declared.isSimilar(sourceSnapshot));
      assertEquals(display, sourceSnapshot.getItemMeta().displayName());
      assertEquals(List.of(lore), sourceSnapshot.getItemMeta().lore());
      assertEquals(2200094, sourceSnapshot.getItemMeta().getCustomModelData());
      assertEquals("CARBONADO", sourceSnapshot.getItemMeta().getPersistentDataContainer()
          .get(ID, PersistentDataType.STRING));
      assertEquals(114L, sourceSnapshot.getItemMeta().getPersistentDataContainer()
          .get(TYPED, PersistentDataType.LONG));

      AbstractItemRecipe registeredCard = new AbstractItemRecipe(
          new ItemStack(Material.PAPER).clone(), sourceSnapshot);
      assertInstanceOf(SlimefunItemStack.class, registeredCard.getFirstItemOutput());

      ItemStack finalCraftingTemplate = declared.clone();
      finalCraftingTemplate.setAmount(1);
      AtomicInteger lookups = new AtomicInteger();
      ItemStack generated = TechGeneratorRecipeOutput.resolve(
          registeredCard.getFirstItemOutput(), id -> {
            assertEquals("CARBONADO", id);
            lookups.incrementAndGet();
            return finalCraftingTemplate;
          });
      assertEquals(1, lookups.get());
      assertTrue(finalCraftingTemplate.isSimilar(generated));
      assertNotSame(finalCraftingTemplate, generated);
      generated.setAmount(11);

      BlockMenu menu = mock(BlockMenu.class);
      when(menu.pushItem(any(ItemStack.class), any(int[].class))).thenReturn(null);
      SupremeInventoryUtils.pushAll(menu, new int[]{19, 20}, generated);
      var captured = org.mockito.ArgumentCaptor.forClass(ItemStack.class);
      verify(menu).pushItem(captured.capture(), any(int[].class));
      ItemStack inserted = captured.getValue();
      assertEquals(11, inserted.getAmount());
      assertTrue(finalCraftingTemplate.isSimilar(inserted));
      assertEquals(display, inserted.getItemMeta().displayName());
      assertEquals(List.of(lore), inserted.getItemMeta().lore());
      assertEquals(2200094, inserted.getItemMeta().getCustomModelData());
      assertEquals(114L, inserted.getItemMeta().getPersistentDataContainer()
          .get(TYPED, PersistentDataType.LONG));
      assertEquals(1, finalCraftingTemplate.getAmount());
      assertEquals(1, declared.getAmount());
    }
  }
}
