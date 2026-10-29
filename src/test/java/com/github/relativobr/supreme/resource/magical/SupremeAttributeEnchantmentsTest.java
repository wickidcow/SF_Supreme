package com.github.relativobr.supreme.resource.magical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Compare the real replacement operation with the exact historical implementation. */
class SupremeAttributeEnchantmentsTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @CsvSource({
            "protection, 10", "protection, 20",
            "unbreaking, 10", "unbreaking, 20",
            "mending, 2", "mending, 10",
            "blast_protection, 10", "blast_protection, 20",
            "fire_protection, 10", "fire_protection, 20",
            "projectile_protection, 10", "projectile_protection, 20",
            "thorns, 5", "thorns, 10",
            "efficiency, 10", "efficiency, 20",
            "fortune, 3", "fortune, 10",
            "sharpness, 10", "sharpness, 20",
            "fire_aspect, 5", "fire_aspect, 10",
            "looting, 3", "looting, 10"
    })
    void bothConfiguredAttributeModesRetainExactItemState(String key, int level) {
        ItemStack actual = fixture();
        ItemStack expected = actual.clone();
        originalApply(expected, key, level);
        SupremeAttributeEnchantments.apply(actual, key, level);
        assertEquals(expected, actual, "Preserve type, amount, metadata, keys and unsafe enchantment level");
        assertEquals(3, actual.getAmount());
        assertEquals(Material.PLAYER_HEAD, actual.getType());
        assertEquals("retain-exactly", actual.getItemMeta().getPersistentDataContainer()
                .get(NamespacedKey.minecraft("supreme_test_identity"), PersistentDataType.STRING));
    }

    @Test
    void unknownRegistryKeyDoesNotMutateTheItem() {
        ItemStack actual = fixture();
        ItemStack before = actual.clone();
        originalApply(before, "missing_supreme_test_enchantment", 20);
        SupremeAttributeEnchantments.apply(actual, "missing_supreme_test_enchantment", 20);
        assertEquals(before, actual);
    }

    @Test
    void repeatedApplicationReplacesTheLevelExactlyAsBefore() {
        ItemStack actual = fixture();
        ItemStack expected = actual.clone();
        for (int level : new int[]{10, 20, 10}) {
            originalApply(expected, "sharpness", level);
            SupremeAttributeEnchantments.apply(actual, "sharpness", level);
            assertEquals(expected, actual);
            assertEquals(level, actual.getEnchantmentLevel(Enchantment.SHARPNESS));
        }
    }

    @Test
    void invalidKeyRemainsAnErrorWithoutChangingTheItem() {
        ItemStack actual = fixture();
        ItemStack before = actual.clone();
        assertThrows(IllegalArgumentException.class, () -> originalApply(before, "INVALID KEY", 10));
        assertThrows(IllegalArgumentException.class,
                () -> SupremeAttributeEnchantments.apply(actual, "INVALID KEY", 10));
        assertEquals(before, actual);
    }

    private static ItemStack fixture() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 3);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing attribute", TextColor.color(0x123456)));
        meta.lore(List.of(Component.text("Owner lore &a <red> remains literal")));
        meta.getPersistentDataContainer().set(NamespacedKey.minecraft("supreme_test_identity"),
                PersistentDataType.STRING, "retain-exactly");
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        return item;
    }

    // Deliberate reference implementation for old-vs-new behavioral parity, not production use.
    @SuppressWarnings("deprecation")
    private static void originalApply(ItemStack item, String key, int level) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }
}
