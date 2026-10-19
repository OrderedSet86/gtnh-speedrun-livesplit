package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plan the commands that bring a timer up to the run's progress. The plan for a timer that is already up to date has
 * no split, so a plan can be computed and sent again at any time. A milestone that is reached a second time, after a
 * world rollback or a reconnect, never splits twice.
 */
public final class SplitGuard {

    private SplitGuard() {}

    /**
     * Plan the commands for one timer.
     *
     * <ul>
     * <li>A stopped timer gets a start, and only when {@link RunProgress#mayStart} is set. The splits wait for the next
     * plan, with the split index after the start.</li>
     * <li>A running or paused timer gets a split for each reached split from the timer's split index on. Before each
     * split, the game time is set to the IGT at which the split was reached. With {@code skipMissedSplits}, a split
     * that the run passed without reaching is skipped. Without {@code skipMissedSplits}, the plan stops at that
     * split.</li>
     * <li>The plan ends with a game time of the current IGT.</li>
     * <li>An ended timer gets no command.</li>
     * </ul>
     */
    public static List<TimerCommand> plan(TimerState state, RunProgress progress, boolean skipMissedSplits) {
        return switch (state.phase()) {
            case NOT_RUNNING -> progress.mayStart() ? List.of(new TimerCommand.Start()) : List.of();
            case RUNNING, PAUSED -> planRunning(state.splitIndex(), progress, skipMissedSplits);
            case ENDED -> List.of();
        };
    }

    private static List<TimerCommand> planRunning(int splitIndex, RunProgress progress, boolean skipMissedSplits) {
        final List<TimerCommand> commands = new ArrayList<>();
        int index = splitIndex;
        for (Map.Entry<Integer, Long> reached : progress.reachedSplits()
            .tailMap(splitIndex)
            .entrySet()) {
            final int reachedIndex = reached.getKey();
            if (reachedIndex > index && !skipMissedSplits) {
                break;
            }
            for (; index < reachedIndex; index++) {
                commands.add(new TimerCommand.SkipSplit());
            }
            commands.add(new TimerCommand.SetGameTime(reached.getValue()));
            commands.add(new TimerCommand.Split());
            index++;
        }
        if (progress.igtTicks() > 0) {
            commands.add(new TimerCommand.SetGameTime(progress.igtTicks()));
        }
        return commands;
    }
}
