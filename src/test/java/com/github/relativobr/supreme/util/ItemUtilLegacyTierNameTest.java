package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Preserves the original String display names and color bytes of generated Supreme tier items. */
class ItemUtilLegacyTierNameTest {

    @Test
    void everyHistoricalTierRetainsItsExactSectionColorAndRomanSuffix() {
        String[] expected = {
            "\u00a77Storm I", "\u00a72Storm II", "\u00a7aStorm III",
            "\u00a71Storm IV", "\u00a79Storm V", "\u00a76Storm VI",
            "\u00a7eStorm VII", "\u00a74Storm VIII", "\u00a75Storm IX"
        };
        for (int tier = 1; tier <= expected.length; tier++) {
            assertEquals(expected[tier - 1], ItemUtil.buildNameTier("Storm", tier), "Tier " + tier);
        }
    }

    @Test
    void outOfRangeTiersRetainTheHistoricDarkGrayFallbackWithoutSuffix() {
        for (int tier : new int[] {-1, 0, 10, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals("\u00a78Storm", ItemUtil.buildNameTier("Storm", tier), "Tier " + tier);
        }
    }

    @Test
    void namesContainingLegacyFormattingAndLiteralAmpersandsAreNotInterpretedOrDiscarded() {
        assertEquals("\u00a77\u00a7lStorm I", ItemUtil.buildNameTier("\u00a7lStorm", 1));
        assertEquals("\u00a7a&cStorm III", ItemUtil.buildNameTier("&cStorm", 3));
        assertEquals("\u00a76Tempest \u2603 VI", ItemUtil.buildNameTier("Tempest \u2603", 6));
    }

    @Test
    void existingNullStringAndNullTierBehaviorRemainUnchanged() {
        assertEquals("\u00a77null I", ItemUtil.buildNameTier(null, 1));
        assertThrows(NullPointerException.class, () -> ItemUtil.buildNameTier("Storm", null));
    }
}
