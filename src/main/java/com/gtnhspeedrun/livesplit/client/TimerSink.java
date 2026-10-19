package com.gtnhspeedrun.livesplit.client;

import java.io.IOException;

import com.gtnhspeedrun.livesplit.split.TimerCommand;
import com.gtnhspeedrun.livesplit.split.TimerState;

/** A connection to one LiveSplit timer. Call a sink from one thread only. */
public interface TimerSink {

    /** Return the sink's name for log lines, such as {@code desktop:16834}. */
    String describe();

    boolean isConnected();

    /** Make the timer reachable, or throw when it isn't. */
    void connect() throws IOException;

    TimerState readState() throws IOException;

    void send(TimerCommand command) throws IOException;

    /** Forget the connection after a failure. The next {@link #connect} starts over. */
    void reset();
}
