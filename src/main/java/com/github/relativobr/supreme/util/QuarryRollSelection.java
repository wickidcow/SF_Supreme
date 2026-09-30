package com.github.relativobr.supreme.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Selection-only helper; it does not generate rolls, clone items or change production probabilities. */
final class QuarryRollSelection {
    private QuarryRollSelection() {}

    static <T> T select(List<T> entries, ToIntFunction<? super T> chance, int roll) {
        // Keep a per-call snapshot before invoking entry accessors, as the old
        // stream collector did. Never cache a mutable addon output definition.
        List<T> snapshot = new ArrayList<>(entries);
        int start = 0;
        for (T entry : snapshot) {
            if (entry == null) {
                continue;
            }
            int end = start + chance.applyAsInt(entry);
            // Inclusive bounds, zero/negative weights and int overflow are
            // historical behavior. Modernization must not silently rebalance them.
            if (start <= roll && end >= roll) {
                return entry;
            }
            start = end;
        }
        return null;
    }
}
