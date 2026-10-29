package com.github.relativobr.supreme.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Regression oracle for the historically shared mutable quarry display formatter. */
class AbstractQuarryFormatterTest {

    private static DecimalFormat historicalFormatter() {
        return new DecimalFormat("###,###,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    @Test
    void exactHistoricalEnglishLocaleAndDoubleBoundariesArePreserved() {
        DecimalFormat expected = historicalFormatter();
        double[] values = {0d, -0d, 1d, -1d, 0.0049, 0.005, 12.345, -12.345,
                999.994, 999.995, 1_000.00, 1_000_000.99, -9_999_999.1234,
                Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double value : values) {
            assertEquals(expected.format(value), AbstractQuarry.format(value),
                    "The existing display text must not change for " + value);
        }
        assertEquals("1,234.50", AbstractQuarry.format(1234.5));
        assertEquals("-1,234.50", AbstractQuarry.format(-1234.5));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void regionConcurrentCallsKeepIndependentFormatterState() throws Exception {
        int workers = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Void>> results = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                final int workerId = worker;
                results.add(pool.submit((Callable<Void>) () -> {
                    DecimalFormat old = historicalFormatter();
                    start.await();
                    for (int index = 0; index < 1500; index++) {
                        double input = (index - 750) * (workerId + 1) * 123.45678;
                        String historical = old.format(input);
                        assertEquals(historical, AbstractQuarry.format(input),
                                "worker=" + workerId + " sample=" + index);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> result : results) {
                result.get(25, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
