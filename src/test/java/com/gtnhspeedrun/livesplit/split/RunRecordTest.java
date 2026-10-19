package com.gtnhspeedrun.livesplit.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** {@link RunRecord} returns each milestone's first reach, in reach order, and reports each first reach once. */
class RunRecordTest {

    @Test
    void firstReachOfEachMilestoneIsKeptInReachOrder() {
        final int CASES = 200;
        final Random random = new Random(11L);
        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            final RunRecord record = new RunRecord();
            final Map<String, Long> firstTicks = new HashMap<>();
            final List<String> firstOrder = new ArrayList<>();
            final int reachCount = caseNumber == 0 ? 0 : random.nextInt(50);
            for (int reach = 0; reach < reachCount; reach++) {
                final String id = "m" + random.nextInt(8);
                final long ticks = random.nextInt(1_000);
                final boolean first = !firstTicks.containsKey(id);
                if (first) {
                    firstTicks.put(id, ticks);
                    firstOrder.add(id);
                }

                assertEquals(first, record.recordReach(id, ticks));
            }

            final List<RunSignal.MilestoneReached> expected = new ArrayList<>();
            for (String id : firstOrder) {
                expected.add(new RunSignal.MilestoneReached(id, firstTicks.get(id)));
            }
            assertEquals(expected, record.listReaches());
        }
    }

    @Test
    void concurrentReachesOfOneMilestoneHaveOneFirst() throws InterruptedException {
        final int THREADS = 8;
        final int ROUNDS = 200;
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                final RunRecord record = new RunRecord();
                final AtomicInteger firsts = new AtomicInteger();
                final List<Runnable> reaches = new ArrayList<>();
                for (int thread = 0; thread < THREADS; thread++) {
                    final long ticks = thread;
                    reaches.add(() -> {
                        if (record.recordReach("nether", ticks)) {
                            firsts.incrementAndGet();
                        }
                    });
                }
                final List<Future<?>> futures = new ArrayList<>();
                for (Runnable reach : reaches) {
                    futures.add(pool.submit(reach));
                }
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException exception) {
                        throw new AssertionError(exception);
                    }
                }

                assertEquals(1, firsts.get());
                assertEquals(
                    1,
                    record.listReaches()
                        .size());
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void clearForgetsEveryReach() {
        final RunRecord record = new RunRecord();
        record.recordReach("nether", 4);
        record.clear();

        assertEquals(List.of(), record.listReaches());
        assertTrue(record.recordReach("nether", 9));
    }
}
