package com.github.relativobr.supreme.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class QuarryRollSelectionTest {
    private record Entry(String name, int chance) {}
    private static final ToIntFunction<Entry> WEIGHT = Entry::chance;

    @Test void emptyAndNullEntriesSelectNothing() {
        assertNull(QuarryRollSelection.select(List.<Entry>of(), WEIGHT, 0));
        assertNull(QuarryRollSelection.select(Arrays.<Entry>asList(null, null), WEIGHT, 0));
    }

    @Test void inclusiveSharedBoundaryStillSelectsTheFirstEntry() {
        var first = new Entry("first", 25);
        var second = new Entry("second", 75);
        var entries = List.of(first, second);
        assertSame(first, QuarryRollSelection.select(entries, WEIGHT, 0));
        assertSame(first, QuarryRollSelection.select(entries, WEIGHT, 25));
        assertSame(second, QuarryRollSelection.select(entries, WEIGHT, 26));
        assertSame(second, QuarryRollSelection.select(entries, WEIGHT, 100));
        assertNull(QuarryRollSelection.select(entries, WEIGHT, 101));
        assertNull(QuarryRollSelection.select(entries, WEIGHT, -1));
    }

    @Test void zeroWeightAtZeroRetainsHistoricalFirstMatch() {
        var zero = new Entry("zero", 0);
        assertSame(zero, QuarryRollSelection.select(List.of(zero, new Entry("next", 100)), WEIGHT, 0));
    }

    @Test void nullEntriesDoNotMoveTheCumulativeBoundary() {
        var first = new Entry("first", 20);
        var last = new Entry("last", 80);
        var entries = Arrays.asList(null, first, null, last, null);
        assertSame(first, QuarryRollSelection.select(entries, WEIGHT, 20));
        assertSame(last, QuarryRollSelection.select(entries, WEIGHT, 21));
    }

    @Test void negativeAndOverflowWeightsMatchTheOriginalArithmetic() {
        var cases = List.of(
            List.of(new Entry("negative", -5), new Entry("positive", 10)),
            List.of(new Entry("min", Integer.MIN_VALUE), new Entry("minus", -1)),
            List.of(new Entry("max", Integer.MAX_VALUE), new Entry("overflow", 1)));
        for (var entries : cases) {
            for (int roll : new int[] {Integer.MIN_VALUE, -6, -5, -1, 0, 1, 5, Integer.MAX_VALUE}) {
                assertSame(original(entries, WEIGHT, roll), QuarryRollSelection.select(entries, WEIGHT, roll));
            }
        }
    }

    @Test void repeatedReferencesAndEqualEntriesKeepObjectIdentity() {
        var first = new Entry("same", 10);
        var equalButDifferent = new Entry("same", 10);
        var entries = List.of(first, equalButDifferent, first);
        assertSame(equalButDifferent, QuarryRollSelection.select(entries, WEIGHT, 11));
        assertSame(first, QuarryRollSelection.select(entries, WEIGHT, 21));
    }

    @Test void sourceListAndEntryValuesAreReadAgainOnEachCall() {
        var values = new ArrayList<Entry>();
        var first = new Entry("old", 10);
        var second = new Entry("new", 20);
        values.add(first);
        assertNull(QuarryRollSelection.select(values, WEIGHT, 15));
        values.set(0, second);
        assertSame(second, QuarryRollSelection.select(values, WEIGHT, 15));
        var weight = new AtomicInteger(5);
        assertNull(QuarryRollSelection.select(values, ignored -> weight.get(), 15));
        weight.set(20);
        assertSame(second, QuarryRollSelection.select(values, ignored -> weight.get(), 15));
    }

    @Test void captureStillCompletesBeforeAccessorCallbacks() {
        var first = new Entry("first", 10);
        var second = new Entry("second", 90);
        var entries = new ArrayList<>(List.of(first, second));
        assertSame(second, QuarryRollSelection.select(entries, entry -> {
            entries.clear();
            return entry.chance();
        }, 50));
        assertTrue(entries.isEmpty());
    }

    @Test void accessorsKeepFirstMatchOrderAndAreNotRepeated() {
        var entries = List.of(new Entry("first", 10), new Entry("second", 20), new Entry("third", 70));
        var calls = new ArrayList<String>();
        assertSame(entries.get(1), QuarryRollSelection.select(entries, entry -> {
            calls.add(entry.name());
            return entry.chance();
        }, 11));
        assertEquals(List.of("first", "second"), calls);
    }

    @Test void accessorFailuresArePropagatedRatherThanSelectingAnotherOutput() {
        var expected = new IllegalStateException("injected chance failure");
        assertSame(expected, assertThrows(IllegalStateException.class,
            () -> QuarryRollSelection.select(List.of(new Entry("first", 10)), entry -> { throw expected; }, 0)));
    }

    @Test void nullSourceStillFailsBeforeAnyAccessorCall() {
        var calls = new AtomicInteger();
        assertThrows(NullPointerException.class, () -> QuarryRollSelection.select(null,
            ignored -> { calls.incrementAndGet(); return 10; }, 0));
        assertEquals(0, calls.get());
    }

    @Test void unmodifiableInputIsNeverModifiedOrReordered() {
        var entries = List.of(new Entry("a", 5), new Entry("b", 10), new Entry("c", 20));
        assertSame(entries.get(2), QuarryRollSelection.select(entries, WEIGHT, 20));
        assertEquals(List.of("a", "b", "c"), entries.stream().map(Entry::name).toList());
    }

    @Test void tenThousandGeneratedLayoutsMatchEveryTestedOriginalBoundary() {
        var random = new Random(0x5F_20260930L);
        for (int layout = 0; layout < 10_000; layout++) {
            var entries = new ArrayList<Entry>();
            for (int slot = random.nextInt(13); slot > 0; slot--) {
                int weight = switch (random.nextInt(20)) {
                    case 0 -> Integer.MIN_VALUE;
                    case 1 -> Integer.MAX_VALUE;
                    default -> random.nextInt(151) - 25;
                };
                entries.add(random.nextInt(5) == 0 ? null : new Entry("item-" + slot, weight));
            }
            var originalEntries = new ArrayList<>(entries);
            for (int roll : new int[] {random.nextInt(), -1, 0, 1, 50, 99, 100, 101}) {
                assertSame(original(entries, WEIGHT, roll), QuarryRollSelection.select(entries, WEIGHT, roll),
                    "Layout " + layout + " roll " + roll);
            }
            assertEquals(originalEntries, entries);
        }
    }

    // Literal old selection behavior is the oracle, including inclusive bounds.
    private static <T> T original(List<T> entries, ToIntFunction<? super T> chance, int roll) {
        AtomicInteger start = new AtomicInteger(0);
        AtomicInteger end = new AtomicInteger(0);
        for (T entry : entries.stream().filter(Objects::nonNull).collect(Collectors.toList())) {
            end.set(start.get() + chance.applyAsInt(entry));
            if (start.get() <= roll && end.get() >= roll) return entry;
            start.set(end.get());
        }
        return null;
    }
}
