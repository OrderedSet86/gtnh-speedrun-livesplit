package com.gtnhspeedrun.livesplit.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plan the commands that bring a timer to the run's progress. The progress is the authority: a timer behind the
 * progress is split, and a timer ahead of the progress is unsplit. The plan for a timer that is already up to date has
 * no split and no unsplit, so a plan can be computed and sent again at any time. A milestone that is reached a second
 * time never splits twice.
 */
public final class SplitGuard {

    private SplitGuard() {}

    /**
     * Plan the commands for one timer.
     *
     * <ul>
     * <li>A stopped timer gets a start, and only when {@link RunProgress#mayStart} is set. The splits wait for the next
     * plan, with the split index after the start.</li>
     * <li>The target split index is where the splits of the progress end. With {@code skipMissedSplits}, a split that
     * the run passed without reaching is skipped. Without {@code skipMissedSplits}, the splits end at that split.</li>
     * <li>A running or paused timer past the target gets an unsplit for each split past the target. A timer before the
     * target gets a split for each reached split from the timer's split index on. Before each split, the game time is
     * set to the IGT at which the split was reached, and to no less than the split before. A split reached out of order
     * then has a segment time of 0, not a negative one.</li>
     * <li>The plan ends with a game time of the current IGT.</li>
     * <li>An ended timer is at {@link RunProgress#segmentCount}. It gets no command unless the target is before that
     * index.</li>
     * </ul>
     */
    public static List<TimerCommand> plan(TimerState state, RunProgress progress, boolean skipMissedSplits) {
        return switch (state.phase()) {
            case NOT_RUNNING -> progress.mayStart() ? List.of(new TimerCommand.Start()) : List.of();
            case RUNNING, PAUSED -> planRunning(state.splitIndex(), progress, skipMissedSplits);
            case ENDED -> computeTargetIndex(progress, skipMissedSplits) < progress.segmentCount()
                ? planRunning(progress.segmentCount(), progress, skipMissedSplits)
                : List.of();
        };
    }

    /** Plan the unsplits and splits that move a timer at {@code splitIndex} to the target, then the game time. */
    private static List<TimerCommand> planRunning(int splitIndex, RunProgress progress, boolean skipMissedSplits) {
        final List<TimerCommand> commands = new ArrayList<>();
        int index = splitIndex;
        for (final int targetIndex = computeTargetIndex(progress, skipMissedSplits); index > targetIndex; index--) {
            commands.add(new TimerCommand.Unsplit());
        }

        long floorTicks = 0;
        for (long ticks : progress.reachedSplits()
            .headMap(index)
            .values()) {
            floorTicks = Math.max(floorTicks, ticks);
        }
        for (Map.Entry<Integer, Long> reached : progress.reachedSplits()
            .tailMap(index)
            .entrySet()) {
            final int reachedIndex = reached.getKey();
            if (reachedIndex > index && !skipMissedSplits) {
                break;
            }
            for (; index < reachedIndex; index++) {
                commands.add(new TimerCommand.SkipSplit());
            }
            floorTicks = Math.max(floorTicks, reached.getValue());
            commands.add(new TimerCommand.SetGameTime(floorTicks));
            commands.add(new TimerCommand.Split());
            index++;
        }
        if (progress.igtTicks() > 0) {
            commands.add(new TimerCommand.SetGameTime(progress.igtTicks()));
        }
        return commands;
    }

    /** Return the split index after the last split of the progress. */
    private static int computeTargetIndex(RunProgress progress, boolean skipMissedSplits) {
        int index = 0;
        for (int reachedIndex : progress.reachedSplits()
            .keySet()) {
            if (reachedIndex > index && !skipMissedSplits) {
                break;
            }
            index = reachedIndex + 1;
        }
        return index;
    }
}
