package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.relativobr.supreme.machine.tech.MobTechGeneric.MobTechType;
import org.junit.jupiter.api.Test;

/** Character-exact oracle for historical saved Supreme mob-tech lore. */
class ItemUtilLegacyMobTechLoreTest {

    @Test
    void everyTechVariantRetainsHistoricalColorsAcrossThresholdsAndIntegerBoundaries() {
        int[] tiers = {Integer.MIN_VALUE, -10, -1, 0, 1, 3, 4, 5, 6, 8, 9, 10, Integer.MAX_VALUE};
        for (MobTechType type : MobTechType.values()) {
            for (int tier : tiers) {
                assertEquals(historicalLore(type, tier), ItemUtil.buildLoreType(type, tier),
                        type + " tier=" + tier);
            }
        }
    }

    @Test
    void generatedLoreRetainsRepeatedSectionCodesAndUnmodifiedWordSpacing() {
        assertEquals("\u00a7a4x \u00a77increase speed \u00a7c40%\u00a77 increase energy",
                ItemUtil.buildLoreType(MobTechType.MUTATION_BERSERK, 3));
        assertEquals("\u00a7a100% \u00a77 decrease energy",
                ItemUtil.buildLoreType(MobTechType.ROBOTIC_EFFICIENCY, 9));
        assertEquals("\u00a7a+4 stack\u00a77 clone",
                ItemUtil.buildLoreType(MobTechType.ROBOTIC_CLONING, 9));
        assertEquals("\u00a77Increases processing speed",
                ItemUtil.buildLoreType(MobTechType.SIMPLE, 0));
    }

    @Test
    void nullValuesStillFailRatherThanSilentlyCreateNewLore() {
        assertThrows(NullPointerException.class,
                () -> ItemUtil.buildLoreType(MobTechType.SIMPLE, null));
        assertThrows(NullPointerException.class,
                () -> ItemUtil.buildLoreType(null, 1));
    }

    // Literal old ChatColor concatenation semantics, not Adventure serialization.
    private static String historicalLore(MobTechType type, int tier) {
        String value = String.valueOf(tier + 1);
        return switch (type) {
            case MUTATION_BERSERK, ROBOTIC_ACCELERATION ->
                    "\u00a7a" + value + "x \u00a77increase speed \u00a7c" + value
                            + "0%\u00a77 increase energy";
            case MUTATION_LUCK, ROBOTIC_CLONING ->
                    "\u00a7a+" + (tier >= 9 ? 4 : tier >= 6 ? 3 : tier >= 4 ? 2 : 1)
                            + " stack\u00a77 clone";
            case MUTATION_INTELLIGENCE, ROBOTIC_EFFICIENCY ->
                    "\u00a7a" + value + "0% \u00a77 decrease energy";
            case SIMPLE -> "\u00a77Increases processing speed";
        };
    }
}
