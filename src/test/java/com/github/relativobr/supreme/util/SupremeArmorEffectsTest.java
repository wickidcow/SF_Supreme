package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers all Supreme armor slots, tiers, effect keys and the actual configured bonuses. */
class SupremeArmorEffectsTest {
    private static final Map<String, Set<String>> EXPECTED = Map.of(
            "helmet", Set.of("night_vision", "conduit_power", "water_breathing"),
            "chestplate", Set.of("resistance", "strength", "saturation"),
            "leggings", Set.of("speed", "haste", "regeneration"),
            "boots", Set.of("dolphins_grace", "fire_resistance"));
    private static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");
    private static final List<String> TIERS = List.of("thornium", "magic", "rare", "epic", "legendary", "supreme");
    private YamlConfiguration config;
    private ConfigurationSection effects;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        config = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        effects = Objects.requireNonNull(config.getConfigurationSection("supreme-effect"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @CsvSource({
            "damage_resistance, resistance",
            "increase_damage, strength",
            "fast_digging, haste",
            "slow_digging, mining_fatigue",
            "slow, slowness",
            "jump, jump_boost",
            "confusion, nausea",
            "heal, instant_health",
            "harm, instant_damage",
            "minecraft:strength, strength",
            "HASTE, haste"
    })
    void legacyAndModernNamesResolveToRegisteredEffects(String configured, String canonical) {
        var type = SupremePotionEffects.resolve(configured);
        assertNotNull(type, configured + " should be a valid registry potion type");
        assertEquals(canonical, type.getKey().getKey());
    }

    @Test
    void allElevenBundledEffectsResolveWithoutMissingAliases() {
        Set<String> observed = new HashSet<>();
        for (String slot : SLOTS) {
            ConfigurationSection section = Objects.requireNonNull(effects.getConfigurationSection(slot));
            for (String key : section.getKeys(false)) {
                if (!section.getBoolean(key)) continue;
                var type = SupremePotionEffects.resolve(key);
                assertNotNull(type, "Unresolved active potion: " + slot + "." + key);
                observed.add(type.getKey().getKey());
            }
            assertEquals(EXPECTED.get(slot).size(), section.getKeys(false).size(),
                    "Unexpected configured effect key in " + slot);
        }
        assertEquals(Set.of("night_vision", "conduit_power", "water_breathing", "resistance",
                "strength", "saturation", "speed", "haste", "regeneration", "dolphins_grace",
                "fire_resistance"), observed);
    }

    @Test
    void everyThorniumTierHasCorrectEffectsLevelsAndVisibleLore() {
        for (String slot : SLOTS) {
            for (String tier : TIERS) {
                String path = "supreme_" + slot + "_" + tier;
                PotionEffect[] bonuses = EnchantsAndEffectsUtil.getPotionEffectsForItemPath(path, effects);
                assertEquals(EXPECTED.get(slot).size(), bonuses.length, path);
                Set<String> types = new HashSet<>();
                int amplifier = switch (tier) {
                    case "rare", "epic" -> 1;
                    case "legendary", "supreme" -> 2;
                    default -> 0;
                };
                for (PotionEffect bonus : bonuses) {
                    types.add(bonus.getType().getKey().getKey());
                    assertEquals(amplifier, bonus.getAmplifier(), path);
                    assertEquals(600, bonus.getDuration(), path);
                    assertFalse(bonus.isAmbient(), path);
                    assertFalse(bonus.hasParticles(), path);
                    assertFalse(bonus.hasIcon(), path);
                }
                assertEquals(EXPECTED.get(slot), types, path);
                List<String> lore = EnchantsAndEffectsUtil.getEffectLoreForItemPath(path, effects);
                assertEquals(bonuses.length, lore.size(), path);
                String level = amplifier == 0 ? " I" : amplifier == 1 ? " II" : " III";
                for (PotionEffect bonus : bonuses) {
                    String expected = SupremePotionEffects.displayName(bonus.getType()) + level;
                    assertTrue(lore.stream().anyMatch(x -> x.endsWith(expected)),
                            "Missing tooltip '" + expected + "' for " + path);
                }
                assertNotNull(config.getConfigurationSection("supreme-enchant." + path),
                        "Missing enchant configuration for " + path);
            }
        }
    }

    @Test
    void allTwelveBasicPiecesRemainEffectFreeButKeepEnchantmentConfiguration() {
        for (String material : List.of("titanium", "aurum", "adamantium")) {
            for (String slot : SLOTS) {
                String id = "supreme_" + material + "_" + slot;
                assertEquals(0, EnchantsAndEffectsUtil.getPotionEffectsForItemPath(id, effects).length, id);
                assertEquals(List.of(), EnchantsAndEffectsUtil.getEffectLoreForItemPath(id, effects), id);
                assertNotNull(config.getConfigurationSection("supreme-enchant." + id),
                        "Missing enchant configuration for " + id);
            }
        }
    }

    @Test
    void disablingAnEffectUpdatesBothItsAppliedBonusAndItsLore() {
        effects.set("chestplate.saturation", false);
        var bonuses = EnchantsAndEffectsUtil.getPotionEffectsForItemPath("supreme_chestplate_epic", effects);
        assertEquals(Set.of("resistance", "strength"), Set.of(
                bonuses[0].getType().getKey().getKey(),
                bonuses[1].getType().getKey().getKey()));
        assertEquals(2, EnchantsAndEffectsUtil.getEffectLoreForItemPath("supreme_chestplate_epic", effects).size());
    }

    @Test
    void unknownEffectIsSkippedWithoutDroppingValidBonuses() {
        effects.set("helmet.not_a_registered_potion", true);
        var bonuses = EnchantsAndEffectsUtil.getPotionEffectsForItemPath("supreme_helmet_magic", effects);
        assertEquals(EXPECTED.get("helmet").size(), bonuses.length);
        assertEquals(bonuses.length,
                EnchantsAndEffectsUtil.getEffectLoreForItemPath("supreme_helmet_magic", effects).size());
    }

    @Test
    void maximumSupportedAmplifierHasMatchingRomanNumeral() {
        effects.set("amplifier_supreme", 11);
        var bonuses = EnchantsAndEffectsUtil.getPotionEffectsForItemPath("supreme_boots_supreme", effects);
        assertEquals(10, bonuses[0].getAmplifier());
        var lore = EnchantsAndEffectsUtil.getEffectLoreForItemPath("supreme_boots_supreme", effects);
        assertTrue(lore.stream().allMatch(s -> s.endsWith(" XI")));
    }
}
