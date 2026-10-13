package com.github.relativobr.supreme.gear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Prevent pretty lore from pretending to be an actual Slimefun soulbound tag. */
class SupremeSoulboundGearTest {
    private static final NamespacedKey SOULBOUND = new NamespacedKey("slimefun", "soulbound");
    private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final Component OLD_LINE =
            LegacyComponentSerializer.legacySection().deserialize("§bSoulbound");
    private static final Component CANONICAL_LINE =
            LegacyComponentSerializer.legacySection().deserialize("§7Soulbound");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void newGearTemplatesCarryRealByteFlag() {
        ItemMeta meta = new ItemStack(Material.NETHERITE_HELMET).getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "SUPREME_HELMET_SUPREME");
        SupremeSoulboundGear.markTemplate(meta, SOULBOUND);
        assertEquals((byte) 1, meta.getPersistentDataContainer().get(SOULBOUND, PersistentDataType.BYTE));
        assertEquals("SUPREME_HELMET_SUPREME",
                meta.getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    }

    @Test
    void legacyAquaDescriptionBecomesFunctionalCanonicalMarker() {
        ItemStack original = oldArmor();
        ItemStack repaired = SupremeSoulboundGear.repair(original, SOULBOUND);

        assertEquals(List.of(Component.text("Original description"), CANONICAL_LINE),
                repaired.getItemMeta().lore());
        assertEquals(List.of(Component.text("Original description"), OLD_LINE),
                original.getItemMeta().lore(), "Original input must not be mutated");
        assertEquals((byte) 1, repaired.getItemMeta().getPersistentDataContainer()
                .get(SOULBOUND, PersistentDataType.BYTE));
    }

    @Test
    void repairingTwiceIsIdempotentAndDoesNotDuplicateTheTag() {
        ItemStack once = SupremeSoulboundGear.repair(oldArmor(), SOULBOUND);
        ItemStack twice = SupremeSoulboundGear.repair(once, SOULBOUND);
        assertEquals(once, twice);
        assertEquals(1, twice.getItemMeta().lore().stream().filter(CANONICAL_LINE::equals).count());
    }

    @Test
    void repairPreservesOriginalGearIdentityCustomizationsAndPdcTypes() {
        ItemStack original = oldArmor();
        ItemMeta meta = original.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("supreme", "charge"),
                PersistentDataType.LONG, 9_000_000_012L);
        meta.getPersistentDataContainer().set(new NamespacedKey("external", "owner"),
                PersistentDataType.STRING, "unchanged");
        ((Damageable) meta).setDamage(49);
        meta.setUnbreakable(true);
        var models = meta.getCustomModelDataComponent();
        models.setStrings(List.of("legacy_model"));
        meta.setCustomModelDataComponent(models);
        original.setItemMeta(meta);

        ItemStack repaired = SupremeSoulboundGear.repair(original, SOULBOUND);
        ItemMeta after = repaired.getItemMeta();
        assertEquals(Material.NETHERITE_CHESTPLATE, repaired.getType());
        assertEquals(original.getAmount(), repaired.getAmount());
        assertEquals("ARMOR_CHESTPLATE_SUPREME", after.getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        assertEquals(9_000_000_012L, after.getPersistentDataContainer()
                .get(new NamespacedKey("supreme", "charge"), PersistentDataType.LONG));
        assertEquals("unchanged", after.getPersistentDataContainer()
                .get(new NamespacedKey("external", "owner"), PersistentDataType.STRING));
        assertEquals(49, ((Damageable) after).getDamage());
        assertTrue(after.isUnbreakable());
        // MockBukkit does not currently round-trip this component on ItemStack#setItemMeta;
        // compare against what the original ItemStack actually retained instead of
        // claiming the test fixture was fully stored.
        assertEquals(original.getItemMeta().getCustomModelDataComponent().getStrings(),
                after.getCustomModelDataComponent().getStrings());
    }

    @Test
    void repairsOnlyEquipmentPresentInBothInventoryAndDrops() {
        PlayerMock player = server.addPlayer();
        var inventory = player.getInventory();
        ItemStack armor = oldArmor();
        ItemStack normal = new ItemStack(Material.DIAMOND_CHESTPLATE);
        inventory.setItem(0, armor.clone());
        inventory.setItem(1, normal.clone());
        List<ItemStack> drops = new ArrayList<>(List.of(armor.clone(), normal.clone()));

        int repaired = SupremeSoulboundGear.repairDeathDrops(inventory, drops,
                stack -> isOldArmorWithoutTag(stack), SOULBOUND);

        assertEquals(1, repaired);
        assertTrue(drops.get(0).getItemMeta().getPersistentDataContainer().has(SOULBOUND, PersistentDataType.BYTE));
        assertTrue(inventory.getItem(0).getItemMeta().getPersistentDataContainer()
                .has(SOULBOUND, PersistentDataType.BYTE));
        assertEquals(normal, drops.get(1));
        assertEquals(normal, inventory.getItem(1));
        assertEquals(0, SupremeSoulboundGear.repairDeathDrops(inventory, drops,
                SupremeSoulboundGearTest::isOldArmorWithoutTag, SOULBOUND),
                "Reprocessing the death event must not add a second copy or second tag");
        assertEquals(2, drops.size(), "Core listener, not Supreme, remains responsible for drop removal");
    }

    @Test
    void noMatchingDeathDropMeansNoInventoryMutationOrSecondCopy() {
        PlayerMock player = server.addPlayer();
        ItemStack armor = oldArmor();
        player.getInventory().setItem(0, armor.clone());
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.STONE)));

        assertEquals(0, SupremeSoulboundGear.repairDeathDrops(player.getInventory(), drops,
                SupremeSoulboundGearTest::isOldArmorWithoutTag, SOULBOUND));
        assertEquals(armor, player.getInventory().getItem(0));
        assertEquals(1, drops.size());
    }

    @Test
    void aSingleDeathDropCanOnlyMatchOneIdenticalInventoryStack() {
        PlayerMock player = server.addPlayer();
        ItemStack armor = oldArmor();
        player.getInventory().setItem(0, armor.clone());
        player.getInventory().setItem(1, armor.clone());
        List<ItemStack> drops = new ArrayList<>(List.of(armor.clone()));

        assertEquals(1, SupremeSoulboundGear.repairDeathDrops(player.getInventory(), drops,
                SupremeSoulboundGearTest::isOldArmorWithoutTag, SOULBOUND));
        long marked = List.of(player.getInventory().getItem(0), player.getInventory().getItem(1)).stream()
                .filter(stack -> stack.getItemMeta().getPersistentDataContainer()
                        .has(SOULBOUND, PersistentDataType.BYTE)).count();
        assertEquals(1, marked);
        assertEquals(1, drops.size());
    }

    private static boolean isOldArmorWithoutTag(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        return "ARMOR_CHESTPLATE_SUPREME".equals(pdc.get(ID, PersistentDataType.STRING))
                && !pdc.has(SOULBOUND, PersistentDataType.BYTE);
    }

    private static ItemStack oldArmor() {
        ItemStack stack = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "ARMOR_CHESTPLATE_SUPREME");
        meta.lore(List.of(Component.text("Original description"), OLD_LINE));
        stack.setItemMeta(meta);
        return stack;
    }
}
