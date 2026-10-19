package com.gtnhspeedrun.livesplit.split;

/**
 * The phase and current split of a LiveSplit timer, read from the timer.
 *
 * @param splitIndex the current split, from 0. The value is -1 outside {@link Phase#RUNNING} and {@link Phase#PAUSED}
 */
public record TimerState(Phase phase, int splitIndex) {

    /** The phase names of desktop LiveSplit and LiveSplit One. */
    public enum Phase {

        NOT_RUNNING,
        RUNNING,
        PAUSED,
        ENDED;

        public static Phase parse(String rawPhase) throws TimerProtocolException {
            return switch (rawPhase) {
                case "NotRunning" -> NOT_RUNNING;
                case "Running" -> RUNNING;
                case "Paused" -> PAUSED;
                case "Ended" -> ENDED;
                default -> throw new TimerProtocolException(
                    "unknown timer phase '" + rawPhase + "' (expected NotRunning, Running, Paused or Ended)");
            };
        }
    }
}
