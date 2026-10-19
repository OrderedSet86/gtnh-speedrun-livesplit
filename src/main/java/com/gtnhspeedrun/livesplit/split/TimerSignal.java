package com.gtnhspeedrun.livesplit.split;

/** A run event that the server sends to each subscribed client, for the client's LiveSplit timer. */
public sealed interface TimerSignal {

    record RunStarted() implements TimerSignal {}

    /** @param splitIndex the position of the split in the rules file, from 0 */
    record SplitReached(int splitIndex, long igtTicks) implements TimerSignal {}

    record GameTime(long igtTicks) implements TimerSignal {}
}
