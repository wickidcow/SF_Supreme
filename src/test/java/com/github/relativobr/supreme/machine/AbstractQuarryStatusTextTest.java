package com.github.relativobr.supreme.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

/** Preserves the immutable old GUI labels while replacing deprecated Bukkit color concatenation. */
class AbstractQuarryStatusTextTest {

    @Test
    void activeAndInactiveLabelsRetainEveryHistoricalSectionCode() {
        assertEquals("\u00a7aACTIVE", AbstractQuarry.statusText(true));
        assertEquals("\u00a7cNOT-ACTIVE", AbstractQuarry.statusText(false));
    }

    @Test
    void generatedLabelsRoundTripThroughLegacySectionSerializer() {
        var old = LegacyComponentSerializer.legacySection();
        assertEquals(Component.text("ACTIVE", NamedTextColor.GREEN),
                old.deserialize(AbstractQuarry.statusText(true)));
        assertEquals(Component.text("NOT-ACTIVE", NamedTextColor.RED),
                old.deserialize(AbstractQuarry.statusText(false)));
    }
}
