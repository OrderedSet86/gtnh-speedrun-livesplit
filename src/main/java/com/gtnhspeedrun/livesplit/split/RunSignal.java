package com.gtnhspeedrun.livesplit.split;

/** A run event that the server sends to each subscribed client. */
public sealed interface RunSignal {

    record RunStarted() implements RunSignal {}

    /** The first time the run reached a milestone. */
    record MilestoneReached(String milestoneId, long igtTicks) implements RunSignal {}

    record GameTime(long igtTicks) implements RunSignal {}

    /** The last signal of the run so far, after the other signals to a new subscriber. */
    record CaughtUp() implements RunSignal {}
}
