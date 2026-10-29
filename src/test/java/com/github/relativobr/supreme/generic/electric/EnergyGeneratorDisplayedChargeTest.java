package com.github.relativobr.supreme.generic.electric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compare the GUI-only conversion against the actual historical core accessor,
 * including balances outside signed-int range. No world storage is modified.
 */
class EnergyGeneratorDisplayedChargeTest {

    @ParameterizedTest(name = "legacy generator display preserves {0} energy")
    @MethodSource("balances")
    @SuppressWarnings("deprecation")
    void displayedLongChargeMatchesOriginalIntAccessor(long balance) {
        Location location = new Location(null, 0, 64, 0);
        EnergyNetComponent component = mock(EnergyNetComponent.class, CALLS_REAL_METHODS);
        doReturn(balance).when(component).getChargeLong(location);

        int oldDisplayedBalance = component.getCharge(location);
        assertEquals(oldDisplayedBalance, EnergyGenerator.legacyDisplayedCharge(balance));
        verify(component).getChargeLong(location);
    }

    private static Stream<Long> balances() {
        return Stream.of(
            Long.MIN_VALUE,
            ((long) Integer.MIN_VALUE) - 1L,
            (long) Integer.MIN_VALUE,
            -1L,
            0L,
            1L,
            (long) Integer.MAX_VALUE,
            ((long) Integer.MAX_VALUE) + 1L,
            Long.MAX_VALUE
        );
    }
}
