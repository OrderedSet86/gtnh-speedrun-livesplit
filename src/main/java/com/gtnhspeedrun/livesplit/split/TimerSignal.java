package com.gtnhspeedrun.livesplit.split;

/** A run event for the client's LiveSplit timers, in terms of the runner's segments. */
public sealed interface TimerSignal {

    record RunStarted() implements TimerSignal {}

    /** @param splitIndex the position of the segment in the runner's route, from 0 */
    record SplitReached(int splitIndex, long igtTicks) implements TimerSignal {}

    record GameTime(long igtTicks) implements TimerSignal {}

    /**
     * The client has every signal of the run so far. Before this signal, a timer can be behind the signals, and no
     * split is undone.
     *
     * @param segmentCount the segments of the runner's route
     */
    record CaughtUp(int segmentCount) implements TimerSignal {}
}
