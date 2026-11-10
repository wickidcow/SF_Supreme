package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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

/** No inventory or saved-record mutation: repairs are candidate clones only. */
class HistoricalItemPresentationRepairTest {
  private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
  private static final NamespacedKey OWNER = new NamespacedKey("supreme", "test-owner");
  private static final NamespacedKey COUNT = new NamespacedKey("supreme", "test-count");

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void historicalSapphirePresentationMayBePreparedWithoutChangingOriginalStacks() {
    MockBukkit.mock();
    ItemStack older = sapphire();
    ItemStack fresh = newer(older, "Synthetic Sapphire");
    ItemStack registered = fresh.clone();
    registered.setAmount(1);
    ItemStack originalOld = older.clone();
    ItemStack originalFresh = fresh.clone();

    assertFalse(older.isSimilar(fresh));
    var result = HistoricalItemPresentationRepair.prepare(older, fresh, registered);
    assertTrue(result.ready(), result.reason());
    ItemStack candidate = result.replacement();
    assertNotNull(candidate);
    assertEquals(32, candidate.getAmount());
    assertTrue(candidate.isSimilar(registered));
    assertEquals("SYNTHETIC_SAPPHIRE",
        candidate.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    assertEquals("keep",
        candidate.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
    assertEquals(7623L,
        candidate.getItemMeta().getPersistentDataContainer().get(COUNT, PersistentDataType.LONG));
    assertEquals(2200089, candidate.getItemMeta().getCustomModelData());
    assertEquals(originalOld, older);
    assertEquals(originalFresh, fresh);
    candidate.setAmount(1);
    assertEquals(32, result.replacement().getAmount(), "candidate access must return a detached copy");
  }

  @Test
  void oldCarbonadoCanPrepareOnlyPresentationWhenFunctionalMetadataMatches() {
    MockBukkit.mock();
    ItemStack old = withIdentity("CARBONADO", "Carbonado", 2200094, 32);
    ItemStack fresh = newer(old, "Carbonado");
    ItemStack registered = fresh.clone();
    registered.setAmount(1);
    var result = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertTrue(result.ready(), result.reason());
    assertTrue(result.replacement().isSimilar(registered));
    assertFalse(old.isSimilar(registered));
  }

  @Test
  void functionalPdcValueAndCustomModelChangesRemainStrictlyRejected() {
    MockBukkit.mock();
    ItemStack old = sapphire();
    ItemStack fresh = newer(old, "Synthetic Sapphire");
    ItemStack registered = fresh.clone();
    ItemMeta changed = old.getItemMeta();
    changed.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 7624L);
    old.setItemMeta(changed);
    ItemStack prior = old.clone();

    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());
    assertEquals(prior, old);
    changed = old.getItemMeta();
    changed.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 7623L);
    changed.setCustomModelData(2200090);
    old.setItemMeta(changed);
    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());
  }

  @Test
  void changedVisibleLoreAndReferenceNotMatchingRegistryMustAbort() {
    MockBukkit.mock();
    ItemStack old = sapphire();
    ItemStack fresh = newer(old, "Synthetic Sapphire");
    ItemStack registered = fresh.clone();
    ItemMeta meta = old.getItemMeta();
    meta.lore(List.of(Component.text("Unique charge: 5", NamedTextColor.GRAY)));
    old.setItemMeta(meta);
    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());

    ItemStack another = sapphire();
    ItemMeta other = registered.getItemMeta();
    other.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 100L);
    registered.setItemMeta(other);
    assertFalse(HistoricalItemPresentationRepair.prepare(another, fresh, registered).ready());
  }

  @Test
  void interactiveOrInjectedPresentationNeverBecomesARepairCandidate() {
    MockBukkit.mock();
    ItemStack old = sapphire();
    ItemStack fresh = newer(old, "Synthetic Sapphire");
    ItemStack registered = fresh.clone();
    ItemMeta meta = old.getItemMeta();
    meta.displayName(meta.displayName().clickEvent(ClickEvent.runCommand("/private")));
    old.setItemMeta(meta);
    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());

    meta = old.getItemMeta();
    meta.displayName(Component.text("").append(
        Component.text("Synthetic Sapphire", NamedTextColor.AQUA)
            .hoverEvent(HoverEvent.showText(Component.text("Hidden state")))));
    old.setItemMeta(meta);
    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());

    meta = old.getItemMeta();
    meta.displayName(Component.text("").append(
        Component.text("Synthetic Sapphire", NamedTextColor.AQUA).insertion("hidden-payload")));
    old.setItemMeta(meta);
    assertFalse(HistoricalItemPresentationRepair.prepare(old, fresh, registered).ready());
  }

  @Test
  void unregisteredOrUnsupportedItemsAreNeverConsidered() {
    MockBukkit.mock();
    ItemStack older = withIdentity("SYNTHETIC_DIAMOND", "Synthetic Diamond", 2200090, 32);
    ItemStack fresh = newer(older, "Synthetic Diamond");
    assertFalse(HistoricalItemPresentationRepair.prepare(older, fresh, fresh).ready());

    ItemStack untagged = new ItemStack(Material.PLAYER_HEAD, 32);
    assertFalse(HistoricalItemPresentationRepair.prepare(untagged, untagged, untagged).ready());
    assertFalse(HistoricalItemPresentationRepair.prepare(null, fresh, fresh).ready());
  }

  private static ItemStack sapphire() {
    return withIdentity("SYNTHETIC_SAPPHIRE", "Synthetic Sapphire", 2200089, 32);
  }

  private static ItemStack withIdentity(String id, String text, int model, int amount) {
    ItemStack old = new ItemStack(Material.PLAYER_HEAD, amount);
    ItemMeta meta = old.getItemMeta();
    meta.displayName(Component.text("").append(
        Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
    meta.lore(List.of(Component.text("").append(
        Component.text("Ordinary lore", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))));
    meta.setCustomModelData(model);
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
    meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "keep");
    meta.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 7623L);
    old.setItemMeta(meta);
    return old;
  }

  private static ItemStack newer(ItemStack old, String text) {
    ItemStack fresh = old.clone();
    ItemMeta meta = fresh.getItemMeta();
    meta.displayName(Component.text(text, NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET));
    meta.lore(List.of(Component.text("Ordinary lore", NamedTextColor.GRAY)));
    fresh.setItemMeta(meta);
    return fresh;
  }
}
