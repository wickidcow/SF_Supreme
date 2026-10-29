package com.github.relativobr.supreme.generic.electric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
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
    void displayedLongChargeMatchesOriginalIntAccessor(long balance) throws ReflectiveOperationException {
        Location location = new Location(null, 0, 64, 0);
        EnergyNetComponent component = mock(EnergyNetComponent.class, CALLS_REAL_METHODS);
        // Bind the optional method through reflection so this test source also
        // compiles against the historical Gugu API. The Legacy test lane runs
        // these nine cases against its actual default implementation.
        Method longReader = EnergyNetComponent.class.getMethod("getChargeLong", Location.class);
        longReader.invoke(doReturn(balance).when(component), location);

        int oldDisplayedBalance = component.getCharge(location);
        assertEquals(oldDisplayedBalance, EnergyGenerator.readDisplayedCharge(component, location));
        longReader.invoke(verify(component, times(2)), location);
    }

    @Test
    @SuppressWarnings("deprecation")
    void olderGuguChargeMethodRemainsTheIntDisplayFallback() {
        Location location = new Location(null, 0, 64, 0);
        EnergyNetComponent component = mock(EnergyNetComponent.class);
        doReturn(-39).when(component).getCharge(location);
        assertEquals(-39, EnergyGenerator.readDisplayedCharge(component, location, null));
        verify(component).getCharge(location);
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
