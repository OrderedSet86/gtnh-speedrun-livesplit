package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Rules of {@link SplitGuard#plan}, checked over generated runs on a model timer. */
class SplitGuardTest {

    private static final int CASES = 2_000;
    private static final int MAX_SPLITS = 10;

    @ParameterizedTest
    @MethodSource("generatedCases")
    void timerNeverPassesTheLastReachedSplit(Case planCase) {
        final int finalIndex = applyPlan(planCase);
        final int lastReached = planCase.progress()
            .reachedSplits()
            .isEmpty() ? -1
                : planCase.progress()
                    .reachedSplits()
                    .lastKey();

        assertTrue(finalIndex <= Math.max(planCase.timerIndex(), lastReached + 1));
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void everySplitIsAReachedSplitAtItsReachedTime(Case planCase) {
        final List<TimerCommand> commands = SplitGuard.plan(planCase.state(), planCase.progress(), planCase.skip());
        int index = planCase.timerIndex();
        for (int position = 0; position < commands.size(); position++) {
            switch (commands.get(position)) {
                case TimerCommand.Split split -> {
                    final Long reachedTicks = planCase.progress()
                        .reachedSplits()
                        .get(index);
                    assertTrue(reachedTicks != null, "split at an unreached index " + index);
                    assertEquals(new TimerCommand.SetGameTime(reachedTicks), commands.get(position - 1));
                    index++;
                }
                case TimerCommand.SkipSplit skip -> index++;
                case TimerCommand.SetGameTime gameTime -> {}
                case TimerCommand.Start start -> throw new AssertionError("start for a running timer");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void secondPlanAfterTheFirstHasNoSplit(Case planCase) {
        final int finalIndex = applyPlan(planCase);
        final List<TimerCommand> replan = SplitGuard
            .plan(new TimerState(TimerState.Phase.RUNNING, finalIndex), planCase.progress(), planCase.skip());

        assertTrue(
            replan.stream()
                .allMatch(TimerCommand.SetGameTime.class::isInstance),
            "replan " + replan);
    }

    @ParameterizedTest
    @MethodSource("generatedCases")
    void withoutSkipMissedSplitsNothingIsSkipped(Case planCase) {
        final List<TimerCommand> commands = SplitGuard.plan(planCase.state(), planCase.progress(), false);

        assertTrue(
            commands.stream()
                .noneMatch(TimerCommand.SkipSplit.class::isInstance));
    }

    @Test
    void stoppedTimerStartsOnlyWhenAllowed() {
        final SortedMap<Integer, Long> reached = new TreeMap<>();
        reached.put(0, 40L);
        final TimerState stopped = new TimerState(TimerState.Phase.NOT_RUNNING, -1);
        final TimerState ended = new TimerState(TimerState.Phase.ENDED, -1);

        assertEquals(
            List.of(new TimerCommand.Start()),
            SplitGuard.plan(stopped, new RunProgress(true, reached, 99), true));
        assertEquals(List.of(), SplitGuard.plan(stopped, new RunProgress(false, reached, 99), true));
        assertEquals(List.of(), SplitGuard.plan(ended, new RunProgress(true, reached, 99), true));
    }

    /** Return the timer's split index after it applies the plan of {@code planCase}. */
    private static int applyPlan(Case planCase) {
        int index = planCase.timerIndex();
        for (TimerCommand command : SplitGuard.plan(planCase.state(), planCase.progress(), planCase.skip())) {
            if (command instanceof TimerCommand.Split || command instanceof TimerCommand.SkipSplit) {
                index++;
            }
        }
        return index;
    }

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
            final int timerIndex = random.nextInt(MAX_SPLITS);
            final TimerState.Phase phase = random.nextBoolean() ? TimerState.Phase.RUNNING : TimerState.Phase.PAUSED;
            cases.add(
                new Case(
                    new TimerState(phase, timerIndex),
                    new RunProgress(random.nextBoolean(), reached, random.nextInt(2_000_000)),
                    random.nextBoolean()));
        }
        return cases.stream();
    }

    private record Case(TimerState state, RunProgress progress, boolean skip) {

        int timerIndex() {
            return state.splitIndex();
        }
    }
}
