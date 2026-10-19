package com.gtnhspeedrun.livesplit.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gtnhspeedrun.livesplit.split.TimerSignal;

/**
 * The bridge drives a real socket to each kind of timer. Signals arrive out of order and twice, and the timer still
 * starts once and splits once per split, at the split's game time. After a rollback, the timer is unsplit back to the
 * run's progress.
 */
class LiveSplitBridgeTest {

    private static final long TIMEOUT_MS = 10_000;

    @Test
    void desktopTimerSplitsOncePerSplitAtTheReachedGameTime() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            runScenario(bridge, timer);
        }
        assertScenarioResult(timer);
    }

    @Test
    void liveSplitOneTimerSplitsOncePerSplitAtTheReachedGameTime() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        final int port = findFreePort();
        final LiveSplitBridge bridge = startBridge(new LsoSink(port));
        // the runner connects LiveSplit One before the run starts, and LiveSplit One doesn't retry a refused connection
        awaitTrue(() -> isListening(port));
        try (FakeLiveSplitOne liveSplitOne = new FakeLiveSplitOne(timer, port)) {
            runScenario(bridge, timer);
        }
        assertScenarioResult(timer);
    }

    @Test
    void gameTimeStaysAtASplitNewerThanTheLastGameTime() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            // a lagging server: the split at tick 80 arrives before the game time signal that passes tick 69
            bridge.offer(new TimerSignal.RunStarted());
            bridge.offer(new TimerSignal.CaughtUp(3));
            bridge.offer(new TimerSignal.GameTime(69));
            awaitTrue(() -> timer.splitIndex() == 0);
            bridge.offer(new TimerSignal.SplitReached(0, 80));
            awaitTrue(() -> timer.splitIndex() == 1);
            Thread.sleep(1_500);

            assertEquals("0:00:04.00", timer.gameTime());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void timerResetByTheRunnerIsNotStartedAgain(boolean startedByRunner) throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        if (startedByRunner) {
            timer.start();
        }
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            bridge.offer(new TimerSignal.RunStarted());
            bridge.offer(new TimerSignal.CaughtUp(3));
            bridge.offer(new TimerSignal.GameTime(20));
            awaitTrue(
                () -> timer.gameTime()
                    .equals("0:00:01.00"));
            timer.reset();
            bridge.offer(new TimerSignal.GameTime(40));
            bridge.offer(new TimerSignal.GameTime(60));
            Thread.sleep(1_500);

            assertEquals("NotRunning", timer.phase());
        }
    }

    @Test
    void catchUpOnAStoppedTimerStartsAndSplitsAtEachReachedGameTime() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            offerCatchUp(bridge, 3, new TimerSignal.SplitReached(0, 20), new TimerSignal.SplitReached(1, 40));
            awaitTrue(() -> timer.splitIndex() == 2);
        }
        assertEquals(List.of("start", "split@0:00:01.00", "split@0:00:02.00"), timer.history());
    }

    @Test
    void timerAheadOfTheProgressIsNotUnsplitBeforeTheCatchUpEnds() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        timer.start();
        timer.split();
        timer.split();
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            bridge.offer(new TimerSignal.RunStarted());
            bridge.offer(new TimerSignal.SplitReached(0, 20));
            Thread.sleep(1_500);
            assertEquals(2, timer.splitIndex());

            bridge.offer(new TimerSignal.CaughtUp(3));
            awaitTrue(() -> timer.splitIndex() == 1);
        }
    }

    @Test
    void rejoinAfterARollbackUnsplitsTheRewoundSplitAndSplitsItAgainAtTheNewTime()
        throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(3);
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            offerCatchUp(bridge, 3, new TimerSignal.SplitReached(0, 20), new TimerSignal.SplitReached(1, 40));
            awaitTrue(() -> timer.splitIndex() == 2);
            bridge.clearRun();
            // the server restarted on a save from before split 1
            offerCatchUp(bridge, 3, new TimerSignal.SplitReached(0, 20));
            awaitTrue(() -> timer.splitIndex() == 1);
            bridge.offer(new TimerSignal.SplitReached(1, 50));
            awaitTrue(() -> timer.splitIndex() == 2);
        }
        assertEquals(
            List.of("start", "split@0:00:01.00", "split@0:00:02.00", "unsplit", "split@0:00:02.50"),
            timer.history());
    }

    @Test
    void endedTimerIsUnsplitWhenTheProgressEndsEarlier() throws IOException, InterruptedException {
        final FakeTimer timer = new FakeTimer(2);
        timer.start();
        timer.split();
        timer.split();
        try (FakeDesktopServer server = new FakeDesktopServer(timer)) {
            final LiveSplitBridge bridge = startBridge(new DesktopSink(server.port()));
            offerCatchUp(bridge, 2, new TimerSignal.SplitReached(0, 20));
            awaitTrue(
                () -> timer.phase()
                    .equals("Running"));
            assertEquals(1, timer.splitIndex());
        }
    }

    /** Offer the signals of a welcome: the run start, {@code splits}, and the end of the catch-up. */
    private static void offerCatchUp(LiveSplitBridge bridge, int segmentCount, TimerSignal.SplitReached... splits) {
        bridge.offer(new TimerSignal.RunStarted());
        for (TimerSignal.SplitReached split : splits) {
            bridge.offer(split);
        }
        bridge.offer(new TimerSignal.CaughtUp(segmentCount));
    }

    /** Start the run, reach split 1 before split 0, reach both again, then reach split 2. */
    private static void runScenario(LiveSplitBridge bridge, FakeTimer timer) throws InterruptedException {
        bridge.offer(new TimerSignal.RunStarted());
        bridge.offer(new TimerSignal.CaughtUp(3));
        bridge.offer(new TimerSignal.GameTime(20));
        bridge.offer(new TimerSignal.SplitReached(1, 60));
        awaitTrue(() -> timer.splitIndex() == 2);
        bridge.offer(new TimerSignal.SplitReached(0, 80));
        bridge.offer(new TimerSignal.SplitReached(1, 90));
        bridge.offer(new TimerSignal.GameTime(100));
        awaitTrue(
            () -> timer.gameTime()
                .equals("0:00:05.00"));
        bridge.offer(new TimerSignal.SplitReached(2, 120));
        awaitTrue(
            () -> timer.phase()
                .equals("Ended"));
    }

    private static void assertScenarioResult(FakeTimer timer) {
        assertEquals(List.of("start", "skip", "split@0:00:03.00", "split@0:00:06.00"), timer.history());
    }

    private static LiveSplitBridge startBridge(TimerSink sink) {
        final LiveSplitBridge bridge = new LiveSplitBridge(
            List.of(sink),
            true,
            LogManager.getLogger("LiveSplitBridgeTest"));
        bridge.start();
        return bridge;
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        final long POLL_MS = 20;
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condition.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < deadline, "timed out");
            Thread.sleep(POLL_MS);
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    private static boolean isListening(int port) {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
