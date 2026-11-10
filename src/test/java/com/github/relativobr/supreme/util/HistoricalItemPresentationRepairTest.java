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
import org.bukkit.inventory.ItemFlag;
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
  void historicalSyntheticEmeraldMatchesOwnerReportedOldNewPresentationShape() {
    MockBukkit.mock();
    ItemStack old = new ItemStack(Material.EMERALD, 64);
    ItemMeta oldMeta = old.getItemMeta();
    oldMeta.displayName(Component.text("").append(
        Component.text("Synthetic Emerald", NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false)));
    oldMeta.lore(List.of(
        Component.text("Line 1", NamedTextColor.WHITE),
        Component.text("Line 2", NamedTextColor.WHITE),
        Component.text("Line 3", NamedTextColor.WHITE)));
    oldMeta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "SYNTHETIC_EMERALD");
    oldMeta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "keep");
    old.setItemMeta(oldMeta);

    ItemStack registered = old.clone();
    registered.setAmount(1);
    ItemMeta currentMeta = registered.getItemMeta();
    currentMeta.displayName(Component.text("Synthetic Emerald", NamedTextColor.AQUA)
        .decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET)
        .decoration(TextDecoration.ITALIC, false));
    registered.setItemMeta(currentMeta);
    ItemStack fresh = registered.clone();
    fresh.setAmount(32);
    ItemStack beforeOld = old.clone(), beforeFresh = fresh.clone(), beforeRegistry = registered.clone();

    assertFalse(old.isSimilar(fresh));
    assertTrue(fresh.isSimilar(registered));
    var result = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertTrue(result.ready(), result.reason());
    assertEquals(64, result.replacement().getAmount());
    assertTrue(result.replacement().isSimilar(registered));
    assertEquals("keep", result.replacement().getItemMeta().getPersistentDataContainer()
        .get(OWNER, PersistentDataType.STRING));
    assertEquals(beforeOld, old, "historical source must remain completely untouched");
    assertEquals(beforeFresh, fresh, "fresh comparison stack must remain untouched");
    assertEquals(beforeRegistry, registered, "registered template must remain untouched");
  }

  @Test
  void smallUraniumHistoricalPreviewRequiresExactlyMatchingTypedData() {
    MockBukkit.mock();
    ItemStack old = new ItemStack(Material.PLAYER_HEAD, 17);
    ItemMeta oldMeta = old.getItemMeta();
    oldMeta.displayName(Component.text("").append(
        Component.text("Small Chunk of Uranium", NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, false)));
    oldMeta.lore(List.of(Component.text("Radioactive material", NamedTextColor.GRAY)));
    oldMeta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "SMALL_URANIUM");
    oldMeta.getPersistentDataContainer().set(COUNT, PersistentDataType.LONG, 100L);
    old.setItemMeta(oldMeta);

    ItemStack fresh = old.clone();
    ItemMeta freshMeta = fresh.getItemMeta();
    freshMeta.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.RED));
    fresh.setItemMeta(freshMeta);
    ItemStack registered = fresh.clone();
    registered.setAmount(1);
    ItemStack sourceBefore = old.clone();

    var accepted = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertTrue(accepted.ready(), accepted.reason());
    assertEquals(17, accepted.replacement().getAmount());
    assertTrue(accepted.replacement().isSimilar(registered));
    assertEquals(sourceBefore, old, "preview must not change historical uranium");

    ItemMeta conflicting = old.getItemMeta();
    conflicting.getPersistentDataContainer().remove(COUNT);
    conflicting.getPersistentDataContainer().set(COUNT, PersistentDataType.STRING, "100");
    old.setItemMeta(conflicting);
    ItemStack badBefore = old.clone();
    var rejected = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertFalse(rejected.ready(), "same key and printable value with changed PDC TYPE must refuse");
    assertEquals(badBefore, old, "rejected preview must preserve complete original");
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
  void customFontInHistoricalLoreMustNeverBeFlattened() {
    MockBukkit.mock();
    ItemStack old = withIdentity("CARBONADO", "Carbonado", 2200094, 8);
    ItemStack fresh = newer(old, "Carbonado");
    ItemStack registered = fresh.clone();
    registered.setAmount(1);
    ItemMeta meta = old.getItemMeta();
    meta.lore(List.of(Component.text("Ordinary lore", NamedTextColor.GRAY)
        .font(NamespacedKey.minecraft("uniform"))));
    old.setItemMeta(meta);
    ItemStack original = old.clone();

    var result = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertFalse(result.ready(), "custom-font lore must not be silently normalized");
    assertTrue(result.reason().contains("custom-font"), result.reason());
    assertEquals(original, old);
  }

  @Test
  void nonPresentationItemFlagsAreNotSilentlyRemovedByHistoricalRepair() {
    MockBukkit.mock();
    ItemStack old = withIdentity("CARBONADO", "Carbonado", 2200094, 12);
    ItemStack fresh = newer(old, "Carbonado");
    ItemStack registered = fresh.clone();
    registered.setAmount(1);

    ItemMeta meta = old.getItemMeta();
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    old.setItemMeta(meta);
    ItemStack snapshot = old.clone();

    var rejected = HistoricalItemPresentationRepair.prepare(old, fresh, registered);
    assertFalse(rejected.ready(), "a different functional item flag is not presentation-only");
    assertTrue(rejected.reason().contains("Non-presentation metadata differs"), rejected.reason());
    assertEquals(snapshot, old, "rejection must leave historical item intact");
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
