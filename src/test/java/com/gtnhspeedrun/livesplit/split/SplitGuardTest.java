package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Rules of {@link SplitGuard#plan}, checked over generated runs and the boundary runs on a model timer. */
class SplitGuardTest {

    private static final int CASES = 2_000;
    private static final int MAX_SPLITS = 10;

    @ParameterizedTest
    @MethodSource("generatedCases")
    void timerEndsRightAfterTheLastSplitThatNoUnreachedSplitBlocks(Case planCase) {
        final int finalIndex = applyPlan(planCase);
        final SortedMap<Integer, Long> reached = planCase.progress()
            .reachedSplits();

        if (planCase.skip()) {
            assertEquals(reached.isEmpty() ? 0 : reached.lastKey() + 1, finalIndex);
            return;
        }
        for (int index = 0; index < finalIndex; index++) {
            assertTrue(reached.containsKey(index), "passed unreached split " + index);
        }
        assertFalse(reached.containsKey(finalIndex), "stopped before reached split " + finalIndex);
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void everySplitIsAReachedSplitAtNoLessThanItsTimeOrTheSplitBefore(Case planCase) {
        final List<TimerCommand> commands = SplitGuard.plan(planCase.state(), planCase.progress(), planCase.skip());
        int index = planCase.timerIndex();
        long previousSplitTicks = 0;
        for (int position = 0; position < commands.size(); position++) {
            switch (commands.get(position)) {
                case TimerCommand.Split split -> {
                    final Long reachedTicks = planCase.progress()
                        .reachedSplits()
                        .get(index);
                    assertTrue(reachedTicks != null, "split at an unreached index " + index);
                    final long splitTicks = ((TimerCommand.SetGameTime) commands.get(position - 1)).igtTicks();
                    assertTrue(splitTicks >= reachedTicks, "split before its reach at " + index);
                    assertTrue(splitTicks >= previousSplitTicks, "negative segment at " + index);
                    previousSplitTicks = splitTicks;
                    index++;
                }
                case TimerCommand.SkipSplit skip -> index++;
                case TimerCommand.Unsplit unsplit -> index--;
                case TimerCommand.SetGameTime gameTime -> {}
                case TimerCommand.Start start -> throw new AssertionError("start for a running timer");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void splitsReachedInOrderSplitAtTheirReachedTime(Case planCase) {
        final SortedMap<Integer, Long> ordered = new TreeMap<>();
        long ticks = 0;
        for (int index : planCase.progress()
            .reachedSplits()
            .keySet()) {
            ticks += 1 + index;
            ordered.put(index, ticks);
        }
        final RunProgress progress = new RunProgress(false, ordered, ticks, MAX_SPLITS);
        final List<TimerCommand> commands = SplitGuard.plan(planCase.state(), progress, planCase.skip());

        int index = planCase.timerIndex();
        for (int position = 0; position < commands.size(); position++) {
            switch (commands.get(position)) {
                case TimerCommand.Split split -> assertEquals(
                    new TimerCommand.SetGameTime(ordered.get(index++)),
                    commands.get(position - 1));
                case TimerCommand.SkipSplit skip -> index++;
                case TimerCommand.Unsplit unsplit -> index--;
                case TimerCommand.SetGameTime gameTime -> {}
                case TimerCommand.Start start -> throw new AssertionError("start for a running timer");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void unsplitsComeFirstAndOnlyForATimerAheadOfTheProgress(Case planCase) {
        final List<TimerCommand> commands = SplitGuard.plan(planCase.state(), planCase.progress(), planCase.skip());
        final int finalIndex = applyPlan(planCase);
        int unsplits = 0;
        while (unsplits < commands.size() && commands.get(unsplits) instanceof TimerCommand.Unsplit) {
            unsplits++;
        }

        assertEquals(Math.max(0, planCase.timerIndex() - finalIndex), unsplits);
        for (TimerCommand command : commands.subList(unsplits, commands.size())) {
            assertFalse(command instanceof TimerCommand.Unsplit, "unsplit after a split " + commands);
        }
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void secondPlanAfterTheFirstHasNoSplitAndNoUnsplit(Case planCase) {
        final int finalIndex = applyPlan(planCase);
        final List<TimerCommand> replan = SplitGuard
            .plan(new TimerState(TimerState.Phase.RUNNING, finalIndex), planCase.progress(), planCase.skip());

        for (TimerCommand command : replan) {
            assertTrue(command instanceof TimerCommand.SetGameTime, "replan " + replan);
        }
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void withoutSkipMissedSplitsNothingIsSkipped(Case planCase) {
        for (TimerCommand command : SplitGuard.plan(planCase.state(), planCase.progress(), false)) {
            assertFalse(command instanceof TimerCommand.SkipSplit);
        }
    }

    @Test
    void stoppedTimerStartsOnlyWhenAllowed() {
        final SortedMap<Integer, Long> reached = new TreeMap<>();
        reached.put(0, 40L);
        final TimerState stopped = new TimerState(TimerState.Phase.NOT_RUNNING, -1);

        assertEquals(
            List.of(new TimerCommand.Start()),
            SplitGuard.plan(stopped, new RunProgress(true, reached, 99, 3), true));
        assertEquals(List.of(), SplitGuard.plan(stopped, new RunProgress(false, reached, 99, 3), true));
    }

    @Test
    void endedTimerIsUnsplitOnlyBackToAShorterProgress() {
        final int SEGMENTS = 3;
        final TimerState ended = new TimerState(TimerState.Phase.ENDED, -1);
        final SortedMap<Integer, Long> reached = new TreeMap<>();
        for (int count = 0; count <= SEGMENTS; count++) {
            final List<TimerCommand> commands = SplitGuard
                .plan(ended, new RunProgress(true, reached, 99, SEGMENTS), true);
            int unsplits = 0;
            for (TimerCommand command : commands) {
                if (command instanceof TimerCommand.Unsplit) {
                    unsplits++;
                }
            }

            assertEquals(SEGMENTS - count, unsplits, "progress of " + count);
            if (count == SEGMENTS) {
                assertEquals(List.of(), commands);
            }
            reached.put(count, 10L * (count + 1));
        }
    }

    /** Return the timer's split index once the timer has run the plan of {@code planCase}. */
    private static int applyPlan(Case planCase) {
        int index = planCase.timerIndex();
        for (TimerCommand command : SplitGuard.plan(planCase.state(), planCase.progress(), planCase.skip())) {
            if (command instanceof TimerCommand.Split || command instanceof TimerCommand.SkipSplit) {
                index++;
            } else if (command instanceof TimerCommand.Unsplit) {
                index--;
            }
        }
        return index;
    }

    /**
     * Generated runs, then the boundary runs: no reach, and every reach in reverse time order, each with the timer at 0
     * and at the end.
     */
    private static Stream<Case> generatedCases() {
        final Random random = new Random(20261018L);
        final List<Case> cases = new ArrayList<>();
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            final SortedMap<Integer, Long> reached = new TreeMap<>();
            for (int index = 0; index < MAX_SPLITS; index++) {
                if (random.nextInt(3) == 0) {
                    reached.put(index, 1L + random.nextInt(1_000_000));
                }
            }
            final TimerState.Phase phase = random.nextBoolean() ? TimerState.Phase.RUNNING : TimerState.Phase.PAUSED;
            cases.add(
                new Case(
                    new TimerState(phase, random.nextInt(MAX_SPLITS + 1)),
                    new RunProgress(random.nextBoolean(), reached, random.nextInt(2_000_000), MAX_SPLITS),
                    random.nextBoolean()));
        }
        final SortedMap<Integer, Long> everySplit = new TreeMap<>();
        for (int index = 0; index < MAX_SPLITS; index++) {
            everySplit.put(index, 100L * (MAX_SPLITS - index));
        }
        for (SortedMap<Integer, Long> reached : List.of(new TreeMap<Integer, Long>(), everySplit)) {
            for (int timerIndex : new int[] { 0, MAX_SPLITS }) {
                for (boolean skip : new boolean[] { false, true }) {
                    cases.add(
                        new Case(
                            new TimerState(TimerState.Phase.RUNNING, timerIndex),
                            new RunProgress(true, reached, 0, MAX_SPLITS),
                            skip));
                }
            }
        }
        return cases.stream();
    }

    private record Case(TimerState state, RunProgress progress, boolean skip) {

        int timerIndex() {
            return state.splitIndex();
        }
    }
}
