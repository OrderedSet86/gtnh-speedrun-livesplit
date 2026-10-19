package com.gtnhspeedrun.livesplit.split;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What the run has reached so far, for {@link SplitGuard#plan}.
 *
 * @param mayStart      whether the timer may be started if it isn't running
 * @param reachedSplits the IGT ticks at which each split index was reached
 */
public record RunProgress(boolean mayStart, SortedMap<Integer, Long> reachedSplits, long igtTicks) {

    public RunProgress {
        reachedSplits = Collections.unmodifiableSortedMap(new TreeMap<>(reachedSplits));
    }
}
