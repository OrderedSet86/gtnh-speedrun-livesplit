package com.gtnhspeedrun.livesplit.split;

/** A splits file that the mod can't read. */
public final class SplitRulesException extends Exception {

    public SplitRulesException(String message) {
        super(message);
    }

    public SplitRulesException(String message, Throwable cause) {
        super(message, cause);
    }
}
