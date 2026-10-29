package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The lookup API changes; existing owner configuration names and levels do not. */
class SupremeEnchantmentResolverTest {
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
            "fall_damage, feather_falling",
            "protection_fall, feather_falling",
            "protection_environmental, protection",
            "protection_fire, fire_protection",
            "protection_explosions, blast_protection",
            "protection_projectile, projectile_protection",
            "damage_all, sharpness",
            "damage_undead, smite",
            "damage_arthropods, bane_of_arthropods",
            "dig_speed, efficiency",
            "durability, unbreaking",
            "loot_bonus_mobs, looting",
            "loot_bonus_blocks, fortune",
            "oxygen, respiration",
            "water_worker, aqua_affinity"
    })
    void everyHistoricalAliasRetainsTheSameRegisteredEnchantment(String configured, String canonical) {
        Enchantment actual = SupremeEnchantmentResolver.resolve(configured);
        assertNotNull(actual);
        assertEquals("minecraft:" + canonical, actual.getKey().toString());
        assertSame(SupremeEnchantmentResolver.resolve(canonical), actual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sharpness", "minecraft:sharpness", "SHARPNESS", "  sharpness  ", " MINECRAFT:DAMAGE_ALL "})
    void whitespaceCaseAndMinecraftPrefixRetainTheirExistingMeaning(String configured) {
        assertSame(Enchantment.SHARPNESS, SupremeEnchantmentResolver.resolve(configured));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "minecraft:", "other:sharpness", "not known", "not_a_registered_enchantment"})
    void invalidOrUnknownNamesRemainUnresolved(String configured) {
        assertNull(SupremeEnchantmentResolver.resolve(configured));
    }

    @Test
    void ownerConfigurationIsNotRewrittenByResolution() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fall_damage", 40);
        config.set("DURABILITY", 5);
        config.set("unbreakable", true);
        String before = config.saveToString();
        Map<Enchantment, Integer> resolved = EnchantsAndEffectsUtil.getEnchants(config);
        assertEquals(40, resolved.get(Enchantment.FEATHER_FALLING));
        assertEquals(5, resolved.get(Enchantment.UNBREAKING));
        assertEquals(2, resolved.size());
        assertEquals(before, config.saveToString());
    }

    @ParameterizedTest
    @CsvSource({"-1, 1", "1, 1", "100, 100", "101, 1"})
    void existingLevelBoundsRemainUnchanged(int configured, int expected) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("damage_all", configured);
        assertEquals(Map.of(Enchantment.SHARPNESS, expected), EnchantsAndEffectsUtil.getEnchants(config));
        assertEquals(configured, config.getInt("damage_all"));
    }

    @Test
    void disabledZeroLevelRemainsAbsent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("damage_all", 0);
        assertTrue(EnchantsAndEffectsUtil.getEnchants(config).isEmpty());
        assertEquals(0, config.getInt("damage_all"));
    }

    @Test
    void caseNormalizationDoesNotDependOnServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertSame(Enchantment.FIRE_PROTECTION, SupremeEnchantmentResolver.resolve("PROTECTION_FIRE"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
