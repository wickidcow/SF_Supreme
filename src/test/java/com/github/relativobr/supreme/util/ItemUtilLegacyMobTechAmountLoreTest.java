package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import com.github.relativobr.supreme.Supreme;
import com.github.relativobr.supreme.machine.tech.MobTechGeneric.MobTechType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Regression oracle for the original mob-tech amount text in stored Slimefun item lore. */
class ItemUtilLegacyMobTechAmountLoreTest {

    private static String invokeLore(MobTechType type, Integer tier) throws Exception {
        Method oldDescriptor = ItemUtil.class.getDeclaredMethod(
                "buildLoreTypeAmount", MobTechType.class, Integer.class);
        oldDescriptor.setAccessible(true);
        return (String) oldDescriptor.invoke(null, type, tier);
    }

    @Test
    void fixedOutputVariantsMatchExactHistoricalLegacyColorAndMathText() throws Exception {
        int[] tiers = {Integer.MIN_VALUE, -10, -1, 0, 1, 3, 4, 5, 6, 8, 9, 10, Integer.MAX_VALUE};
        for (int tier : tiers) {
            String tierString = String.valueOf(tier + 1);
            String variable = "\u00a77Value process \u00a7e(" + tierString + " * amount stack * 0.15625)";
            for (MobTechType type : new MobTechType[] {MobTechType.MUTATION_BERSERK,
                    MobTechType.ROBOTIC_ACCELERATION, MobTechType.MUTATION_INTELLIGENCE,
                    MobTechType.ROBOTIC_EFFICIENCY}) {
                assertEquals(variable, invokeLore(type, tier), type + " tier=" + tier);
            }
            assertEquals("\u00a77Value process \u00a7e(amount stack * 0.15625)",
                    invokeLore(MobTechType.SIMPLE, tier), "simple tier=" + tier);
        }
    }

    @Test
    void cloneAndLuckPreserveLiveConfiguredMaxAmountWithNoFormattingRetuning() throws Exception {
        try (MockedStatic<Supreme> supreme = mockStatic(Supreme.class)) {
            SupremeOptions options = SupremeOptions.defaultValue();
            supreme.when(Supreme::getSupremeOptions).thenReturn(options);
            for (int max : new int[] {1, 16, 64, 1024}) {
                options.setMaxAmountTechGenerator(max);
                for (MobTechType type : new MobTechType[] {
                        MobTechType.MUTATION_LUCK, MobTechType.ROBOTIC_CLONING}) {
                    assertEquals("\u00a77Value process \u00a7e(" + max + "x per stack)",
                            invokeLore(type, Integer.MIN_VALUE), type + " max=" + max);
                }
            }
            supreme.verify(Supreme::getSupremeOptions, org.mockito.Mockito.times(8));
        }
    }

    @Test
    void legacyNullFailuresStayUnchangedAndDoNotSubstituteDefaults() throws Exception {
        InvocationTargetException badTier = assertThrows(InvocationTargetException.class,
                () -> invokeLore(MobTechType.SIMPLE, null));
        assertInstanceOf(NullPointerException.class, badTier.getCause());
        InvocationTargetException badType = assertThrows(InvocationTargetException.class,
                () -> invokeLore(null, 1));
        assertInstanceOf(NullPointerException.class, badType.getCause());
    }
}
