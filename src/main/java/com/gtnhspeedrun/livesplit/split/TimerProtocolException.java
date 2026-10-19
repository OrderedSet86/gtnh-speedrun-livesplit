package com.gtnhspeedrun.livesplit.split;

import java.io.IOException;

/** A reply from a LiveSplit timer that the mod can't read. */
public final class TimerProtocolException extends IOException {

    public TimerProtocolException(String message) {
        super(message);
    }

    public TimerProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
