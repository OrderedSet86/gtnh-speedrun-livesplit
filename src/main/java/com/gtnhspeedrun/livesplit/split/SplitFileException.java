package com.gtnhspeedrun.livesplit.split;

/** A milestone file or a selection file that the mod can't read. */
public final class SplitFileException extends Exception {

    public SplitFileException(String message) {
        super(message);
    }

    public SplitFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
