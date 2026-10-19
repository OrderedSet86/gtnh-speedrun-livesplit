package com.gtnhspeedrun.livesplit.split;

/** One command to a LiveSplit timer, in no protocol yet. */
public sealed interface TimerCommand {

    record Start() implements TimerCommand {}

    record Split() implements TimerCommand {}

    record SkipSplit() implements TimerCommand {}

    record SetGameTime(long igtTicks) implements TimerCommand {

        /**
         * Format the game time the way desktop LiveSplit and LiveSplit One both parse a time. The hours have no upper
         * bound.
         *
         * <pre>
         *     0 ticks         ->   0:00:00.00
         *     1 tick          ->   0:00:00.05
         *     9_000_037 ticks -> 125:00:01.85
         * </pre>
         */
        public String timeText() {
            final long TICKS_PER_SECOND = 20;
            final long HUNDREDTHS_PER_TICK = 5;
            final long totalSeconds = igtTicks / TICKS_PER_SECOND;
            return String.format(
                "%d:%02d:%02d.%02d",
                totalSeconds / 3600,
                totalSeconds / 60 % 60,
                totalSeconds % 60,
                igtTicks % TICKS_PER_SECOND * HUNDREDTHS_PER_TICK);
        }
    }
}
