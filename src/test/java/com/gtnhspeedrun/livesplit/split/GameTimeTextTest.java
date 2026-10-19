package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** {@link TimerCommand.SetGameTime#timeText} reads back to the same tick count, in the form both timers parse. */
class GameTimeTextTest {

    // matches:
    // `0:00:00.00`
    // `512:59:59.95`
    private static final Pattern TIME_TEXT = Pattern.compile("(\\d+):([0-5]\\d):([0-5]\\d)\\.(\\d\\d)");

    @Test
    void timeTextReadsBackToTheSameTicks() {
        final int CASES = 10_000;
        final long MAX_TICKS = 20L * 3600 * 24 * 365;
        final Random random = new Random(1018L);
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            final long igtTicks = caseNumber < 100 ? caseNumber : (long) (random.nextDouble() * MAX_TICKS);
            final String text = new TimerCommand.SetGameTime(igtTicks).timeText();
            final Matcher matcher = TIME_TEXT.matcher(text);
            assertTrue(matcher.matches(), text);
            final long hundredths = ((Long.parseLong(matcher.group(1)) * 60 + Long.parseLong(matcher.group(2))) * 60
                + Long.parseLong(matcher.group(3))) * 100 + Long.parseLong(matcher.group(4));

            assertEquals(igtTicks * 5, hundredths, text);
        }
    }
}
